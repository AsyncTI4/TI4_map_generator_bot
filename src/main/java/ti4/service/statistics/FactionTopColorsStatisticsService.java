package ti4.service.statistics;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.statistics.GameStatisticsFilterer;
import ti4.image.Mapper;
import ti4.map.persistence.GamesPage;
import ti4.message.MessageHelper;

@UtilityClass
public class FactionTopColorsStatisticsService {

    private static final int TOP_COLORS_PER_FACTION = 8;

    public static void queueReply(SlashCommandInteractionEvent event) {
        StatisticsPipeline.queue(event, () -> showTopColorsByFaction(event));
    }

    private static void showTopColorsByFaction(SlashCommandInteractionEvent event) {
        Map<String, Map<String, Integer>> colorCountsByFaction = new HashMap<>();

        GamesPage.consumeAllGames(GameStatisticsFilterer.getGamesFilter(event), game -> game.getRealPlayers()
                .forEach(player -> {
                    String faction = player.getFaction();
                    String color = player.getColor();
                    if (faction == null || color == null || "null".equals(color)) {
                        return;
                    }

                    colorCountsByFaction
                            .computeIfAbsent(faction, ignored -> new HashMap<>())
                            .merge(color, 1, Integer::sum);
                }));

        String message = Mapper.getFactionsValues().stream()
                .map(factionModel ->
                        formatFactionLine(factionModel.getAlias(), factionModel.getFactionName(), colorCountsByFaction))
                .filter(line -> line != null)
                .collect(Collectors.joining("\n"));

        if (message.isBlank()) {
            message = "No faction color data matched the selected filters.";
        }

        MessageHelper.sendMessageToThread(event.getChannel(), "Top 8 Colors by Faction", message);
    }

    private static String formatFactionLine(
            String factionAlias, String factionName, Map<String, Map<String, Integer>> colorCountsByFaction) {
        Map<String, Integer> colorCounts = colorCountsByFaction.get(factionAlias);
        if (colorCounts == null || colorCounts.isEmpty()) {
            return null;
        }

        List<String> topColors = colorCounts.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, Integer> entry) -> entry.getValue())
                        .reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(TOP_COLORS_PER_FACTION)
                .map(entry -> {
                    String colorDisplayName = Mapper.getColorDisplayName(entry.getKey());
                    String colorName = colorDisplayName == null ? entry.getKey() : colorDisplayName;
                    return colorName + " (" + entry.getValue() + ")";
                })
                .toList();

        return "**" + factionName + "**: " + String.join(", ", topColors);
    }
}
