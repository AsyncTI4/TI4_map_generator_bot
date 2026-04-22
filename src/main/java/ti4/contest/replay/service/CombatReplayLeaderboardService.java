package ti4.contest.replay.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import ti4.contest.replay.core.*;
import ti4.contest.replay.entities.*;
import ti4.contest.replay.repository.*;
import ti4.discord.JdaService;
import ti4.game.Game;
import ti4.game.Player;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.spring.service.contest.CombatContestEntity;
import ti4.spring.service.contest.CombatContestLeaderboardRow;
import ti4.spring.service.contest.CombatContestPredictionEntity;
import ti4.spring.service.contest.CombatContestPredictionRepository;
import ti4.spring.service.contest.CombatContestRepository;
import ti4.spring.service.contest.CombatContestStatus;
import ti4.spring.service.contest.CombatContestType;
import ti4.spring.service.contest.CombatContestUserPointsRow;

@Service
@RequiredArgsConstructor
public class CombatReplayLeaderboardService {

    private static final double ZERO_EPSILON = 0.0001;

    private final CombatContestRepository contestRepository;
    private final CombatContestPredictionRepository predictionRepository;

    public Long initializeReplayLeaderboardContest(
            Game game,
            CombatObservationEntity observation,
            CombatCandidateEntity candidate,
            Long publicChannelId,
            Long publicMessageId,
            Long publicThreadId,
            String initialSummaryText) {
        if (CombatReplayRuntimeSettings.SHADOW_MODE) {
            return null;
        }
        Player attacker = game.getPlayerFromColorOrFaction(candidate.getAttackerFaction());
        Player defender = game.getPlayerFromColorOrFaction(candidate.getDefenderFaction());
        if (attacker == null || defender == null) return null;

        CombatContestEntity contest = new CombatContestEntity();
        contest.setStatus(CombatContestStatus.POSTED);
        contest.setCombatType(CombatContestType.SPACE);
        contest.setGameName(candidate.getGameName());
        contest.setTilePosition(candidate.getTilePosition());
        contest.setTileRepresentation(resolveTileRepresentation(game, candidate));
        contest.setAttackerFaction(candidate.getAttackerFaction());
        contest.setDefenderFaction(candidate.getDefenderFaction());
        contest.setAttackerColor(attacker.getColor());
        contest.setDefenderColor(defender.getColor());
        contest.setPublicChannelId(publicChannelId);
        contest.setPublicMessageId(publicMessageId);
        contest.setPublicThreadId(publicThreadId);
        contest.setPostedAt(LocalDateTime.now());
        contest.setInitialSummaryText(initialSummaryText);
        contest.setInitialStrengthAttacker(observation.getAttackerStrength());
        contest.setInitialStrengthDefender(observation.getDefenderStrength());
        contest.setInitialHpAttacker(observation.getAttackerHp());
        contest.setInitialHpDefender(observation.getDefenderHp());
        contest.setDiceRolled(true);
        contestRepository.save(contest);

        addPredictionReactions(attacker, defender, publicMessageId, publicChannelId);
        return contest.getId();
    }

    public void finalizeReplayLeaderboardContest(
            Game game, CombatReplayContestEntity replayContest, CombatCandidateEntity candidate) {
        if (CombatReplayRuntimeSettings.SHADOW_MODE) {
            return;
        }
        Long legacyContestId = replayContest.getLegacyPredictorContestId();
        if (legacyContestId == null) return;
        CombatContestEntity contest =
                contestRepository.findById(legacyContestId).orElse(null);
        if (contest == null || contest.getStatus() == CombatContestStatus.RESOLVED) return;

        contest.setStatus(CombatContestStatus.RESOLVED);
        contest.setResolvedAt(LocalDateTime.now());
        contest.setWinnerFaction(candidate.getWinnerFaction());
        contest.setLoserFaction(candidate.getLoserFaction());
        contestRepository.save(contest);

        capturePredictionsAtResolution(game, contest);
        List<CombatContestPredictionEntity> predictions = awardPredictionPoints(contest);
        MessageChannel threadOrChannel = getContestThreadOrChannel(contest);
        if (threadOrChannel != null) {
            postPredictionPointsSummary(threadOrChannel, predictions);
        }
        maybePostLeaderboardAfterResolvedContest();
    }

    private void addPredictionReactions(Player attacker, Player defender, Long publicMessageId, Long publicChannelId) {
        TextChannel channel = getContestPublicChannel(publicChannelId);
        if (channel == null || publicMessageId == null) return;
        try {
            Message message = channel.retrieveMessageById(publicMessageId).complete();
            addReaction(attacker, message);
            addReaction(defender, message);
        } catch (Exception e) {
            BotLogger.error("Failed to add replay contest prediction reactions.", e);
        }
    }

    private void capturePredictionsAtResolution(Game game, CombatContestEntity contest) {
        if (!predictionRepository.findByContestId(contest.getId()).isEmpty()) return;
        TextChannel contestChannel = getContestPublicChannel(contest.getPublicChannelId());
        if (contestChannel == null || contest.getPublicMessageId() == null) return;

        try {
            Message message = contestChannel
                    .retrieveMessageById(contest.getPublicMessageId())
                    .complete();
            capturePredictions(game, message, contest);
        } catch (Exception e) {
            BotLogger.error("Failed to capture replay contest predictions.", e);
        }
    }

    private void capturePredictions(Game game, Message message, CombatContestEntity contest) {
        Map<String, Set<String>> factionsByUser = new HashMap<>();
        Map<String, String> namesByUser = new HashMap<>();
        Map<String, String> factionToEmoji = Map.of(
                contest.getAttackerFaction(), getFactionEmoji(game, contest.getAttackerFaction()),
                contest.getDefenderFaction(), getFactionEmoji(game, contest.getDefenderFaction()));

        for (MessageReaction reaction : message.getReactions()) {
            String predictedFaction = null;
            for (Map.Entry<String, String> entry : factionToEmoji.entrySet()) {
                if (reaction.getEmoji().getFormatted().equals(entry.getValue())) {
                    predictedFaction = entry.getKey();
                    break;
                }
            }
            if (predictedFaction == null) continue;

            List<User> users = reaction.retrieveUsers().complete();
            for (User user : users) {
                if (user.isBot()) continue;
                factionsByUser
                        .computeIfAbsent(user.getId(), key -> new HashSet<>())
                        .add(predictedFaction);
                namesByUser.put(user.getId(), user.getName());
            }
        }

        List<CombatContestPredictionEntity> predictions = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : factionsByUser.entrySet()) {
            if (entry.getValue().size() != 1) continue;
            CombatContestPredictionEntity prediction = new CombatContestPredictionEntity();
            prediction.setContestId(contest.getId());
            prediction.setDiscordUserId(entry.getKey());
            prediction.setDiscordUserName(namesByUser.getOrDefault(entry.getKey(), "Unknown User"));
            prediction.setPredictedFaction(entry.getValue().iterator().next());
            prediction.setLockedAt(LocalDateTime.now());
            predictions.add(prediction);
        }
        predictionRepository.saveAll(predictions);
    }

    private List<CombatContestPredictionEntity> awardPredictionPoints(CombatContestEntity contest) {
        List<CombatContestPredictionEntity> predictions = predictionRepository.findByContestId(contest.getId());
        if (predictions.isEmpty()) return predictions;

        int attackerPredictions = (int) predictions.stream()
                .filter(prediction -> prediction.getPredictedFaction().equalsIgnoreCase(contest.getAttackerFaction()))
                .count();
        int defenderPredictions = predictions.size() - attackerPredictions;
        int winnerPredictions = contest.getWinnerFaction().equalsIgnoreCase(contest.getAttackerFaction())
                ? attackerPredictions
                : defenderPredictions;
        int totalPredictions = attackerPredictions + defenderPredictions;
        for (CombatContestPredictionEntity prediction : predictions) {
            boolean correct = prediction.getPredictedFaction().equalsIgnoreCase(contest.getWinnerFaction());
            prediction.setCorrect(correct);
            prediction.setPointsAwarded(correct ? calculatePredictionPoints(winnerPredictions, totalPredictions) : 0);
        }
        predictionRepository.saveAll(predictions);
        return predictions;
    }

    private int calculatePredictionPoints(int winnerPredictions, int totalPredictions) {
        totalPredictions = Math.max(1, totalPredictions);
        double winnerShare = winnerPredictions / (double) totalPredictions;
        double scaledPoints = 4.0 / Math.max(winnerShare, ZERO_EPSILON);
        return (int) Math.round(Math.max(4.0, Math.min(100.0, scaledPoints)));
    }

    private void postPredictionPointsSummary(
            MessageChannel threadOrChannel, List<CombatContestPredictionEntity> predictions) {
        List<CombatContestPredictionEntity> winningPredictions = predictions.stream()
                .filter(prediction -> safeInt(prediction.getPointsAwarded()) > 0)
                .toList();
        if (winningPredictions.isEmpty()) return;

        Map<String, Integer> totalsByUser = predictionRepository
                .findPointTotalsByDiscordUserIdIn(winningPredictions.stream()
                        .map(CombatContestPredictionEntity::getDiscordUserId)
                        .toList())
                .stream()
                .collect(Collectors.toMap(
                        CombatContestUserPointsRow::getDiscordUserId, row -> safeInt(row.getTotalPoints())));

        String message = winningPredictions.stream()
                .sorted((left, right) -> {
                    int pointsComparison =
                            Integer.compare(safeInt(right.getPointsAwarded()), safeInt(left.getPointsAwarded()));
                    if (pointsComparison != 0) return pointsComparison;
                    return left.getDiscordUserName().compareToIgnoreCase(right.getDiscordUserName());
                })
                .map(prediction -> {
                    int pointsAwarded = safeInt(prediction.getPointsAwarded());
                    int totalPoints = totalsByUser.getOrDefault(prediction.getDiscordUserId(), 0);
                    return "<@" + prediction.getDiscordUserId() + "> - " + totalPoints + " points (+" + pointsAwarded
                            + ")";
                })
                .collect(Collectors.joining("\n", "## Prediction Points\n", ""));
        MessageHelper.splitAndSentWithAction(message, threadOrChannel, null);
    }

    private void maybePostLeaderboardAfterResolvedContest() {
        List<CombatContestEntity> pendingBatch =
                contestRepository
                        .findTop5ByStatusAndResolvedAtIsNotNullAndLeaderboardPostedAtIsNullOrderByResolvedAtAsc(
                                CombatContestStatus.RESOLVED);
        if (pendingBatch.size() < 5) return;
        if (!postLeaderboard()) return;

        LocalDateTime postedAt = LocalDateTime.now();
        pendingBatch.forEach(contest -> contest.setLeaderboardPostedAt(postedAt));
        contestRepository.saveAll(pendingBatch);
    }

    public boolean postLeaderboard() {
        String message = buildLeaderboardMessage();
        if (message == null) return false;
        TextChannel contestChannel = getContestPublicChannelByName();
        if (contestChannel == null) return false;
        MessageHelper.sendMessageToChannel(contestChannel, message);
        return true;
    }

    private String buildLeaderboardMessage() {
        List<CombatContestLeaderboardRow> topEntries = predictionRepository.findLeaderboardRows(PageRequest.of(0, 10));
        if (topEntries.isEmpty()) return null;
        final int[] rank = {1};
        return topEntries.stream()
                .map(entry -> {
                    long predictions = entry.getPredictionCount() == null ? 0 : entry.getPredictionCount();
                    long correctPredictions = entry.getCorrectPredictions() == null ? 0 : entry.getCorrectPredictions();
                    int accuracy = predictions == 0 ? 0 : Math.round((100f * correctPredictions) / predictions);
                    return '`' + Integer.toString(rank[0]++) + ".` "
                            + getSafeLeaderboardName(entry.getDiscordUserName()) + " - **" + entry.getTotalPoints()
                            + "** points (`" + correctPredictions + "/" + predictions + "` correct, " + accuracy
                            + "%)";
                })
                .collect(Collectors.joining("\n", "## Lazax War Archives Leaderboard\n", ""));
    }

    private TextChannel getContestPublicChannel(Long publicChannelId) {
        if (publicChannelId == null || JdaService.guildPrimary == null) return null;
        return JdaService.guildPrimary.getTextChannelById(publicChannelId);
    }

    private TextChannel getContestPublicChannelByName() {
        if (JdaService.guildPrimary == null) return null;
        return JdaService.guildPrimary.getTextChannelsByName("lazax-war-archives", true).stream()
                .findFirst()
                .orElse(null);
    }

    private MessageChannel getContestThreadOrChannel(CombatContestEntity contest) {
        if (JdaService.guildPrimary == null) return null;
        if (contest.getPublicThreadId() != null) {
            ThreadChannel thread = JdaService.guildPrimary.getThreadChannelById(contest.getPublicThreadId());
            if (thread != null) return thread;
        }
        return getContestPublicChannel(contest.getPublicChannelId());
    }

    private void addReaction(Player player, Message message) {
        if (player == null) return;
        try {
            message.addReaction(Emoji.fromFormatted(player.getFactionEmoji())).queue(null, BotLogger::catchRestError);
        } catch (Exception e) {
            BotLogger.error("Failed to parse replay contest prediction reaction emoji.", e);
        }
    }

    private String resolveTileRepresentation(Game game, CombatCandidateEntity candidate) {
        var tile = game.getTileByPosition(candidate.getTilePosition());
        return tile == null ? candidate.getTilePosition() : tile.getRepresentationForButtons();
    }

    private String getFactionEmoji(Game game, String faction) {
        Player player = game.getPlayerFromColorOrFaction(faction);
        return player == null ? "" : player.getFactionEmoji();
    }

    private String getSafeLeaderboardName(String userName) {
        if (userName == null || userName.isBlank()) return "Unknown User";
        return userName.replace("@", "@\u200B");
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}
