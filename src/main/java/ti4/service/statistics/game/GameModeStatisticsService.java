package ti4.service.statistics.game;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.statistics.GameStatisticsFilterer;
import ti4.map.persistence.GamesPage;
import ti4.message.MessageHelper;
import ti4.service.game.GameModeService;

@UtilityClass
class GameModeStatisticsService {

    static void showModeCounts(SlashCommandInteractionEvent event) {
        AtomicInteger totalGames = new AtomicInteger();
        Map<String, Integer> modeCounts = new HashMap<>();

        GamesPage.consumeAllGames(GameStatisticsFilterer.getGamesFilter(event), game -> {
            totalGames.incrementAndGet();
            GameModeService.getModes(game).forEach(modeName -> modeCounts.merge(modeName, 1, Integer::sum));
        });

        MessageHelper.sendMessageToThread(
                (MessageChannelUnion) event.getMessageChannel(),
                "Game Mode Counts",
                formatModeStatistics(totalGames.get(), modeCounts));
    }

    private static String formatModeStatistics(int totalGames, Map<String, Integer> modeCounts) {
        StringBuilder message = new StringBuilder("Game count by mode (total games: " + totalGames + "):\n");
        modeCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> {
                    double percent = totalGames == 0 ? 0.0 : (entry.getValue() * 100.0) / totalGames;
                    message.append("- ")
                            .append(entry.getKey())
                            .append(": ")
                            .append(entry.getValue())
                            .append(" (")
                            .append(String.format("%.1f%%", percent))
                            .append(")\n");
                });
        return message.toString();
    }
}
