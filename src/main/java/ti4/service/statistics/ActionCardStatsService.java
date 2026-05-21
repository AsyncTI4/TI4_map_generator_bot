package ti4.service.statistics;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.statistics.GameStatisticsFilterer;
import ti4.executors.ExecutionLockType;
import ti4.game.Game;
import ti4.game.GameStats;
import ti4.game.persistence.ConsumeGameUtility;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;

@UtilityClass
public class ActionCardStatsService {

    public static void queueReply(SlashCommandInteractionEvent event) {
        StatisticsPipeline.queue(event, () -> showActionCardStats(event));
    }

    private static void showActionCardStats(SlashCommandInteractionEvent event) {
        Map<String, Integer> sabotageCounts = new HashMap<>();
        Map<String, Integer> actionCardsPlayedCounts = new HashMap<>();
        Map<String, Integer> overruleCounts = new HashMap<>();
        AtomicInteger totalSabotages = new AtomicInteger();
        AtomicInteger totalOverrules = new AtomicInteger();

        ConsumeGameUtility.consumeAllGames(
                GameStatisticsFilterer.getGamesFilter(event),
                game -> accumulateActionCardStats(
                        game, sabotageCounts, actionCardsPlayedCounts, overruleCounts, totalSabotages, totalOverrules),
                ExecutionLockType.READ);

        MessageHelper.sendMessageToThread(
                event.getChannel(),
                "Action Card Statistics",
                buildMessage(sabotageCounts, actionCardsPlayedCounts, overruleCounts, totalSabotages, totalOverrules));
    }

    private static void accumulateActionCardStats(
            Game game,
            Map<String, Integer> sabotageCounts,
            Map<String, Integer> actionCardsPlayedCounts,
            Map<String, Integer> overruleCounts,
            AtomicInteger totalSabotages,
            AtomicInteger totalOverrules) {
        totalSabotages.addAndGet(game.getGameStats().getTotalPlays(GameStats.SABOTAGE));
        game.getGameStats()
                .getCountPerTarget(GameStats.SABOTAGE)
                .forEach((acName, count) -> sabotageCounts.merge(acName, count, Integer::sum));

        totalOverrules.addAndGet(game.getGameStats().getTotalPlays(GameStats.OVERRULE));
        game.getGameStats()
                .getCountPerTarget(GameStats.OVERRULE)
                .forEach((scName, count) -> overruleCounts.merge(scName, count, Integer::sum));

        game.getDiscardActionCards().forEach((acID, ignored) -> incrementActionCardPlayCount(actionCardsPlayedCounts, acID));
        game.getPurgedActionCards().forEach((acID, ignored) -> incrementActionCardPlayCount(actionCardsPlayedCounts, acID));
    }

    private static void incrementActionCardPlayCount(Map<String, Integer> actionCardsPlayedCounts, String actionCardId) {
        ActionCardModel actionCardModel = Mapper.getActionCard(actionCardId);
        if (actionCardModel == null) {
            return;
        }
        actionCardsPlayedCounts.merge(actionCardModel.getName(), 1, Integer::sum);
    }

    private static String buildMessage(
            Map<String, Integer> sabotageCounts,
            Map<String, Integer> actionCardsPlayedCounts,
            Map<String, Integer> overruleCounts,
            AtomicInteger totalSabotages,
            AtomicInteger totalOverrules) {
        StringBuilder message = new StringBuilder();
        message.append("Recorded Sabotages: ").append(totalSabotages.get()).append('\n');
        message.append("Recorded Overrules: ").append(totalOverrules.get()).append("\n\n");
        message.append("**Sabotage targets**\n");
        appendSabotageStats(message, sabotageCounts, actionCardsPlayedCounts);
        message.append("\n**Overrule choices**\n");
        appendOverruleStats(message, overruleCounts);
        return message.toString();
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
}
