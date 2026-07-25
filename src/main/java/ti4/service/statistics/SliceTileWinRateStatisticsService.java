package ti4.service.statistics;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.commands.statistics.GameStatisticsFilterer;
import ti4.executors.ExecutionLockType;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.persistence.ConsumeGameUtility;
import ti4.image.Mapper;
import ti4.image.TileHelper;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.model.PlanetModel;
import ti4.model.TileModel;

@UtilityClass
public class SliceTileWinRateStatisticsService {

    private static final String ENTROPIC_SCAR_TILE_ID = "114";

    private static final int TOP_BOTTOM_COUNT = 3;

    static final Map<String, List<String>> SLICE_POSITIONS_BY_HOME = Map.of(
            "301", List.of("318", "302", "201", "101"),
            "304", List.of("303", "305", "203", "102"),
            "307", List.of("306", "308", "205", "103"),
            "310", List.of("309", "311", "207", "104"),
            "313", List.of("312", "314", "209", "105"),
            "316", List.of("315", "317", "211", "106"));

    private static final Comparator<Entry<String, WinRateCount>> BY_WIN_RATE_DESC = Comparator.comparingDouble(
                    (Entry<String, WinRateCount> entry) -> entry.getValue().winRate())
            .thenComparingInt(entry -> entry.getValue().total)
            .reversed()
            .thenComparing(entry -> tileName(entry.getKey()));

    public static void queueReply(SlashCommandInteractionEvent event) {
        StatisticsPipeline.queue(event, () -> showSliceTileWinRates(event));
    }

    private static void showSliceTileWinRates(SlashCommandInteractionEvent event) {
        AtomicInteger gameCount = new AtomicInteger();
        SliceTileWinRateStats stats = new SliceTileWinRateStats();

        ConsumeGameUtility.consumeAllGames(
                GameStatisticsFilterer.getStandardCompetitiveGamesFilter(),
                game -> {
                    if (!hasStandardSixPlayerRingLayout(game)) {
                        stats.excludedGames++;
                        return;
                    }
                    gameCount.incrementAndGet();
                    accumulateGame(game, stats);
                },
                ExecutionLockType.READ);

        MessageHelper.sendMessageToThread(
                event.getChannel(), "Slice tile win rates", buildReport(gameCount.get(), stats));
    }

    static String buildReport(List<Game> games) {
        SliceTileWinRateStats stats = new SliceTileWinRateStats();
        int gameCount = 0;
        for (Game game : games) {
            if (!hasStandardSixPlayerRingLayout(game)) {
                stats.excludedGames++;
                continue;
            }
            gameCount++;
            accumulateGame(game, stats);
        }
        return buildReport(gameCount, stats);
    }

    private static String buildReport(int gameCount, SliceTileWinRateStats stats) {
        if (gameCount == 0) {
            return "No standard 6-player competitive games were available for slice analysis."
                    + (stats.excludedGames == 0
                            ? ""
                            : " (" + stats.excludedGames + " skipped for a non-standard map layout.)");
        }

        StringBuilder sb = new StringBuilder("## __**Slice Tile Win Rates**__\n");
        sb.append("_A slice is the systems adjacent to a player's home, plus the ring-1 system"
                + " adjacent to `000` nearest to them._\n");
        sb.append("_6-player, 10-victory-point, non-fog, non-Galactic-Event, non-Scenario, non-homebrew games"
                + " with winners, on the standard ring layout._\n");
        sb.append("Games analyzed: ")
                .append(gameCount)
                .append(" | Slices analyzed: ")
                .append(stats.sliceCount)
                .append(" | Skipped for non-standard layout: ")
                .append(stats.excludedGames)
                .append('\n');
        if (stats.skippedNonSystemTiles > 0) {
            sb.append("_Ignored ")
                    .append(stats.skippedNonSystemTiles)
                    .append(" slice position(s) holding a blank or placeholder tile rather than a system._\n");
        }

        appendOverallSection(sb, stats);
        appendBestAndWorstByFactionSection(sb, stats);
        appendSpecialTileSection(sb, stats);

        return sb.toString();
    }

    private static void accumulateGame(Game game, SliceTileWinRateStats stats) {
        Set<String> winningFactions = game.getWinners().stream()
                .map(Player::getFaction)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (Player player : game.getRealAndEliminatedPlayers()) {
            String faction = player.getFaction();
            List<String> slicePositions = SLICE_POSITIONS_BY_HOME.get(homePosition(player));
            if (faction == null || slicePositions == null) {
                continue;
            }

            boolean isWinner = winningFactions.contains(faction);
            List<String> factionKeys = FactionStatisticsHelper.getStatisticsFactionKeys(faction);
            stats.sliceCount++;
            for (String factionKey : factionKeys) {
                stats.factionRecords
                        .computeIfAbsent(factionKey, _ -> new WinRateCount())
                        .record(isWinner);
            }

            for (String position : slicePositions) {
                Tile tile = game.getTileByPosition(position);
                if (tile == null) {
                    continue;
                }
                String tileId = tile.getTileID();
                if (isNotASystem(tileId)) {
                    stats.skippedNonSystemTiles++;
                    continue;
                }

                stats.overallTiles
                        .computeIfAbsent(tileId, _ -> new WinRateCount())
                        .record(isWinner);
                if (isSpecialTile(tileId)) {
                    stats.specialTileIds.add(tileId);
                }
                for (String factionKey : factionKeys) {
                    stats.factionTiles
                            .computeIfAbsent(factionKey, _ -> new HashMap<>())
                            .computeIfAbsent(tileId, _ -> new WinRateCount())
                            .record(isWinner);
                }
            }
        }
    }

    private static String homePosition(Player player) {
        String position = player.getPlayerStatsAnchorPosition();
        if (isBlankish(position)) {
            position = player.getHomeSystemPosition();
        }
        return isBlankish(position) ? null : position;
    }

    private static boolean isBlankish(String value) {
        return StringUtils.isBlank(value) || "null".equalsIgnoreCase(value);
    }

    private static boolean isNotASystem(String tileId) {
        TileModel tileModel = TileHelper.getTileById(tileId);
        return tileModel == null || StringUtils.isBlank(tileModel.getNameNullSafe());
    }

    private static boolean hasStandardSixPlayerRingLayout(Game game) {
        Set<String> homes = new HashSet<>();
        for (Player player : game.getRealAndEliminatedPlayers()) {
            String home = homePosition(player);
            List<String> slicePositions = home == null ? null : SLICE_POSITIONS_BY_HOME.get(home);
            if (slicePositions == null || !homes.add(home)) {
                return false;
            }
            for (String position : slicePositions) {
                if (game.getTileByPosition(position) == null) {
                    return false;
                }
            }
        }
        return homes.size() == SLICE_POSITIONS_BY_HOME.size();
    }

    private static boolean isSpecialTile(String tileId) {
        return ENTROPIC_SCAR_TILE_ID.equals(tileId) || isIntrinsicallyLegendaryTile(tileId);
    }

    static boolean isIntrinsicallyLegendaryTile(String tileId) {
        List<PlanetModel> planets = TileHelper.getPlanetsByTileId(tileId);
        if (planets == null) {
            return false;
        }
        return planets.stream().anyMatch(planet -> {
            String abilityName = planet.getLegendaryAbilityName();
            return StringUtils.isNotBlank(abilityName);
        });
    }

    private static void appendOverallSection(StringBuilder sb, SliceTileWinRateStats stats) {
        sb.append("\n### Slice tile win rates, all factions\n");
        stats.overallTiles.entrySet().stream()
                .sorted(BY_WIN_RATE_DESC)
                .forEach(entry -> appendTileLine(sb, "- ", entry));
    }

    private static void appendBestAndWorstByFactionSection(StringBuilder sb, SliceTileWinRateStats stats) {
        sb.append("\n### Best and worst slice tiles by faction\n");
        stats.factionTiles.entrySet().stream().sorted(Entry.comparingByKey()).forEach(factionEntry -> {
            List<Entry<String, WinRateCount>> ranked = factionEntry.getValue().entrySet().stream()
                    .sorted(BY_WIN_RATE_DESC)
                    .toList();

            sb.append("- ")
                    .append(factionLabel(factionEntry.getKey()))
                    .append(" - overall ")
                    .append(stats.factionRecords.get(factionEntry.getKey()))
                    .append('\n');

            sb.append("  - Best:\n");
            ranked.stream().limit(TOP_BOTTOM_COUNT).forEach(entry -> appendTileLine(sb, "    - ", entry));

            // Take the tail without letting it overlap the best list.
            int worstFrom = Math.max(TOP_BOTTOM_COUNT, ranked.size() - TOP_BOTTOM_COUNT);
            if (worstFrom < ranked.size()) {
                sb.append("  - Worst:\n");
                List<Entry<String, WinRateCount>> worst = ranked.subList(worstFrom, ranked.size());
                worst.reversed().forEach(entry -> appendTileLine(sb, "    - ", entry));
            }
        });
    }

    private static void appendSpecialTileSection(StringBuilder sb, SliceTileWinRateStats stats) {
        sb.append("\n### Entropic Scar and Legendary tiles by faction\n");
        List<String> specialTileIds = stats.specialTileIds.stream()
                .sorted(Comparator.comparing(SliceTileWinRateStatisticsService::tileName))
                .toList();
        if (specialTileIds.isEmpty()) {
            sb.append("- No Entropic Scar or Legendary tile appeared in any slice.\n");
            return;
        }
        sb.append("_That faction's win rate when the tile was in their slice._\n");

        stats.factionTiles.entrySet().stream().sorted(Entry.comparingByKey()).forEach(factionEntry -> {
            List<String> present = specialTileIds.stream()
                    .filter(factionEntry.getValue()::containsKey)
                    .toList();
            if (present.isEmpty()) {
                return;
            }
            sb.append("- ")
                    .append(factionLabel(factionEntry.getKey()))
                    .append(" - overall ")
                    .append(stats.factionRecords.get(factionEntry.getKey()))
                    .append('\n');
            for (String tileId : present) {
                sb.append("  - ")
                        .append(tileLabel(tileId))
                        .append(": ")
                        .append(factionEntry.getValue().get(tileId))
                        .append('\n');
            }
        });
    }

    private static void appendTileLine(StringBuilder sb, String indent, Entry<String, WinRateCount> entry) {
        WinRateCount count = entry.getValue();
        sb.append(indent)
                .append('`')
                .append(StringUtils.leftPad(Long.toString(count.percent()), 3))
                .append("%` ")
                .append(count.wins)
                .append('/')
                .append(count.total)
                .append(' ')
                .append(tileLabel(entry.getKey()))
                .append('\n');
    }

    /**
     * Never null: this feeds a Comparator, and TileModel.getName() is null for placeholder art, which
     * would blow up String.compareTo mid-sort.
     */
    private static String tileName(String tileId) {
        TileModel tileModel = TileHelper.getTileById(tileId);
        String name = tileModel == null ? null : tileModel.getNameNullSafe();
        return StringUtils.isBlank(name) ? tileId : name;
    }

    private static String tileLabel(String tileId) {
        return "`" + tileId + "` " + tileName(tileId);
    }

    private static String factionLabel(String faction) {
        FactionModel factionModel = Mapper.getFaction(faction);
        String factionName = factionModel != null ? factionModel.getFactionNameWithSourceEmoji() : faction;
        return FactionStatisticsHelper.getFactionEmoji(faction) + " **" + factionName + "**";
    }

    // ---------------------------------------------------------------- accumulators

    private static class SliceTileWinRateStats {
        final Map<String, WinRateCount> overallTiles = new HashMap<>();

        final Map<String, Map<String, WinRateCount>> factionTiles = new HashMap<>();

        final Map<String, WinRateCount> factionRecords = new HashMap<>();

        /** Entropic Scar and Legendary tiles that actually turned up, so we only report real data. */
        final Set<String> specialTileIds = new HashSet<>();

        int sliceCount;
        int excludedGames;
        int skippedNonSystemTiles;
    }

    private static class WinRateCount {
        int wins;
        int total;

        void record(boolean isWinner) {
            total++;
            if (isWinner) {
                wins++;
            }
        }

        double winRate() {
            return total == 0 ? 0.0 : (double) wins / total;
        }

        long percent() {
            return total == 0 ? 0 : Math.round(wins * 100.0 / total);
        }

        @Override
        public String toString() {
            if (total == 0) {
                return "No data";
            }
            return wins + "/" + total + " (" + percent() + "%)";
        }
    }
}
