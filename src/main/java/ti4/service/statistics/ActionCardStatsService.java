package ti4.service.statistics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
    private static final LocalDate PLAYER_TRACKING_START_DATE = LocalDate.of(2026, 5, 23);

    public static void queueReply(SlashCommandInteractionEvent event) {
        StatisticsPipeline.queue(event, () -> showActionCardStats(event));
    }

    private static void showActionCardStats(SlashCommandInteractionEvent event) {
        Map<String, Integer> sabotageCounts = new HashMap<>();
        Map<String, Integer> actionCardsPlayedCounts = new HashMap<>();
        Map<String, Integer> overruleCounts = new HashMap<>();
        Map<String, PlayToWinCorrelationCount> playToWinCorrelationCounts = new HashMap<>();

        ConsumeGameUtility.consumeAllGames(
                GameStatisticsFilterer.getGamesFilter(event),
                game -> accumulateActionCardStats(
                        game, sabotageCounts, actionCardsPlayedCounts, overruleCounts, playToWinCorrelationCounts),
                ExecutionLockType.READ);

        MessageHelper.sendMessageToThread(
                event.getChannel(),
                "Action Card Play Statistics",
                buildMessage(sabotageCounts, actionCardsPlayedCounts, overruleCounts, playToWinCorrelationCounts));
    }

    private static void accumulateActionCardStats(
            Game game,
            Map<String, Integer> sabotageCounts,
            Map<String, Integer> actionCardsPlayedCounts,
            Map<String, Integer> overruleCounts,
            Map<String, PlayToWinCorrelationCount> playToWinCorrelationCounts) {
        game.getGameStats()
                .getCountPerTarget(GameStats.SABOTAGE)
                .forEach((acName, count) -> sabotageCounts.merge(acName, count, Integer::sum));

        game.getGameStats()
                .getCountPerTarget(GameStats.OVERRULE)
                .forEach((scName, count) -> overruleCounts.merge(scName, count, Integer::sum));

        game.getDiscardActionCards()
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
            Map<String, Integer> sabotageCounts,
            Map<String, Integer> actionCardsPlayedCounts,
            Map<String, Integer> overruleCounts,
            Map<String, PlayToWinCorrelationCount> playToWinCorrelationCounts) {
        StringBuilder message = new StringBuilder();
        message.append("\n**Action card plays and Sabotage rate**\n");
        appendActionCardPlayAndSabotageStats(message, actionCardsPlayedCounts, sabotageCounts);
        message.append("\n**Action card play-to-win correlation**\n");
        appendTrackingStartNote(message);
        appendPlayToWinCorrelationStats(message, playToWinCorrelationCounts);
        message.append("\n**Overrule targets**\n");
        appendTrackingStartNote(message);
        appendOverruleStats(message, overruleCounts);
        return message.toString();
    }

    private static void appendTrackingStartNote(StringBuilder message) {
        message.append("_We started tracking these on ")
                .append(PLAYER_TRACKING_START_DATE)
                .append("._\n");
    }

    private static void appendActionCardPlayAndSabotageStats(
            StringBuilder message, Map<String, Integer> actionCardsPlayedCounts, Map<String, Integer> sabotageCounts) {
        if (actionCardsPlayedCounts.isEmpty() && sabotageCounts.isEmpty()) {
            message.append("No action card play or Sabotage data matched the selected filters.\n");
            return;
        }

        Set<String> actionCardNames = new HashSet<>(actionCardsPlayedCounts.keySet());
        actionCardNames.addAll(sabotageCounts.keySet());

        actionCardNames.stream()
                .sorted(Comparator.comparingInt(
                                (String actionCardName) -> actionCardsPlayedCounts.getOrDefault(actionCardName, 0))
                        .reversed()
                        .thenComparing(
                                actionCardName -> sabotageCounts.getOrDefault(actionCardName, 0),
                                Comparator.reverseOrder())
                        .thenComparing(actionCardName -> actionCardName))
                .forEach(actionCardName -> {
                    int playCount = actionCardsPlayedCounts.getOrDefault(actionCardName, 0);
                    int sabotageCount = sabotageCounts.getOrDefault(actionCardName, 0);
                    double sabotageRate = playCount == 0 ? 0 : (double) sabotageCount / playCount;
                    message.append("- ")
                            .append(actionCardName)
                            .append(": ")
                            .append(playCount)
                            .append(" played, ")
                            .append(sabotageCount)
                            .append(" Sabotaged (")
                            .append(formatSabotageRate(sabotageRate))
                            .append(")\n");
                });
    }

    private static String formatSabotageRate(double sabotageRate) {
        return BigDecimal.valueOf(sabotageRate * 100)
                        .setScale(2, RoundingMode.HALF_UP)
                        .stripTrailingZeros()
                        .toPlainString()
                + "%";
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

    private static void accumulateActionCardPlayToWinCorrelation(
            Game game, Map<String, PlayToWinCorrelationCount> playToWinCorrelationCounts) {
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

    private static void appendPlayToWinCorrelationStats(
            StringBuilder message, Map<String, PlayToWinCorrelationCount> playToWinCorrelationCounts) {
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
