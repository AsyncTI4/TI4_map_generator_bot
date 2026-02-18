package ti4.service.statistics.game;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.statistics.GameStatisticsFilterer;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.persistence.GamesPage;
import ti4.message.MessageHelper;
import ti4.model.DeckModel;

@UtilityClass
class RelicDrawPercentageStatisticsService {

    static void showRelicDrawPercentage(SlashCommandInteractionEvent event) {
        Map<String, Integer> gamesAvailableByRelic = new HashMap<>();
        Map<String, Integer> gamesDrawnByRelic = new HashMap<>();

        GamesPage.consumeAllGames(
                GameStatisticsFilterer.getGamesFilter(event),
                game -> addRelicDrawInfo(game, gamesAvailableByRelic, gamesDrawnByRelic));

        String output = buildOutput(gamesAvailableByRelic, gamesDrawnByRelic);
        MessageHelper.sendMessageToThread(event.getChannel(), "Relic Draw Percentage", output);
    }

    private static void addRelicDrawInfo(
            Game game, Map<String, Integer> gamesAvailableByRelic, Map<String, Integer> gamesDrawnByRelic) {
        DeckModel relicDeck = Mapper.getDeck(game.getRelicDeckID());
        if (relicDeck == null) {
            return;
        }

        for (String relicId : relicDeck.getNewDeck()) {
            gamesAvailableByRelic.merge(relicId, 1, Integer::sum);
            if (!game.getAllRelics().contains(relicId)) {
                gamesDrawnByRelic.merge(relicId, 1, Integer::sum);
            }
        }
    }

    private static String buildOutput(Map<String, Integer> gamesAvailableByRelic, Map<String, Integer> gamesDrawnByRelic) {
        if (gamesAvailableByRelic.isEmpty()) {
            return "No relic data found for the selected filters.";
        }

        int totalAvailable = gamesAvailableByRelic.values().stream().mapToInt(Integer::intValue).sum();
        int totalDrawn = gamesDrawnByRelic.values().stream().mapToInt(Integer::intValue).sum();

        StringBuilder sb = new StringBuilder();
        sb.append("Overall relic draw rate (when available): ")
                .append(formatPercentage(totalDrawn, totalAvailable))
                .append(" (`")
                .append(totalDrawn)
                .append("/")
                .append(totalAvailable)
                .append("`)\n\n");

        Map<String, Integer> sortedRelics = gamesAvailableByRelic.entrySet().stream()
                .sorted(Comparator.comparingDouble((Map.Entry<String, Integer> entry) -> {
                            int available = entry.getValue();
                            int drawn = gamesDrawnByRelic.getOrDefault(entry.getKey(), 0);
                            return available == 0 ? 0 : (double) drawn / available;
                        })
                        .reversed()
                        .thenComparing(entry -> Mapper.getRelic(entry.getKey()) == null
                                ? entry.getKey()
                                : Mapper.getRelic(entry.getKey()).getName()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> left,
                        java.util.LinkedHashMap::new));

        for (Map.Entry<String, Integer> relicEntry : sortedRelics.entrySet()) {
            String relicId = relicEntry.getKey();
            int available = relicEntry.getValue();
            int drawn = gamesDrawnByRelic.getOrDefault(relicId, 0);
            String relicName = Mapper.getRelic(relicId) == null ? relicId : Mapper.getRelic(relicId).getName();
            sb.append("- ")
                    .append(relicName)
                    .append(": ")
                    .append(formatPercentage(drawn, available))
                    .append(" (`")
                    .append(drawn)
                    .append("/")
                    .append(available)
                    .append("`)\n");
        }

        return sb.toString();
    }

    private static String formatPercentage(int numerator, int denominator) {
        if (denominator == 0) {
            return "0.00%";
        }
        return String.format("%.2f%%", (100.0 * numerator) / denominator);
    }
}
