package ti4.service.statistics;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.statistics.GameStatisticsFilterer;
import ti4.executors.ExecutionLockType;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.ConsumeGameUtility;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.service.fow.FogGameSummaryService;
import ti4.service.game.GameSummaryService.ModeBreakdown;
import ti4.service.option.FOWOptionService.FOWOption;

@UtilityClass
public class FogEndedGameStatisticsService {

    public static void queueReply(SlashCommandInteractionEvent event) {
        StatisticsPipeline.queue(event, () -> showFogStatistics(event));
    }

    public static boolean isEndedFogGame(Game game) {
        return game.isHasEnded() && (game.isFowMode() || game.isLightFogMode());
    }

    private static void showFogStatistics(SlashCommandInteractionEvent event) {
        FogTally tally = new FogTally();
        ConsumeGameUtility.consumeAllGames(
                GameStatisticsFilterer.getGamesFilter(event).and(FogEndedGameStatisticsService::isEndedFogGame),
                tally::add,
                ExecutionLockType.READ);
        MessageHelper.sendMessageToThread(event.getChannel(), "Ended Fog Game Statistics", tally.report());
    }

    static class FogTally {

        private int games;
        private final Map<Integer, Integer> playerCounts = new TreeMap<>();
        private final Map<Integer, Integer> galaxyCounts = new TreeMap<>();
        private int sectorGames;
        private final Map<String, Integer> variants = new HashMap<>();
        private final Map<String, Integer> factionGames = new HashMap<>();
        private final Map<String, Integer> factionWins = new HashMap<>();
        private final IntSummaryStatistics tiles = new IntSummaryStatistics();
        private final Map<String, Integer> expansions = new HashMap<>();
        private final Map<String, Integer> homebrew = new HashMap<>();
        private final Map<String, Integer> scenarios = new HashMap<>();
        private final Map<String, Integer> otherModes = new HashMap<>();
        private final Map<FOWOption, Integer> options = new EnumMap<>(FOWOption.class);

        void add(Game game) {
            games++;
            playerCounts.merge(game.getRealAndEliminatedPlayers().size(), 1, Integer::sum);
            variants.merge(FogGameSummaryService.fogVariant(game), 1, Integer::sum);
            tiles.accept(game.getTileMap().size());
            galaxyCounts.merge(FogGameSummaryService.galaxyCount(game), 1, Integer::sum);
            if (FogGameSummaryService.usesSectors(game)) {
                sectorGames++;
            }
            game.getRealAndEliminatedPlayers().forEach(player -> countFaction(factionGames, player));
            game.getWinners().forEach(player -> countFaction(factionWins, player));
            ModeBreakdown modes = ModeBreakdown.of(game);
            modes.expansions().forEach(mode -> expansions.merge(mode, 1, Integer::sum));
            modes.homebrew().forEach(mode -> homebrew.merge(mode, 1, Integer::sum));
            modes.scenarios().forEach(mode -> scenarios.merge(mode, 1, Integer::sum));
            modes.other().forEach(mode -> otherModes.merge(mode, 1, Integer::sum));
            FogGameSummaryService.enabledOptions(game).forEach(option -> options.merge(option, 1, Integer::sum));
        }

        int games() {
            return games;
        }

        Map<String, Integer> variants() {
            return variants;
        }

        Map<FOWOption, Integer> options() {
            return options;
        }

        Map<String, Integer> factionGames() {
            return factionGames;
        }

        Map<String, Integer> factionWins() {
            return factionWins;
        }

        Map<Integer, Integer> galaxyCounts() {
            return galaxyCounts;
        }

        int sectorGames() {
            return sectorGames;
        }

        IntSummaryStatistics tiles() {
            return tiles;
        }

        String report() {
            if (games == 0) {
                return "No ended fog games matched the selected filters.";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("**Games:** ").append(games).append('\n');
            sb.append("**Player counts:** ")
                    .append(playerCounts.entrySet().stream()
                            .map(entry -> entry.getKey() + "p: " + entry.getValue())
                            .collect(Collectors.joining(", ")))
                    .append('\n');
            sb.append("**Tiles per game:** min ")
                    .append(tiles.getMin())
                    .append(" · avg ")
                    .append(String.format(Locale.ROOT, "%.1f", tiles.getAverage()))
                    .append(" · max ")
                    .append(tiles.getMax())
                    .append('\n');
            sb.append("**Galaxies per game:** ")
                    .append(galaxyCounts.entrySet().stream()
                            .map(entry -> entry.getKey() + ": " + entry.getValue())
                            .collect(Collectors.joining(", ")))
                    .append('\n');
            sb.append("**Games with custom or auto sectors:** ")
                    .append(sectorGames)
                    .append('/')
                    .append(games)
                    .append("\n\n");
            appendSection(sb, "Fog type", variants, Function.identity());
            appendSection(sb, "Expansions", expansions, Function.identity());
            appendSection(sb, "Homebrew", homebrew, Function.identity());
            appendSection(sb, "Scenarios", scenarios, Function.identity());
            appendSection(sb, "Other modes & events", otherModes, Function.identity());
            appendSection(sb, "Fog options enabled", options, FOWOption::getTitle);
            appendFactions(sb);
            return sb.toString();
        }

        private <K> void appendSection(
                StringBuilder sb, String title, Map<K, Integer> counts, Function<K, String> label) {
            sb.append("### ").append(title).append('\n');
            if (counts.isEmpty()) {
                sb.append("None\n");
                return;
            }
            counts.entrySet().stream()
                    .sorted(Map.Entry.<K, Integer>comparingByValue().reversed())
                    .forEach(entry -> sb.append(label.apply(entry.getKey()))
                            .append(": ")
                            .append(entry.getValue())
                            .append('/')
                            .append(games)
                            .append(" (")
                            .append(Math.round(100.0 * entry.getValue() / games))
                            .append("%)\n"));
        }

        private void appendFactions(StringBuilder sb) {
            sb.append("### Factions (games · wins)\n");
            List<Map.Entry<String, Integer>> sorted = factionGames.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue()
                            .reversed()
                            .thenComparing(Map.Entry.comparingByKey(Comparator.naturalOrder())))
                    .toList();
            for (Map.Entry<String, Integer> entry : sorted) {
                sb.append(factionLabel(entry.getKey()))
                        .append(": ")
                        .append(entry.getValue())
                        .append(" · ")
                        .append(factionWins.getOrDefault(entry.getKey(), 0))
                        .append('\n');
            }
        }

        private static void countFaction(Map<String, Integer> counts, Player player) {
            String faction = player.getFaction();
            if (faction != null && !"null".equals(faction)) {
                counts.merge(faction, 1, Integer::sum);
            }
        }

        private static String factionLabel(String faction) {
            FactionModel model = Mapper.getFaction(faction);
            return model == null ? faction : model.getFactionName();
        }
    }
}
