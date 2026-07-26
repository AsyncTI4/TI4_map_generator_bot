package ti4.service.statistics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.image.PositionMapper;
import ti4.image.TileHelper;
import ti4.testUtils.BaseTi4Test;

class SliceTileWinRateStatisticsServiceTest extends BaseTi4Test {

    private static final String MECATOL_POSITION = "000";

    /** Home position -> faction, in ring order. Order matters: it drives the tile ids placed below. */
    private static final List<Entry<String, String>> FACTION_BY_HOME = List.of(
            Map.entry("301", "sol"),
            Map.entry("304", "jolnar"),
            Map.entry("307", "hacan"),
            Map.entry("310", "letnev"),
            Map.entry("313", "xxcha"),
            Map.entry("316", "yssaril"));

    private static final int FIRST_TILE_ID = 19;
    private static final int TILES_PER_SLICE = 4;

    /** Mirrors SliceTileWinRateStatisticsService.MINIMUM_OCCURRENCES. */
    private static final int MINIMUM_OCCURRENCES = 10;

    /** The same board repeated, so per-tile counts clear the sample threshold. */
    private static List<Game> repeatGame(
            int count, String winningFaction, Map<String, String> tileOverridesByPosition) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> createStandardSliceGame(Integer.toString(i), winningFaction, tileOverridesByPosition))
                .toList();
    }

    /**
     * Twilight's Fall must be excluded explicitly: its setup clears the Thunder's Edge flag, but enabling TE
     * afterwards does not clear the TF flag, so one game can carry both and would slip past isThundersEdge().
     */
    @Test
    void onlyNonHomebrewThundersEdgeGamesWithoutTwilightsFallAreEligible() {
        Game thundersEdge = createStandardSliceGame("1", "sol", Map.of());
        thundersEdge.setThundersEdge(true);
        assertTrue(SliceTileWinRateStatisticsService.isEligibleGameType(thundersEdge), "plain Thunder's Edge");

        Game bothModes = createStandardSliceGame("2", "sol", Map.of());
        bothModes.setThundersEdge(true);
        bothModes.setTwilightsFallMode(true);
        assertFalse(
                SliceTileWinRateStatisticsService.isEligibleGameType(bothModes),
                "a game flagged both Thunder's Edge and Twilight's Fall must be excluded");

        Game twilightsFallOnly = createStandardSliceGame("3", "sol", Map.of());
        twilightsFallOnly.setTwilightsFallMode(true);
        assertFalse(SliceTileWinRateStatisticsService.isEligibleGameType(twilightsFallOnly), "Twilight's Fall only");

        Game notThundersEdge = createStandardSliceGame("4", "sol", Map.of());
        assertFalse(SliceTileWinRateStatisticsService.isEligibleGameType(notThundersEdge), "non-TE game");

        Game homebrew = createStandardSliceGame("5", "sol", Map.of());
        homebrew.setThundersEdge(true);
        homebrew.setAbsolMode(true);
        assertFalse(SliceTileWinRateStatisticsService.isEligibleGameType(homebrew), "homebrew Thunder's Edge game");
    }

    /**
     * The hard-coded slice table has to keep matching tileAdjacencies.properties, which is the real source
     * of truth for the board geometry.
     */
    @Test
    void sliceTableMatchesAdjacencyData() {
        Set<String> allSliceTiles = new HashSet<>();
        Set<String> homes = SliceTileWinRateStatisticsService.SLICE_POSITIONS_BY_HOME.keySet();
        assertEquals(6, homes.size());

        for (Entry<String, List<String>> entry : SliceTileWinRateStatisticsService.SLICE_POSITIONS_BY_HOME.entrySet()) {
            String home = entry.getKey();
            List<String> slice = entry.getValue();
            assertEquals(4, slice.size(), "slice size for home " + home);

            // The first three entries are exactly the on-board neighbours of the home system.
            assertEquals(Set.copyOf(slice.subList(0, 3)), onBoardNeighbours(home), "neighbours of home " + home);

            // The fourth is the ring-1 system reached through the ring-2 entry, and it touches 000.
            String ringTwoPosition = slice.get(2);
            String ringOnePosition = slice.get(3);
            assertTrue(
                    onBoardNeighbours(ringTwoPosition).contains(ringOnePosition),
                    ringOnePosition + " should be adjacent to " + ringTwoPosition);
            assertTrue(
                    PositionMapper.getAdjacentTilePositions(MECATOL_POSITION).contains(ringOnePosition),
                    ringOnePosition + " should be adjacent to " + MECATOL_POSITION);

            slice.forEach(
                    position -> assertTrue(allSliceTiles.add(position), position + " appears in more than one slice"));
            assertFalse(slice.contains(home), "a slice should not contain its own home");
        }

        assertEquals(24, allSliceTiles.size());
        assertTrue(homes.stream().noneMatch(allSliceTiles::contains), "no home should also be a slice tile");
    }

    @Test
    void buildReportRanksTilesAndBreaksDownByFaction() {
        String report = SliceTileWinRateStatisticsService.buildReport(repeatGame(MINIMUM_OCCURRENCES, "sol", Map.of()));

        assertTrue(report.contains("Games analyzed: 10"), report);
        assertTrue(report.contains("Slices analyzed: 60"), report);
        assertTrue(report.contains("Skipped for non-standard layout: 0"), report);

        // Sol's four slice tiles were in the winner's slice every game; Letnev's never were.
        sliceTileIdsFor("301")
                .forEach(tileId -> assertTrue(
                        report.contains("`100%` (10/10) " + tileId + " ("), "expected 100% for tile " + tileId));
        sliceTileIdsFor("310")
                .forEach(tileId -> assertTrue(
                        report.contains("`  0%` (0/10) " + tileId + " ("), "expected 0% for tile " + tileId));

        assertTrue(report.contains("Best and worst slice tiles by faction"), report);
        assertTrue(report.contains("Entropic Scar and Legendary tiles by faction"), report);
        assertTrue(
                report.contains("No Entropic Scar or Legendary tile appeared in any slice."),
                "no special tiles were placed in these games");
    }

    @Test
    void buildReportBreaksOutEntropicScarAndLegendaryTilesPerFaction() {
        // Entropic Scar and Primor sit in Sol's slice every game, and Sol never wins.
        List<Game> games = repeatGame(MINIMUM_OCCURRENCES, "letnev", Map.of("318", "114", "302", "65"));

        String report = SliceTileWinRateStatisticsService.buildReport(games);

        assertTrue(report.contains("`  0%` (0/10) 114 (" + tileName("114") + ")"), "Entropic Scar line for Sol");
        assertTrue(report.contains("`  0%` (0/10) 65 (" + tileName("65") + ")"), "Primor line for Sol");
        assertFalse(report.contains("without"), "the without metric was dropped");
    }

    /** A tile has to clear MINIMUM_OCCURRENCES in a bucket before it earns a row. */
    @Test
    void buildReportSuppressesTilesBelowTheSampleThreshold() {
        String justUnder =
                SliceTileWinRateStatisticsService.buildReport(repeatGame(MINIMUM_OCCURRENCES - 1, "sol", Map.of()));
        assertTrue(justUnder.contains("Games analyzed: 9"), justUnder);
        assertTrue(justUnder.contains("No tile appeared in at least 10 slices."), justUnder);
        sliceTileIdsFor("301")
                .forEach(tileId ->
                        assertFalse(justUnder.contains(tileId + " ("), "tile " + tileId + " is under-sampled"));
        assertTrue(
                justUnder.contains("No faction held an Entropic Scar or Legendary tile in at least 10 slices.")
                        || justUnder.contains("No Entropic Scar or Legendary tile appeared in any slice."),
                justUnder);

        String atThreshold =
                SliceTileWinRateStatisticsService.buildReport(repeatGame(MINIMUM_OCCURRENCES, "sol", Map.of()));
        assertTrue(atThreshold.contains("`100%` (10/10) 19 ("), "exactly 10 occurrences should qualify");
    }

    /** Special-tile rows are held to the same threshold as everything else. */
    @Test
    void buildReportSuppressesUnderSampledSpecialTiles() {
        List<Game> games = repeatGame(MINIMUM_OCCURRENCES - 1, "letnev", Map.of("318", "114", "302", "65"));

        String report = SliceTileWinRateStatisticsService.buildReport(games);

        assertFalse(report.contains("114 (" + tileName("114") + ")"), "under-sampled Entropic Scar row");
        assertFalse(report.contains("65 (" + tileName("65") + ")"), "under-sampled Primor row");
        assertTrue(
                report.contains("No faction held an Entropic Scar or Legendary tile in at least 10 slices."), report);
    }
    /**
     * TileModel.getName() is null for placeholder art (0g, 0b, 0r, -1, fog covers), which used to reach the
     * sort comparator and throw NPE out of String.compareTo.
     */
    @Test
    void buildReportIgnoresBlankAndPlaceholderTilesInSlices() {
        List<Game> games = repeatGame(MINIMUM_OCCURRENCES, "sol", Map.of("318", "0g", "302", "-1", "201", "0border"));

        String report = SliceTileWinRateStatisticsService.buildReport(games);

        assertTrue(report.contains("Games analyzed: 10"), report);
        assertTrue(
                report.contains("Ignored 30 slice position(s) holding a blank or placeholder tile"),
                "three placeholders per game across ten games");
        assertFalse(report.contains("0g ("), "placeholder tiles must not get a win rate row");
        assertFalse(report.contains("-1 ("), "placeholder tiles must not get a win rate row");
        assertFalse(report.contains("0border ("), "placeholder tiles must not get a win rate row");
        // Sol's one remaining real slice tile is still counted.
        assertTrue(report.contains("`100%` (10/10) 22 ("), report);
    }

    @Test
    void buildReportSkipsGamesWhoseHomesAreNotOnTheRingCorners() {
        Game game = createStandardSliceGame("1", "sol", Map.of());
        // Nudge one home off its corner; the hard-coded slices no longer describe this board.
        game.getPlayerFromColorOrFaction("sol").setPlayerStatsAnchorPosition("302");

        String report = SliceTileWinRateStatisticsService.buildReport(List.of(game));

        assertEquals(
                "No standard 6-player competitive games were available for slice analysis."
                        + " (1 skipped for a non-standard map layout.)",
                report);
    }

    @Test
    void legendaryDetectionUsesTheTilesOwnPlanets() {
        assertTrue(SliceTileWinRateStatisticsService.isIntrinsicallyLegendaryTile("65"), "Primor");
        assertTrue(SliceTileWinRateStatisticsService.isIntrinsicallyLegendaryTile("66"), "Hope's End");
        assertFalse(SliceTileWinRateStatisticsService.isIntrinsicallyLegendaryTile("19"), "Wellon is not legendary");
        assertFalse(
                SliceTileWinRateStatisticsService.isIntrinsicallyLegendaryTile("114"),
                "Entropic Scar has no planets, so it is not legendary - it is reported on its own");
        assertFalse(SliceTileWinRateStatisticsService.isIntrinsicallyLegendaryTile("not-a-tile"), "unknown tile");
    }

    private static Set<String> onBoardNeighbours(String position) {
        return PositionMapper.getAdjacentTilePositions(position).stream()
                .filter(adjacent -> !"x".equals(adjacent))
                .filter(adjacent -> {
                    int ring = Integer.parseInt(adjacent) / 100;
                    return ring >= 1 && ring <= 3;
                })
                .collect(Collectors.toSet());
    }

    /**
     * A three-ring, six-player board where every slice position holds a distinct system tile. Tile ids run
     * 19..42 across the six slices, so each slice's tiles are identifiable in the report.
     */
    private static Game createStandardSliceGame(
            String suffix, String winningFaction, Map<String, String> tileOverridesByPosition) {
        Game game = new Game();
        game.setName("slice-stats-" + suffix);
        game.setVp(1);
        game.setRound(3);
        game.setHasEnded(true);

        int nextTileId = FIRST_TILE_ID;
        for (Entry<String, String> homeEntry : FACTION_BY_HOME) {
            String home = homeEntry.getKey();
            String faction = homeEntry.getValue();

            Player player = game.addPlayer(faction + "-user-" + suffix, faction + " " + suffix);
            player.setFaction(faction);
            player.setColor(colorFor(faction));
            player.setPlayerStatsAnchorPosition(home);
            if (faction.equals(winningFaction)) {
                player.setSecretScored("so_" + faction + "_" + suffix);
            }

            for (String position : SliceTileWinRateStatisticsService.SLICE_POSITIONS_BY_HOME.get(home)) {
                String tileId = tileOverridesByPosition.getOrDefault(position, Integer.toString(nextTileId));
                game.setTile(new Tile(tileId, position));
                nextTileId++;
            }
        }
        return game;
    }

    /** The tile ids {@link #createStandardSliceGame} places in the slice of the given home. */
    private static List<String> sliceTileIdsFor(String home) {
        int start = FIRST_TILE_ID;
        for (Entry<String, String> entry : FACTION_BY_HOME) {
            if (entry.getKey().equals(home)) {
                break;
            }
            start += TILES_PER_SLICE;
        }
        int firstTileId = start;
        return IntStream.range(0, TILES_PER_SLICE)
                .mapToObj(offset -> Integer.toString(firstTileId + offset))
                .toList();
    }

    private static String colorFor(String faction) {
        return switch (faction) {
            case "sol" -> "red";
            case "jolnar" -> "blue";
            case "hacan" -> "green";
            case "letnev" -> "black";
            case "xxcha" -> "yellow";
            default -> "purple";
        };
    }

    private static String tileName(String tileId) {
        return TileHelper.getTileById(tileId).getName();
    }
}
