package ti4.service.statistics.game;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.statistics.GameStatisticsFilterer;
import ti4.map.Game;
import ti4.map.persistence.GamesPage;
import ti4.message.MessageHelper;

@UtilityClass
class GameModeStatisticsService {

    static void showModeCounts(SlashCommandInteractionEvent event) {
        Map<String, Predicate<Game>> modePredicates = modePredicateByName();
        AtomicInteger totalGames = new AtomicInteger();
        Map<String, AtomicInteger> modeCounts = buildModeCounters(modePredicates);

        GamesPage.consumeAllGames(GameStatisticsFilterer.getGamesFilter(event), game -> {
            totalGames.incrementAndGet();
            modePredicates.forEach((modeName, modePredicate) -> {
                if (modePredicate.test(game)) {
                    modeCounts.get(modeName).incrementAndGet();
                }
            });
        });

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(), formatModeStatistics(totalGames.get(), modeCounts));
    }

    static String formatModeStatistics(int totalGames, Map<String, AtomicInteger> modeCounts) {
        StringBuilder message = new StringBuilder("Game count by mode (total games: " + totalGames + "):\n");
        modeCounts.forEach((modeName, modeCount) -> {
            double percent = totalGames == 0 ? 0.0 : (modeCount.get() * 100.0) / totalGames;
            message.append("- ")
                    .append(modeName)
                    .append(": ")
                    .append(modeCount.get())
                    .append(" (")
                    .append(String.format("%.1f%%", percent))
                    .append(")\n");
        });
        return message.toString();
    }

    private static Map<String, AtomicInteger> buildModeCounters(Map<String, Predicate<Game>> modePredicates) {
        Map<String, AtomicInteger> modeCounts = new LinkedHashMap<>();
        modePredicates.keySet().forEach(mode -> modeCounts.put(mode, new AtomicInteger()));
        return modeCounts;
    }

    private static Map<String, Predicate<Game>> modePredicateByName() {
        Map<String, Predicate<Game>> modes = new LinkedHashMap<>();
        modes.put("Prophecy of Kings", Game::isProphecyOfKings);
        modes.put("Thunder's Edge", Game::isThundersEdge);
        modes.put("Discordant Stars", Game::isDiscordantStarsMode);
        return modes;
    }
}
