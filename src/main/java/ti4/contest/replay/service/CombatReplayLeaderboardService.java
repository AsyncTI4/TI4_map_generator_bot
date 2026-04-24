package ti4.contest.replay.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import org.springframework.stereotype.Service;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.contest.replay.entities.CombatReplayContestEntity;
import ti4.contest.replay.entities.CombatReplayLeaderboardEntryEntity;
import ti4.contest.replay.entities.CombatReplayPredictionEntity;
import ti4.contest.replay.repository.CombatReplayContestRepository;
import ti4.contest.replay.repository.CombatReplayLeaderboardEntryRepository;
import ti4.contest.replay.repository.CombatReplayPredictionRepository;
import ti4.contest.replay.service.CombatReplaySideBetService.ResolvedSideBet;
import ti4.contest.replay.service.CombatReplaySideBetService.SideBetResolution;
import ti4.discord.JdaService;
import ti4.game.Game;
import ti4.game.Player;
import ti4.json.JsonMapperManager;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.spring.service.contest.CombatContestService;

@Service
@RequiredArgsConstructor
/**
 * Manages replay-native prediction locking, scoring, and leaderboard posting.
 */
public class CombatReplayLeaderboardService {

    private static final double ZERO_EPSILON = 0.0001;
    private static final int WRONG_PREDICTION_PENALTY = -4;
    private static final String SUBSCRIBE_EMOJI = "\uD83D\uDFE2";
    private static final String UNSUBSCRIBE_EMOJI = "\uD83D\uDD34";
    private static final String CONTEST_CHANNEL_NAME_V1 = "lazax-war-archives-dev";
    private static final String CONTEST_CHANNEL_NAME_V2 = "lazax-war-archives";
    private static final Comparator<LockedPrediction> LOCKED_PREDICTION_COMPARATOR = Comparator.comparing(
                    LockedPrediction::discordUserName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(LockedPrediction::discordUserId);

    private final CombatContestSettings settings;
    private final CombatReplayContestRepository replayContestRepository;
    private final CombatReplayPredictionRepository replayPredictionRepository;
    private final CombatReplayLeaderboardEntryRepository leaderboardEntryRepository;
    private final CombatReplaySideBetService sideBetService;

    public void lockPredictionsAtReplayStart(
            Game game, CombatReplayContestEntity replayContest, CombatCandidateEntity candidate) {
        if (!settings.getRuntime().isDiscordPostingEnabled()) return;
        if (replayContest.getId() == null) return;
        if (replayPredictionRepository.findByContestId(replayContest.getId()).isPresent()) return;

        TextChannel contestChannel = getContestPublicChannel(replayContest.getPublicChannelId());
        if (contestChannel == null || replayContest.getPublicMessageId() == null) {
            throw new IllegalStateException("Replay contest message unavailable for prediction lock.");
        }

        Message message = contestChannel
                .retrieveMessageById(replayContest.getPublicMessageId())
                .complete();
        replayPredictionRepository.save(buildLockedPredictionSnapshot(game, replayContest, candidate, message));
    }

    public void announceLockedPredictionsIfNeeded(
            MessageChannel replayChannel,
            Game game,
            CombatReplayContestEntity replayContest,
            CombatCandidateEntity candidate) {
        if (!settings.getRuntime().isDiscordPostingEnabled()) return;
        if (replayContest.getId() == null) return;

        CombatReplayPredictionEntity lockedPrediction = replayPredictionRepository
                .findByContestId(replayContest.getId())
                .orElse(null);
        if (lockedPrediction == null || lockedPrediction.getAnnouncedAt() != null) return;

        replayChannel
                .sendMessage(buildLockedPredictionMessage(game, candidate, lockedPrediction))
                .complete();
        lockedPrediction.setAnnouncedAt(LocalDateTime.now());
        replayPredictionRepository.save(lockedPrediction);
    }

    public void clearLockedPredictions(Long replayContestId) {
        if (replayContestId == null) return;
        replayPredictionRepository.deleteByContestId(replayContestId);
    }

    public void finalizeReplayLeaderboardContest(
            Game game, CombatReplayContestEntity replayContest, CombatCandidateEntity candidate) {
        if (!settings.getRuntime().isDiscordPostingEnabled()) return;
        if (replayContest.getId() == null) return;
        if (replayContest.getLeaderboardPostedAt() != null) return;

        CombatReplayPredictionEntity lockedPrediction = replayPredictionRepository
                .findByContestId(replayContest.getId())
                .orElse(null);
        ScoredContestResult result = lockedPrediction == null
                ? scoreSideBetOnlyContest(candidate, replayContest)
                : scoreContest(candidate, replayContest, lockedPrediction);
        MessageChannel threadOrChannel = getContestThreadOrChannel(replayContest);
        if (threadOrChannel != null) {
            postPredictionResultsSummary(game, threadOrChannel, candidate, result, () -> {
                postSideBetResultsSummary(threadOrChannel, result);
                postParticipantFollowup(game, candidate, threadOrChannel);
                postSubscriptionPrompt(threadOrChannel);
            });
        }
        markLeaderboardPostedIfPublished(replayContest);
    }

    public boolean postLeaderboard() {
        if (!settings.getRuntime().isDiscordPostingEnabled()) return false;
        List<CombatReplayLeaderboardEntryEntity> topEntries =
                leaderboardEntryRepository
                        .findTop10ByOrderByTotalPointsDescCorrectPredictionsDescPredictionCountDescDiscordUserNameAsc();
        if (topEntries.isEmpty()) return false;

        TextChannel contestChannel = getContestPublicChannelByName();
        if (contestChannel == null) return false;

        final int[] rank = {1};
        String message = topEntries.stream()
                .map(entry -> {
                    int predictions = safeInt(entry.getPredictionCount());
                    int correctPredictions = safeInt(entry.getCorrectPredictions());
                    int accuracy = predictions == 0 ? 0 : Math.round((100f * correctPredictions) / predictions);
                    return '`' + Integer.toString(rank[0]++) + ".` "
                            + getSafeLeaderboardName(entry.getDiscordUserName()) + " - **"
                            + safeInt(entry.getTotalPoints()) + "** points (`" + correctPredictions + "/" + predictions
                            + "` correct, " + accuracy + "%)";
                })
                .collect(Collectors.joining("\n", "## Lazax War Archives Leaderboard\n", ""));
        MessageHelper.sendMessageToChannel(contestChannel, message);
        return true;
    }

    private CombatReplayPredictionEntity buildLockedPredictionSnapshot(
            Game game, CombatReplayContestEntity replayContest, CombatCandidateEntity candidate, Message message) {
        Map<String, Set<String>> factionsByUser = new HashMap<>();
        Map<String, String> namesByUser = new HashMap<>();
        Map<String, String> factionToEmoji = Map.of(
                candidate.getAttackerFaction(), getFactionEmoji(game, candidate.getAttackerFaction()),
                candidate.getDefenderFaction(), getFactionEmoji(game, candidate.getDefenderFaction()));

        for (MessageReaction reaction : message.getReactions()) {
            String predictedFaction = null;
            for (Map.Entry<String, String> entry : factionToEmoji.entrySet()) {
                if (reaction.getEmoji().getFormatted().equals(entry.getValue())) {
                    predictedFaction = entry.getKey();
                    break;
                }
            }
            if (predictedFaction == null) continue;

            for (User user : reaction.retrieveUsers().complete()) {
                if (user.isBot()) continue;
                factionsByUser
                        .computeIfAbsent(user.getId(), key -> new HashSet<>())
                        .add(predictedFaction);
                namesByUser.put(user.getId(), user.getName());
            }
        }

        List<LockedPrediction> attackerPredictions = new ArrayList<>();
        List<LockedPrediction> defenderPredictions = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : factionsByUser.entrySet()) {
            if (entry.getValue().size() != 1) continue;
            LockedPrediction prediction =
                    new LockedPrediction(entry.getKey(), namesByUser.getOrDefault(entry.getKey(), "Unknown User"));
            String predictedFaction = entry.getValue().iterator().next();
            if (predictedFaction.equalsIgnoreCase(candidate.getAttackerFaction())) {
                attackerPredictions.add(prediction);
                continue;
            }
            if (predictedFaction.equalsIgnoreCase(candidate.getDefenderFaction())) {
                defenderPredictions.add(prediction);
            }
        }

        attackerPredictions.sort(LOCKED_PREDICTION_COMPARATOR);
        defenderPredictions.sort(LOCKED_PREDICTION_COMPARATOR);

        CombatReplayPredictionEntity lockedPrediction = new CombatReplayPredictionEntity();
        lockedPrediction.setContestId(replayContest.getId());
        lockedPrediction.setLockedAt(LocalDateTime.now());
        lockedPrediction.setAnnouncedAt(null);
        lockedPrediction.setScoredAt(null);
        lockedPrediction.setAttackerPredictionCount(attackerPredictions.size());
        lockedPrediction.setDefenderPredictionCount(defenderPredictions.size());
        lockedPrediction.setAttackerPredictionsJson(writeLockedPredictions(attackerPredictions));
        lockedPrediction.setDefenderPredictionsJson(writeLockedPredictions(defenderPredictions));
        return lockedPrediction;
    }

    private ScoredContestResult scoreSideBetOnlyContest(
            CombatCandidateEntity candidate, CombatReplayContestEntity replayContest) {
        SideBetResolution sideBetResolution = sideBetService.resolveSideBets(candidate, replayContest);
        return new ScoredContestResult(0, List.of(), sideBetResolution.resolvedSideBets());
    }

    private ScoredContestResult scoreContest(
            CombatCandidateEntity candidate,
            CombatReplayContestEntity replayContest,
            CombatReplayPredictionEntity lockedPrediction) {
        List<LockedPrediction> attackerPredictions =
                readLockedPredictions(lockedPrediction.getAttackerPredictionsJson());
        List<LockedPrediction> defenderPredictions =
                readLockedPredictions(lockedPrediction.getDefenderPredictionsJson());
        boolean attackerWon = candidate.getWinnerFaction() != null
                && candidate.getWinnerFaction().equalsIgnoreCase(candidate.getAttackerFaction());

        List<LockedPrediction> winningPredictions = attackerWon ? attackerPredictions : defenderPredictions;
        List<LockedPrediction> allPredictions =
                new ArrayList<>(attackerPredictions.size() + defenderPredictions.size());
        allPredictions.addAll(attackerPredictions);
        allPredictions.addAll(defenderPredictions);
        int winnerPredictions = winningPredictions.size();
        int totalPredictions = allPredictions.size();
        int pointsAwarded = winnerPredictions == 0 ? 0 : calculatePredictionPoints(winnerPredictions, totalPredictions);

        List<String> userIds =
                allPredictions.stream().map(LockedPrediction::discordUserId).toList();
        Map<String, CombatReplayLeaderboardEntryEntity> entriesByUser =
                leaderboardEntryRepository.findByDiscordUserIdIn(userIds).stream()
                        .collect(
                                Collectors.toMap(CombatReplayLeaderboardEntryEntity::getDiscordUserId, entry -> entry));

        List<ResolvedSideBet> resolvedSideBets = List.of();
        if (lockedPrediction.getScoredAt() == null) {
            applyContestResult(allPredictions, winningPredictions, pointsAwarded, entriesByUser);
            leaderboardEntryRepository.saveAll(entriesByUser.values());
            SideBetResolution sideBetResolution = sideBetService.resolveSideBets(candidate, replayContest);
            sideBetResolution.leaderboardEntries().forEach(entry -> entriesByUser.put(entry.getDiscordUserId(), entry));
            resolvedSideBets = sideBetResolution.resolvedSideBets();
            lockedPrediction.setScoredAt(LocalDateTime.now());
            replayPredictionRepository.save(lockedPrediction);
        }

        return new ScoredContestResult(
                totalPredictions, resultSummaries(winningPredictions, pointsAwarded, entriesByUser), resolvedSideBets);
    }

    private void applyContestResult(
            List<LockedPrediction> allPredictions,
            List<LockedPrediction> winningPredictions,
            int pointsAwarded,
            Map<String, CombatReplayLeaderboardEntryEntity> entriesByUser) {
        Set<String> winningUserIds =
                winningPredictions.stream().map(LockedPrediction::discordUserId).collect(Collectors.toSet());
        LocalDateTime now = LocalDateTime.now();

        for (LockedPrediction prediction : allPredictions) {
            CombatReplayLeaderboardEntryEntity entry = entriesByUser.computeIfAbsent(
                    prediction.discordUserId(),
                    ignored -> newLeaderboardEntry(prediction.discordUserId(), prediction.discordUserName()));
            entry.setDiscordUserName(prediction.discordUserName());
            entry.setPredictionCount(safeInt(entry.getPredictionCount()) + 1);
            if (winningUserIds.contains(prediction.discordUserId())) {
                entry.setCorrectPredictions(safeInt(entry.getCorrectPredictions()) + 1);
                entry.setTotalPoints(safeInt(entry.getTotalPoints()) + pointsAwarded);
            } else {
                entry.setTotalPoints(Math.max(0, safeInt(entry.getTotalPoints()) + WRONG_PREDICTION_PENALTY));
            }
            entry.setUpdatedAt(now);
        }
    }

    private CombatReplayLeaderboardEntryEntity newLeaderboardEntry(String discordUserId, String discordUserName) {
        CombatReplayLeaderboardEntryEntity entry = new CombatReplayLeaderboardEntryEntity();
        entry.setDiscordUserId(discordUserId);
        entry.setDiscordUserName(
                discordUserName == null || discordUserName.isBlank() ? "Unknown User" : discordUserName);
        entry.setTotalPoints(0);
        entry.setPredictionCount(0);
        entry.setCorrectPredictions(0);
        entry.setUpdatedAt(LocalDateTime.now());
        return entry;
    }

    private void postPredictionResultsSummary(
            Game game,
            MessageChannel threadOrChannel,
            CombatCandidateEntity candidate,
            ScoredContestResult result,
            Runnable afterPost) {
        String winnerDisplay = getWinnerDisplay(game, candidate);
        List<WinningPredictionSummary> winningPredictions = result.winningPredictions();
        String message = "## Prediction Results\n"
                + "Winner: " + winnerDisplay + "\n"
                + "Predictions locked: **" + result.totalPredictions() + "**\n"
                + "Correct predictions: **" + winningPredictions.size() + "**";
        if (result.totalPredictions() == 0) {
            message += "\nNo predictions were locked for this contest.";
        } else if (winningPredictions.isEmpty()) {
            message += "\nNo one called it.";
        } else {
            message += "\n\n"
                    + winningPredictions.stream()
                            .map(prediction -> "<@" + prediction.discordUserId() + "> - " + prediction.totalPoints()
                                    + " points (pred +" + prediction.pointsAwarded() + ")")
                            .collect(Collectors.joining("\n"));
        }
        MessageHelper.splitAndSentWithAction(message, threadOrChannel, ignored -> {
            if (afterPost != null) {
                afterPost.run();
            }
        });
    }

    private void postSideBetResultsSummary(MessageChannel threadOrChannel, ScoredContestResult result) {
        List<AggregatedSideBetSummary> aggregatedSideBets = aggregateSideBetSummaries(result.winningSideBets());
        if (aggregatedSideBets.isEmpty()) return;

        String message = "## Side Bets\n"
                + aggregatedSideBets.stream()
                        .map(sideBet -> {
                            String repeats = sideBet.hitCount() > 1 ? " x" + sideBet.hitCount() : "";
                            return "<@" + sideBet.discordUserId() + "> - *" + sideBet.label() + "*" + repeats
                                    + " (side +" + sideBet.totalProfit() + ")";
                        })
                        .collect(Collectors.joining("\n"));
        MessageHelper.sendMessageToChannel(threadOrChannel, message);
    }

    private String getWinnerDisplay(Game game, CombatCandidateEntity candidate) {
        if (game != null && candidate.getWinnerFaction() != null) {
            Player winner = game.getPlayerFromColorOrFaction(candidate.getWinnerFaction());
            if (winner != null) {
                return winner.getFactionEmoji() + " **" + getSafeLeaderboardName(winner.getUserName()) + "**";
            }
        }
        return "**" + (candidate.getWinnerFaction() == null ? "Unknown" : candidate.getWinnerFaction()) + "**";
    }

    private void postParticipantFollowup(Game game, CombatCandidateEntity candidate, MessageChannel threadOrChannel) {
        if (game == null) return;
        Player attacker = game.getPlayerFromColorOrFaction(candidate.getAttackerFaction());
        Player defender = game.getPlayerFromColorOrFaction(candidate.getDefenderFaction());
        if (attacker == null || defender == null) return;

        String message = "## Summons From The Archives\n"
                + "<@" + attacker.getUserID() + "> <@" + defender.getUserID() + ">\n"
                + "By decree of the Lazax War Game, your fleets have been judged worthy of remembrance. "
                + "If it pleases the honored claimants, tarry a moment and read the commentaries, acclaim, and quiet condemnation recorded above concerning your struggle.";
        MessageHelper.sendMessageToChannel(threadOrChannel, message);
    }

    private void postSubscriptionPrompt(MessageChannel threadOrChannel) {
        String message = "Did you like this? React " + SUBSCRIBE_EMOJI
                + " to subscribe to more, " + UNSUBSCRIBE_EMOJI
                + " to opt out if already subscribed.\n"
                + CombatContestService.LAZAX_MINIGAME_SUBSCRIPTION_MARKER;
        MessageHelper.splitAndSentWithAction(message, threadOrChannel, postedMessage -> {
            postedMessage.addReaction(Emoji.fromUnicode(SUBSCRIBE_EMOJI)).queue(null, BotLogger::catchRestError);
            postedMessage.addReaction(Emoji.fromUnicode(UNSUBSCRIBE_EMOJI)).queue(null, BotLogger::catchRestError);
        });
    }

    private void markLeaderboardPostedIfPublished(CombatReplayContestEntity replayContest) {
        if (!postLeaderboard()) return;
        replayContest.setLeaderboardPostedAt(LocalDateTime.now());
    }

    private TextChannel getContestPublicChannel(Long publicChannelId) {
        if (publicChannelId == null || JdaService.guildPrimary == null) return null;
        return JdaService.guildPrimary.getTextChannelById(publicChannelId);
    }

    private TextChannel getContestPublicChannelByName() {
        if (JdaService.guildPrimary == null) return null;
        return JdaService.guildPrimary.getTextChannelsByName(getContestChannelName(), true).stream()
                .findFirst()
                .orElse(null);
    }

    private String getContestChannelName() {
        return isReplayV2Enabled() ? CONTEST_CHANNEL_NAME_V2 : CONTEST_CHANNEL_NAME_V1;
    }

    private boolean isReplayV2Enabled() {
        String versionEnabled = settings.getRuntime().getVersionEnabled();
        return "v2".equalsIgnoreCase(versionEnabled);
    }

    private MessageChannel getContestThreadOrChannel(CombatReplayContestEntity contest) {
        if (JdaService.guildPrimary == null) return null;
        if (contest.getPublicThreadId() != null) {
            ThreadChannel thread = JdaService.guildPrimary.getThreadChannelById(contest.getPublicThreadId());
            if (thread != null) return thread;
        }
        return getContestPublicChannel(contest.getPublicChannelId());
    }

    private String getFactionEmoji(Game game, String faction) {
        Player player = game.getPlayerFromColorOrFaction(faction);
        return player == null ? "" : player.getFactionEmoji();
    }

    private String buildLockedPredictionMessage(
            Game game, CombatCandidateEntity candidate, CombatReplayPredictionEntity lockedPrediction) {
        return "## Predictions Locked\n"
                + "Votes are now frozen before the combat begins.\n"
                + getFactionEmoji(game, candidate.getAttackerFaction()) + " " + candidate.getAttackerFaction() + ": **"
                + safeInt(lockedPrediction.getAttackerPredictionCount()) + "**\n"
                + getFactionEmoji(game, candidate.getDefenderFaction()) + " " + candidate.getDefenderFaction() + ": **"
                + safeInt(lockedPrediction.getDefenderPredictionCount()) + "**";
    }

    private String getSafeLeaderboardName(String userName) {
        if (userName == null || userName.isBlank()) return "Unknown User";
        return userName.replace("@", "@\u200B");
    }

    private String writeLockedPredictions(List<LockedPrediction> predictions) {
        try {
            return JsonMapperManager.basic().writeValueAsString(predictions);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to write replay prediction snapshot.", e);
        }
    }

    private List<LockedPrediction> readLockedPredictions(String predictionsJson) {
        if (predictionsJson == null || predictionsJson.isBlank()) return List.of();
        try {
            return JsonMapperManager.basic()
                    .readerForListOf(LockedPrediction.class)
                    .readValue(predictionsJson);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read replay prediction snapshot.", e);
        }
    }

    private int calculatePredictionPoints(int winnerPredictions, int totalPredictions) {
        totalPredictions = Math.max(1, totalPredictions);
        double winnerShare = winnerPredictions / (double) totalPredictions;
        double scaledPoints = 4.0 / Math.max(winnerShare, ZERO_EPSILON);
        return (int) Math.round(Math.max(4.0, Math.min(100.0, scaledPoints)));
    }

    private List<WinningPredictionSummary> resultSummaries(
            List<LockedPrediction> winningPredictions,
            int predictionPointsAwarded,
            Map<String, CombatReplayLeaderboardEntryEntity> entriesByUser) {
        return winningPredictions.stream()
                .map(prediction -> {
                    CombatReplayLeaderboardEntryEntity entry = entriesByUser.get(prediction.discordUserId());
                    return new WinningPredictionSummary(
                            prediction.discordUserId(),
                            prediction.discordUserName(),
                            predictionPointsAwarded,
                            entry == null ? 0 : safeInt(entry.getTotalPoints()));
                })
                .sorted((left, right) -> {
                    int totalPointsComparison = Integer.compare(right.totalPoints(), left.totalPoints());
                    if (totalPointsComparison != 0) return totalPointsComparison;
                    return left.discordUserName().compareToIgnoreCase(right.discordUserName());
                })
                .toList();
    }

    private List<AggregatedSideBetSummary> aggregateSideBetSummaries(List<ResolvedSideBet> resolvedSideBets) {
        record SideBetKey(String discordUserId, String discordUserName, String label) {}

        Map<SideBetKey, List<ResolvedSideBet>> grouped = resolvedSideBets.stream()
                .collect(Collectors.groupingBy(
                        sideBet -> new SideBetKey(sideBet.discordUserId(), sideBet.discordUserName(), sideBet.label()),
                        LinkedHashMap::new,
                        Collectors.toList()));

        return grouped.entrySet().stream()
                .map(entry -> new AggregatedSideBetSummary(
                        entry.getKey().discordUserId(),
                        entry.getKey().discordUserName(),
                        entry.getKey().label(),
                        entry.getValue().size(),
                        entry.getValue().stream()
                                .mapToInt(ResolvedSideBet::profitPoints)
                                .sum()))
                .toList();
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private record LockedPrediction(String discordUserId, String discordUserName) {}

    private record AggregatedSideBetSummary(
            String discordUserId, String discordUserName, String label, int hitCount, int totalProfit) {}

    record WinningPredictionSummary(String discordUserId, String discordUserName, int pointsAwarded, int totalPoints) {}

    record ScoredContestResult(
            int totalPredictions,
            List<WinningPredictionSummary> winningPredictions,
            List<ResolvedSideBet> winningSideBets) {}
}
