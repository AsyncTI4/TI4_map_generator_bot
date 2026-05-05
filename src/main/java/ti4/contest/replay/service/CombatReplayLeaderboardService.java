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
import ti4.contest.replay.core.CombatReplayChannels;
import ti4.contest.replay.core.CombatReplayHouse;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.contest.replay.entities.CombatReplayContestEntity;
import ti4.contest.replay.entities.CombatReplayLeaderboardEntryEntity;
import ti4.contest.replay.entities.CombatReplayPredictionEntity;
import ti4.contest.replay.house.hacan.CombatReplayHacanTradeConvoysService;
import ti4.contest.replay.repository.CombatReplayContestRepository;
import ti4.contest.replay.repository.CombatReplayLeaderboardEntryRepository;
import ti4.contest.replay.repository.CombatReplayPredictionRepository;
import ti4.contest.replay.service.CombatReplayHouseLedgerService.HouseAbilitySummary;
import ti4.contest.replay.service.CombatReplayHouseLedgerService.HouseFavorSummary;
import ti4.contest.replay.service.CombatReplayHouseLedgerService.HouseLeaderboardSummary;
import ti4.contest.replay.service.CombatReplayHouseLedgerService.HousePredictionSummary;
import ti4.contest.replay.service.CombatReplayPredictionScorer.LockedPrediction;
import ti4.contest.replay.service.CombatReplayPredictionScorer.ScoredPredictions;
import ti4.contest.replay.service.CombatReplayPredictionScorer.WinningPredictionSummary;
import ti4.contest.replay.service.CombatReplaySideBetService.ResolvedSideBet;
import ti4.contest.replay.service.CombatReplaySideBetService.SideBetResolution;
import ti4.discord.JdaService;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ThreadGetter;
import ti4.json.JsonMapperManager;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;

/**
 * Manages replay-native prediction locking, scoring, and leaderboard posting.
 */
@Service
@RequiredArgsConstructor
public class CombatReplayLeaderboardService {

    public static final String LAZAX_MINIGAME_SUBSCRIPTION_MARKER = "-# Lazax Minigame Subscription";
    public static final String LAZAX_MINIGAME_ROLE_NAME = "Lazax Minigame";

    private static final int WRONG_PREDICTION_PENALTY = -4;
    private static final String SUBSCRIBE_EMOJI = "\uD83D\uDFE2";
    private static final String UNSUBSCRIBE_EMOJI = "\uD83D\uDD34";
    private static final Comparator<LockedPrediction> LOCKED_PREDICTION_COMPARATOR = Comparator.comparing(
                    LockedPrediction::discordUserName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(LockedPrediction::discordUserId);

    private final CombatContestSettings settings;
    private final CombatReplayContestRepository replayContestRepository;
    private final CombatReplayPredictionRepository replayPredictionRepository;
    private final CombatReplayLeaderboardEntryRepository leaderboardEntryRepository;
    private final CombatReplaySideBetService sideBetService;
    private final CombatReplayHouseService houseService;
    private final CombatReplayHouseLedgerService houseLedgerService;
    private final CombatReplayHouseFavorService houseFavorService;
    private final CombatReplayHacanTradeConvoysService hacanTradeConvoysService;

    public void lockPredictionsAtReplayStart(
            Game game, CombatReplayContestEntity replayContest, CombatCandidateEntity candidate) {
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
        replayPredictionRepository.findByContestId(replayContestId).ifPresent(replayPredictionRepository::delete);
        houseLedgerService.clearContest(replayContestId);
    }

    public List<HousePredictionSummary> computeAndPersistHouseScoresFromFacts(
            CombatCandidateEntity candidate, CombatReplayContestEntity replayContest) {
        if (candidate == null || replayContest == null || replayContest.getId() == null) return List.of();

        ScoredPredictions scoredPredictions = replayPredictionRepository
                .findByContestId(replayContest.getId())
                .map(lockedPrediction -> {
                    List<LockedPrediction> attackerPredictions =
                            readLockedPredictions(lockedPrediction.getAttackerPredictionsJson());
                    List<LockedPrediction> defenderPredictions =
                            readLockedPredictions(lockedPrediction.getDefenderPredictionsJson());
                    return CombatReplayPredictionScorer.score(
                            attackerPredictions,
                            defenderPredictions,
                            candidate.getWinnerFaction(),
                            candidate.getAttackerFaction());
                })
                .orElse(null);
        return houseLedgerService.buildAndPersistPredictionSummaries(
                replayContest, scoredPredictions, sideBetService.resolvedSideBetsFromFacts(candidate, replayContest));
    }

    public void finalizeReplayLeaderboardContest(
            Game game, CombatReplayContestEntity replayContest, CombatCandidateEntity candidate) {
        if (replayContest.getId() == null) return;
        if (replayContest.getLeaderboardPostedAt() != null) return;

        hacanTradeConvoysService.lockPreviousTradeConvoysIfCurrentCombatEnded(replayContest);
        ScoredContestResult result = scoreReplayContest(candidate, replayContest);
        MessageChannel threadOrChannel = getContestThreadOrChannel(replayContest);
        if (threadOrChannel != null) {
            postPredictionResultsSummary(game, threadOrChannel, candidate, result, () -> {
                if (!settings.isHousesEnabled()) {
                    postSideBetResultsSummary(threadOrChannel, result);
                }
                postDelegationFavorAwards(candidate, result.housePredictionSummaries());
                hacanTradeConvoysService.postPostCombatTradeConvoysButtonsIfNeeded(
                        replayContest, candidate, result.housePredictionSummaries());
                postParticipantFollowup(game, candidate, threadOrChannel);
                postSubscriptionPrompt(threadOrChannel);
            });
        }
        markLeaderboardPostedIfPublished(replayContest);
    }

    public boolean postLeaderboard() {
        if (settings.isHousesEnabled()) {
            return postDelegationLeaderboard();
        }

        List<CombatReplayLeaderboardEntryEntity> topEntries =
                leaderboardEntryRepository
                        .findTop10ByOrderByTotalPointsDescCorrectPredictionsDescPredictionCountDescDiscordUserNameAsc();
        if (topEntries.isEmpty()) return false;

        TextChannel contestChannel = getContestPublicChannelByName();
        if (contestChannel == null) return false;

        MessageHelper.sendMessageToChannel(contestChannel, buildLeaderboardMessage(topEntries));
        return true;
    }

    public String buildTop100LeaderboardMessage() {
        List<CombatReplayLeaderboardEntryEntity> topEntries =
                leaderboardEntryRepository
                        .findTop100ByOrderByTotalPointsDescCorrectPredictionsDescPredictionCountDescDiscordUserNameAsc();
        if (topEntries.isEmpty()) return "No Lazax War Archives leaderboard entries have been recorded yet.";
        return buildLeaderboardMessage(topEntries);
    }

    public String buildUserPointsMessage(String discordUserId) {
        CombatReplayLeaderboardEntryEntity userEntry =
                leaderboardEntryRepository.findByDiscordUserId(discordUserId).orElse(null);
        if (userEntry == null) {
            return "You do not have any Lazax War Archives points yet.";
        }

        List<CombatReplayLeaderboardEntryEntity> entries =
                leaderboardEntryRepository
                        .findAllByOrderByTotalPointsDescCorrectPredictionsDescPredictionCountDescDiscordUserNameAsc();
        int rank = 0;
        for (int index = 0; index < entries.size(); index++) {
            if (discordUserId.equals(entries.get(index).getDiscordUserId())) {
                rank = index + 1;
                break;
            }
        }

        int predictions = safeInt(userEntry.getPredictionCount());
        int correctPredictions = safeInt(userEntry.getCorrectPredictions());
        int accuracy = predictions == 0 ? 0 : Math.round((100.0f * correctPredictions) / predictions);
        return "## Your Lazax War Archives Points\n"
                + "Rank: **" + (rank == 0 ? "Unranked" : "#" + rank) + "**\n"
                + "Points: **" + safeInt(userEntry.getTotalPoints()) + "**\n"
                + "Correct predictions: `" + correctPredictions + "/" + predictions + "` (" + accuracy + "%)";
    }

    private String buildLeaderboardMessage(List<CombatReplayLeaderboardEntryEntity> topEntries) {
        StringBuilder message = new StringBuilder("## Lazax War Archives Leaderboard\n");
        for (int index = 0; index < topEntries.size(); index++) {
            CombatReplayLeaderboardEntryEntity entry = topEntries.get(index);
            int predictions = safeInt(entry.getPredictionCount());
            int correctPredictions = safeInt(entry.getCorrectPredictions());
            int accuracy = predictions == 0 ? 0 : Math.round((100.0f * correctPredictions) / predictions);
            message.append('`')
                    .append(index + 1)
                    .append(".` ")
                    .append(getSafeLeaderboardName(entry.getDiscordUserName()))
                    .append(" - **")
                    .append(safeInt(entry.getTotalPoints()))
                    .append("** points (`")
                    .append(correctPredictions)
                    .append("/")
                    .append(predictions)
                    .append("` correct, ")
                    .append(accuracy)
                    .append("%)");
            if (index < topEntries.size() - 1) {
                message.append("\n");
            }
        }
        return message.toString();
    }

    public String pointsCountMessage(User user) {
        if (user == null) return "Could not identify your Lazax points.";
        CombatReplayLeaderboardEntryEntity entry =
                leaderboardEntryRepository.findByDiscordUserId(user.getId()).orElse(null);
        int points = entry == null ? 0 : safeInt(entry.getTotalPoints());
        String label = points == 1 ? "point" : "points";
        return "You have **" + points + "** Lazax " + label + ".";
    }

    public boolean postDelegationLeaderboard() {
        List<HouseLeaderboardSummary> summaries = houseLedgerService.leaderboardSummaries();
        if (summaries.isEmpty()) return false;

        TextChannel contestChannel = getContestPublicChannelByName();
        if (contestChannel == null) return false;

        StringBuilder message = new StringBuilder("## Lazax War Archives Delegation Leaderboard\n");
        for (int index = 0; index < summaries.size(); index++) {
            HouseLeaderboardSummary summary = summaries.get(index);
            int accuracy = summary.predictionCount() == 0
                    ? 0
                    : Math.round((100.0f * summary.correctPredictions()) / summary.predictionCount());
            message.append('`')
                    .append(index + 1)
                    .append(".` ")
                    .append(FactionEmojis.getFactionIcon(summary.house().displayName()))
                    .append(" ")
                    .append(houseService.houseRoleMention(summary.house()))
                    .append(" - **")
                    .append(summary.totalPoints())
                    .append("** points (`")
                    .append(summary.correctPredictions())
                    .append("/")
                    .append(summary.predictionCount())
                    .append("` correct, ")
                    .append(accuracy)
                    .append("%, ")
                    .append(summary.memberCount())
                    .append(summary.memberCount() == 1 ? " member" : " members")
                    .append(")");
            if (index < summaries.size() - 1) {
                message.append("\n");
            }
        }
        MessageHelper.sendMessageToChannel(contestChannel, message.toString());
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
                houseService.assignHouseIfAbsent(user, message.getGuild());
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
        List<HousePredictionSummary> housePredictionSummaries = buildAndPersistHousePredictionSummaries(
                candidate, replayContest, null, sideBetResolution.resolvedSideBets());
        return new ScoredContestResult(0, List.of(), housePredictionSummaries, sideBetResolution.resolvedSideBets());
    }

    private synchronized ScoredContestResult scoreReplayContest(
            CombatCandidateEntity candidate, CombatReplayContestEntity replayContest) {
        CombatReplayPredictionEntity lockedPrediction = replayPredictionRepository
                .findByContestId(replayContest.getId())
                .orElse(null);
        return lockedPrediction == null
                ? scoreSideBetOnlyContest(candidate, replayContest)
                : scoreContest(candidate, replayContest, lockedPrediction);
    }

    private ScoredContestResult scoreContest(
            CombatCandidateEntity candidate,
            CombatReplayContestEntity replayContest,
            CombatReplayPredictionEntity lockedPrediction) {
        List<LockedPrediction> attackerPredictions =
                readLockedPredictions(lockedPrediction.getAttackerPredictionsJson());
        List<LockedPrediction> defenderPredictions =
                readLockedPredictions(lockedPrediction.getDefenderPredictionsJson());
        ScoredPredictions scoredPredictions = CombatReplayPredictionScorer.score(
                attackerPredictions, defenderPredictions, candidate.getWinnerFaction(), candidate.getAttackerFaction());

        List<String> userIds = new ArrayList<>();
        for (LockedPrediction prediction : scoredPredictions.allPredictions()) {
            userIds.add(prediction.discordUserId());
        }

        Map<String, CombatReplayLeaderboardEntryEntity> entriesByUser = new HashMap<>();
        for (CombatReplayLeaderboardEntryEntity entry : leaderboardEntryRepository.findByDiscordUserIdIn(userIds)) {
            entriesByUser.put(entry.getDiscordUserId(), entry);
        }

        List<ResolvedSideBet> resolvedSideBets = List.of();
        List<HousePredictionSummary> housePredictionSummaries;
        if (lockedPrediction.getScoredAt() == null) {
            applyContestResult(
                    scoredPredictions.allPredictions(),
                    scoredPredictions.winningPredictions(),
                    scoredPredictions.pointsAwarded(),
                    entriesByUser);
            leaderboardEntryRepository.saveAll(entriesByUser.values());
            SideBetResolution sideBetResolution = sideBetService.resolveSideBets(candidate, replayContest);
            for (CombatReplayLeaderboardEntryEntity entry : sideBetResolution.leaderboardEntries()) {
                entriesByUser.put(entry.getDiscordUserId(), entry);
            }
            resolvedSideBets = sideBetResolution.resolvedSideBets();
            housePredictionSummaries = buildAndPersistHousePredictionSummaries(
                    candidate, replayContest, scoredPredictions, resolvedSideBets);
            lockedPrediction.setScoredAt(LocalDateTime.now());
            replayPredictionRepository.save(lockedPrediction);
        } else {
            housePredictionSummaries = readHousePredictionSummaries(replayContest.getId());
            if (housePredictionSummaries.isEmpty()) {
                SideBetResolution sideBetResolution = sideBetService.resolveSideBets(candidate, replayContest);
                resolvedSideBets = sideBetResolution.resolvedSideBets();
                housePredictionSummaries = buildAndPersistHousePredictionSummaries(
                        candidate, replayContest, scoredPredictions, resolvedSideBets);
            }
        }

        return new ScoredContestResult(
                scoredPredictions.totalPredictions(),
                CombatReplayPredictionScorer.resultSummaries(
                        scoredPredictions.winningPredictions(), scoredPredictions.pointsAwarded(), entriesByUser),
                housePredictionSummaries,
                resolvedSideBets);
    }

    private void applyContestResult(
            List<LockedPrediction> allPredictions,
            List<LockedPrediction> winningPredictions,
            int pointsAwarded,
            Map<String, CombatReplayLeaderboardEntryEntity> entriesByUser) {
        Set<String> winningUserIds = new HashSet<>();
        for (LockedPrediction prediction : winningPredictions) {
            winningUserIds.add(prediction.discordUserId());
        }
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

    private List<HousePredictionSummary> buildAndPersistHousePredictionSummaries(
            CombatCandidateEntity candidate,
            CombatReplayContestEntity replayContest,
            ScoredPredictions scoredPredictions,
            List<ResolvedSideBet> resolvedSideBets) {
        return houseLedgerService.buildAndPersistPredictionSummaries(
                replayContest, scoredPredictions, resolvedSideBets);
    }

    private List<HousePredictionSummary> readHousePredictionSummaries(Long contestId) {
        return houseLedgerService.readPredictionSummaries(contestId);
    }

    private CombatReplayLeaderboardEntryEntity newLeaderboardEntry(String discordUserId, String discordUserName) {
        CombatReplayLeaderboardEntryEntity entry = new CombatReplayLeaderboardEntryEntity();
        entry.setDiscordUserId(discordUserId);
        entry.setDiscordUserName(
                discordUserName == null || discordUserName.isBlank() ? "Unknown User" : discordUserName);
        entry.setTotalPoints(settings.getHouseAbilities().getInitialIndividualPoints());
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
        if (settings.isHousesEnabled()) {
            String message = formatHousePredictionResults(result.housePredictionSummaries());
            MessageHelper.splitAndSentWithAction(message, threadOrChannel, ignored -> {
                if (afterPost != null) {
                    afterPost.run();
                }
            });
            return;
        }

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
            StringBuilder winners = new StringBuilder(message).append("\n\n");
            for (WinningPredictionSummary prediction : winningPredictions) {
                winners.append("<@")
                        .append(prediction.discordUserId())
                        .append("> - ")
                        .append(prediction.totalPoints())
                        .append(" points (pred +")
                        .append(prediction.pointsAwarded())
                        .append(")\n");
            }
            message = winners.toString().trim();
        }
        message = appendHousePredictionResults(message, result.housePredictionSummaries());
        MessageHelper.splitAndSentWithAction(message, threadOrChannel, ignored -> {
            if (afterPost != null) {
                afterPost.run();
            }
        });
    }

    private String appendHousePredictionResults(String message, List<HousePredictionSummary> houseSummaries) {
        if (!settings.isHousesEnabled() || houseSummaries == null || houseSummaries.isEmpty()) return message;
        return message + "\n\n" + formatHousePredictionResults(houseSummaries);
    }

    private String formatHousePredictionResults(List<HousePredictionSummary> houseSummaries) {
        if (houseSummaries == null || houseSummaries.isEmpty()) return "## Delegation Results\nNo delegation results.";

        StringBuilder builder = new StringBuilder("## Delegation Results\n");
        for (HousePredictionSummary summary : houseSummaries) {
            builder.append(houseService.houseRoleMention(summary.house()))
                    .append(" - **")
                    .append(formatSignedPoints(summary.totalPoints()))
                    .append("** (`")
                    .append(summary.correctPredictions())
                    .append("/")
                    .append(summary.predictionCount())
                    .append("` correct)\n")
                    .append("> Predictions: ")
                    .append(formatSignedPoints(summary.predictionPoints()))
                    .append(" points\n")
                    .append("> Side Bets: ")
                    .append(formatSignedPoints(summary.sideBetPoints()))
                    .append(" points\n");
            for (HouseAbilitySummary abilitySummary : summary.abilitySummaries()) {
                builder.append("> ")
                        .append(abilitySummary.label())
                        .append(": ")
                        .append(formatSignedPoints(abilitySummary.points()))
                        .append(" points\n");
            }
        }
        return builder.toString().trim();
    }

    private void postDelegationFavorAwards(CombatCandidateEntity candidate, List<HousePredictionSummary> summaries) {
        if (!settings.isHousesEnabled() || summaries == null || summaries.isEmpty()) return;
        for (HousePredictionSummary summary : summaries) {
            TextChannel channel = houseChannel(summary.house());
            if (channel == null) continue;
            MessageHelper.sendMessageToChannel(
                    channel, favorAwardMessage(summary, candidate == null ? null : candidate.getId()));
        }
    }

    private String favorAwardMessage(HousePredictionSummary summary) {
        return favorAwardMessage(summary, null);
    }

    private String favorAwardMessage(HousePredictionSummary summary, Long candidateId) {
        StringBuilder message = new StringBuilder("## Favor Granted\n")
                .append(FactionEmojis.getFactionIcon(summary.house().displayName()))
                .append(" ")
                .append(summary.house().displayName())
                .append(" Delegation receives `")
                .append(formatSignedPoints(summary.favorPoints()))
                .append("` Favor.\n");
        List<HouseFavorSummary> favorSummaries = summary.favorSummaries();
        if (favorSummaries == null || favorSummaries.isEmpty()) {
            message.append("- `")
                    .append(formatSignedPoints(summary.favorPoints()))
                    .append("` from the Custodians sealing this combat's ledger.\n");
            appendTotalFavorLine(message, summary.house(), candidateId);
            return message.toString().trim();
        }
        for (HouseFavorSummary favorSummary : favorSummaries) {
            message.append("- `")
                    .append(formatSignedPoints(favorSummary.favor()))
                    .append("` from ")
                    .append(favorSummary.label())
                    .append(".\n");
        }
        appendTotalFavorLine(message, summary.house(), candidateId);
        return message.toString().trim();
    }

    private void appendTotalFavorLine(StringBuilder message, CombatReplayHouse house, Long candidateId) {
        CombatReplayHouseFavorService.FavorLedger favorLedger = houseFavorService.ledger(house);
        int contestSpend = houseFavorService.spentForContest(house, candidateId);
        if (contestSpend > 0) {
            message.append("- **Favor Spent:** `")
                    .append(formatSignedPoints(-contestSpend))
                    .append("`\n");
        }
        message.append("- **Available Favor:** `").append(favorLedger.balance()).append("`\n");
    }

    private TextChannel houseChannel(CombatReplayHouse house) {
        if (JdaService.guildPrimary == null || house == null) return null;
        List<TextChannel> channels = JdaService.guildPrimary.getTextChannelsByName(house.channelName(), true);
        if (channels.isEmpty()) {
            BotLogger.warning("Lazax house channel not found: " + house.channelName());
            return null;
        }
        return channels.getFirst();
    }

    private void postSideBetResultsSummary(MessageChannel threadOrChannel, ScoredContestResult result) {
        List<AggregatedSideBetSummary> aggregatedSideBets = aggregateSideBetSummaries(result.winningSideBets());
        if (aggregatedSideBets.isEmpty()) return;

        StringBuilder message = new StringBuilder("## Side Bets\n");
        for (AggregatedSideBetSummary sideBet : aggregatedSideBets) {
            String repeats = sideBet.hitCount() > 1 ? " x" + sideBet.hitCount() : "";
            message.append("<@")
                    .append(sideBet.discordUserId())
                    .append("> - *")
                    .append(sideBet.label())
                    .append("*")
                    .append(repeats)
                    .append(" (side +")
                    .append(sideBet.totalProfit())
                    .append(")\n");
        }
        MessageHelper.sendMessageToChannel(threadOrChannel, message.toString().trim());
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
                + LAZAX_MINIGAME_SUBSCRIPTION_MARKER;
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
        List<TextChannel> channels =
                JdaService.guildPrimary.getTextChannelsByName(CombatReplayChannels.contestChannelName(settings), true);
        return channels.isEmpty() ? null : channels.getFirst();
    }

    private MessageChannel getContestThreadOrChannel(CombatReplayContestEntity contest) {
        if (JdaService.guildPrimary == null) return null;
        TextChannel contestChannel = getContestPublicChannel(contest.getPublicChannelId());
        if (contestChannel == null) return null;
        if (contest.getPublicThreadId() != null) {
            ThreadChannel thread = ThreadGetter.getThreadInChannelById(contestChannel, contest.getPublicThreadId());
            if (thread != null) return thread;
        }
        return contestChannel;
    }

    private String getFactionEmoji(Game game, String faction) {
        Player player = game.getPlayerFromColorOrFaction(faction);
        return player == null ? "" : player.getFactionEmoji();
    }

    private String buildLockedPredictionMessage(
            Game game, CombatCandidateEntity candidate, CombatReplayPredictionEntity lockedPrediction) {
        return "## The Ledger Is Sealed\n"
                + "Predictions are locked before the combat begins.\n"
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

    private List<AggregatedSideBetSummary> aggregateSideBetSummaries(List<ResolvedSideBet> resolvedSideBets) {
        record SideBetKey(String discordUserId, String discordUserName, String label) {}

        Map<SideBetKey, AggregatedSideBetSummary> summariesByKey = new LinkedHashMap<>();
        for (ResolvedSideBet sideBet : resolvedSideBets) {
            SideBetKey key = new SideBetKey(sideBet.discordUserId(), sideBet.discordUserName(), sideBet.label());
            AggregatedSideBetSummary current = summariesByKey.get(key);
            if (current == null) {
                summariesByKey.put(
                        key,
                        new AggregatedSideBetSummary(
                                key.discordUserId(), key.discordUserName(), key.label(), 1, sideBet.profitPoints()));
                continue;
            }
            summariesByKey.put(
                    key,
                    new AggregatedSideBetSummary(
                            current.discordUserId(),
                            current.discordUserName(),
                            current.label(),
                            current.hitCount() + 1,
                            current.totalProfit() + sideBet.profitPoints()));
        }
        return new ArrayList<>(summariesByKey.values());
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String formatSignedPoints(int points) {
        return points > 0 ? "+" + points : String.valueOf(points);
    }

    private record AggregatedSideBetSummary(
            String discordUserId, String discordUserName, String label, int hitCount, int totalProfit) {}

    record ScoredContestResult(
            int totalPredictions,
            List<WinningPredictionSummary> winningPredictions,
            List<HousePredictionSummary> housePredictionSummaries,
            List<ResolvedSideBet> winningSideBets) {}
}
