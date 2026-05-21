package ti4.service.statistics;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.commands.statistics.GameStatisticsFilterer;
import ti4.executors.ExecutionLockType;
import ti4.game.Game;
import ti4.game.GameStats;
import ti4.game.GameStats.ActionCardPlay;
import ti4.game.Player;
import ti4.game.persistence.ConsumeGameUtility;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;

@UtilityClass
public class ActionCardStatsService {
    static final LocalDate PLAYER_TRACKING_START_DATE = LocalDate.of(2026, 5, 22);
    private static final long PLAYER_TRACKING_START_MILLIS =
            PLAYER_TRACKING_START_DATE.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();

    public static void queueReply(SlashCommandInteractionEvent event) {
        StatisticsPipeline.queue(event, () -> showActionCardStats(event));
    }

    private static void showActionCardStats(SlashCommandInteractionEvent event) {
        Map<String, Integer> trackedPlayCounts = new HashMap<>();
        Map<String, Integer> sabotageCounts = new HashMap<>();
        Map<String, Integer> actionCardsPlayedCounts = new HashMap<>();
        Map<String, Integer> overruleCounts = new HashMap<>();
        Map<String, PlayToWinCorrelationCount> playToWinCorrelationCounts = new HashMap<>();

        ConsumeGameUtility.consumeAllGames(
                GameStatisticsFilterer.getGamesFilter(event),
                game -> accumulateActionCardStats(
                        game,
                        trackedPlayCounts,
                        sabotageCounts,
                        actionCardsPlayedCounts,
                        overruleCounts,
                        playToWinCorrelationCounts),
                ExecutionLockType.READ);

        MessageHelper.sendMessageToThread(
                event.getChannel(),
                "Action Card Play Statistics",
                buildMessage(
                        trackedPlayCounts,
                        sabotageCounts,
                        actionCardsPlayedCounts,
                        overruleCounts,
                        playToWinCorrelationCounts));
    }

    private static void accumulateActionCardStats(
            Game game,
            Map<String, Integer> trackedPlayCounts,
            Map<String, Integer> sabotageCounts,
            Map<String, Integer> actionCardsPlayedCounts,
            Map<String, Integer> overruleCounts,
            Map<String, PlayToWinCorrelationCount> playToWinCorrelationCounts) {
        if (game.getStartedDate() >= PLAYER_TRACKING_START_MILLIS) {
            for (ActionCardPlay actionCardPlay : game.getGameStats().getActionCardPlays()) {
                trackedPlayCounts.merge(actionCardPlay.getActionCard(), 1, Integer::sum);
            }
        }

        game.getGameStats()
                .getCountPerTarget(GameStats.SABOTAGE)
                .forEach((acName, count) -> sabotageCounts.merge(acName, count, Integer::sum));

        game.getGameStats()
                .getCountPerTarget(GameStats.OVERRULE)
                .forEach((scName, count) -> overruleCounts.merge(scName, count, Integer::sum));

        game.getDiscardActionCards()
                .forEach((acID, ignored) -> incrementActionCardPlayCount(actionCardsPlayedCounts, acID));
        game.getPurgedActionCards()
                .forEach((acID, ignored) -> incrementActionCardPlayCount(actionCardsPlayedCounts, acID));
        accumulateActionCardPlayToWinCorrelation(game, playToWinCorrelationCounts);
    }

    private static void incrementActionCardPlayCount(
            Map<String, Integer> actionCardsPlayedCounts, String actionCardId) {
        ActionCardModel actionCardModel = Mapper.getActionCard(actionCardId);
        String name = actionCardModel != null ? actionCardModel.getName() : actionCardId;
        actionCardsPlayedCounts.merge(name, 1, Integer::sum);
    }

    private static String buildMessage(
            Map<String, Integer> trackedPlayCounts,
            Map<String, Integer> sabotageCounts,
            Map<String, Integer> actionCardsPlayedCounts,
            Map<String, Integer> overruleCounts,
            Map<String, PlayToWinCorrelationCount> playToWinCorrelationCounts) {
        StringBuilder message = new StringBuilder();
        message.append("**Tracked action card plays**\n");
        appendTrackedPlayStats(message, trackedPlayCounts);
        message.append("\n**Action card play-to-win correlation**\n");
        appendPlayToWinCorrelationStats(message, playToWinCorrelationCounts);
        message.append("**Sabotage targets**\n");
        appendSabotageStats(message, sabotageCounts, actionCardsPlayedCounts);
        message.append("\n**Overrule choices**\n");
        appendOverruleStats(message, overruleCounts);
        return message.toString();
    }

    private static void appendTrackedPlayStats(StringBuilder message, Map<String, Integer> trackedPlayCounts) {
        if (trackedPlayCounts.isEmpty()) {
            message.append("No tracked action card play data matched the selected filters.\n");
            return;
        }

        trackedPlayCounts.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, Integer> entry) -> entry.getValue())
                        .reversed()
                        .thenComparing(Map.Entry::getKey))
                .forEach(entry -> message.append("- ")
                        .append(entry.getKey())
                        .append(": ")
                        .append(entry.getValue())
                        .append('\n'));
    }

    private static void appendSabotageStats(
            StringBuilder message, Map<String, Integer> sabotageCounts, Map<String, Integer> actionCardsPlayedCounts) {
        if (sabotageCounts.isEmpty()) {
            message.append("No Sabotage data matched the selected filters.\n");
            return;
        }

        sabotageCounts.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, Integer> entry) -> entry.getValue())
                        .reversed()
                        .thenComparing(Map.Entry::getKey))
                .forEach(entry -> message.append("- ")
                        .append(entry.getKey())
                        .append(": ")
                        .append(entry.getValue())
                        .append(" out of ")
                        .append(actionCardsPlayedCounts.getOrDefault(entry.getKey(), 0))
                        .append(" times played\n"));
    }

    private static void appendOverruleStats(StringBuilder message, Map<String, Integer> overruleCounts) {
        if (overruleCounts.isEmpty()) {
            message.append("No Overrule data matched the selected filters.\n");
            return;
        }

        overruleCounts.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, Integer> entry) -> entry.getValue())
                        .reversed()
                        .thenComparing(Map.Entry::getKey))
                .forEach(entry -> message.append("- ")
                        .append(entry.getKey())
                        .append(": ")
                        .append(entry.getValue())
                        .append('\n'));
    }

    static void accumulateActionCardPlayToWinCorrelation(
            Game game, Map<String, PlayToWinCorrelationCount> playToWinCorrelationCounts) {
        if (game.getStartedDate() < PLAYER_TRACKING_START_MILLIS) {
            return;
        }

        Player winner = game.getWinner().orElse(null);
        if (winner == null) {
            return;
        }

        String winningPlayerId = StringUtils.defaultIfBlank(winner.getStatsTrackedUserID(), winner.getUserID());
        for (ActionCardPlay actionCardPlay : game.getGameStats().getActionCardPlays()) {
            if (StringUtils.isBlank(actionCardPlay.getPlayerId())) {
                continue;
            }

            PlayToWinCorrelationCount count = playToWinCorrelationCounts.computeIfAbsent(
                    actionCardPlay.getActionCard(), _ -> new PlayToWinCorrelationCount());
            count.incrementTotal();
            if (winningPlayerId.equals(actionCardPlay.getPlayerId())) {
                count.incrementWins();
            }
        }
    }

    static void appendPlayToWinCorrelationStats(
            StringBuilder message, Map<String, PlayToWinCorrelationCount> playToWinCorrelationCounts) {
        message.append("_Only includes games started on or after ")
                .append(PLAYER_TRACKING_START_DATE)
                .append("; per-player tracking started on that date._\n");
        if (playToWinCorrelationCounts.isEmpty()) {
            message.append("No eligible action card play-to-win correlation data matched the selected filters.\n");
            return;
        }

        playToWinCorrelationCounts.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, PlayToWinCorrelationCount>, Double>comparing(
                                entry -> entry.getValue().getWinRate())
                        .reversed()
                        .thenComparing(entry -> entry.getValue().getTotal(), Comparator.reverseOrder())
                        .thenComparing(Map.Entry::getKey))
                .forEach(entry -> message.append("- ")
                        .append(entry.getKey())
                        .append(": ")
                        .append(String.format("%.1f%%", entry.getValue().getWinRate() * 100))
                        .append(" (")
                        .append(entry.getValue().getWins())
                        .append('/')
                        .append(entry.getValue().getTotal())
                        .append(" plays by the eventual winner)\n"));
    }

    @Getter
    static class PlayToWinCorrelationCount {
        private int total;
        private int wins;

        void incrementTotal() {
            total++;
        }

        void incrementWins() {
            wins++;
        }

        double getWinRate() {
            return total == 0 ? 0 : (double) wins / total;
        }
    }
}
