package ti4.image.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.DisplayType;
import ti4.helpers.Units;
import ti4.image.map.CompactOverviewGenerator.Group;
import ti4.image.map.CompactOverviewGenerator.Layout;
import ti4.image.map.CompactOverviewGenerator.Options;
import ti4.image.map.CompactOverviewGenerator.Placed;
import ti4.image.map.CompactOverviewGenerator.Style;
import ti4.model.GalaxyNames;
import ti4.testUtils.BaseTi4Test;

class CompactOverviewGeneratorTest extends BaseTi4Test {

    private Game game;
    private Player player;
    private String main;
    private String galaxyA;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("compact-overview-test");
        game.setFowMode(true);
        player = game.addPlayer("overview-" + java.util.UUID.randomUUID(), "sol");
        player.setFaction("sol");
        player.setColor("blue");

        // Two far-apart sectors on the main map, plus map A with a sector and an unsectored rest.
        MapFrame.positionsWithin("000", 1).forEach(position -> game.setTile(new Tile("19", position)));
        MapFrame.positionsWithin("1237", 1).forEach(position -> game.setTile(new Tile("20", position)));
        MapFrame.positionsWithin("a000", 1).forEach(position -> game.setTile(new Tile("21", position)));
        game.setTile(new Tile("22", "a301"));
        game.setTile(new Tile("23", "tl"));
        MapSegment.put(game, new MapSegment("core", "000", 1));
        MapSegment.put(game, new MapSegment("south", "1237", 1));
        MapSegment.put(game, new MapSegment("outpost", "a000", 1));
        main = GalaxyNames.name(game, GalaxyNames.MAIN_ID);
        galaxyA = GalaxyNames.name(game, "a");
    }

    private void giveSolACarrierAtTheCore() {
        game.getTileByPosition("000")
                .addUnit("space", Units.getUnitKey(Units.UnitType.Carrier, player.getColorID()), 1);
    }

    private static Set<String> titles(List<Group> groups) {
        return groups.stream().map(Group::title).collect(Collectors.toSet());
    }

    private static Set<String> positions(List<Group> groups) {
        return groups.stream()
                .flatMap(group -> group.islands().stream())
                .flatMap(island -> island.keySet().stream())
                .collect(Collectors.toSet());
    }

    private static Group group(List<Group> groups, String title) {
        return groups.stream()
                .filter(group -> group.title().equals(title))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no group " + title + " in " + titles(groups)));
    }

    @Test
    void theGmSeesOneGroupPerSectorPlusTheLeftoversAndCorners() {
        List<Group> groups = CompactOverviewGenerator.gmGroups(game, Options.DEFAULT_REACH);

        assertEquals(
                Set.of(main + " / core", main + " / south", galaxyA + " / outpost", galaxyA, "corners"),
                titles(groups));
        assertEquals(new HashSet<>(game.getTileMap().keySet()), positions(groups), "every placed tile is shown");
    }

    @Test
    void groupsGetShortCodesFromTheirBoard() {
        Map<String, String> codes = new HashMap<>();
        CompactOverviewGenerator.gmGroups(game, Options.DEFAULT_REACH)
                .forEach(group -> codes.put(group.title(), group.code()));

        assertEquals("M1", codes.get(main + " / core"));
        assertEquals("M2", codes.get(main + " / south"));
        assertEquals("A1", codes.get(galaxyA + " / outpost"));
        // Whole boards and the corners get a bare letter.
        assertEquals("A", codes.get(galaxyA));
        assertEquals("X", codes.get("corners"));
    }

    @Test
    void aPlayerOnlyGetsGroupsAndHexesTheyKnow() {
        // The player knows the core and one hex of the outpost; south, map A's rest and the corner are unknown.
        Set<String> known = new HashSet<>(MapFrame.positionsWithin("000", 1));
        known.add("a000");
        giveSolACarrierAtTheCore();
        player.addFogTile("21", "a000", "Rnd 1");

        List<Group> groups = CompactOverviewGenerator.playerGroups(game, player, known, Options.DEFAULT_REACH);

        assertEquals(Set.of(main + " / core", galaxyA + " / outpost"), titles(groups));
        assertEquals(known, positions(groups), "no unknown hex of a known sector is drawn");
    }

    @Test
    void knownHexesOneEmptyHexApartStayOneIslandByDefault() {
        // 101 and 104 sit on opposite sides of 000, two steps apart.
        Set<String> known = Set.of("101", "104");
        giveSolACarrierAtTheCore();
        String core = main + " / core";

        Group joined = group(CompactOverviewGenerator.playerGroups(game, player, known, 2), core);
        Group touchingOnly = group(CompactOverviewGenerator.playerGroups(game, player, known, 1), core);
        Group whole = group(CompactOverviewGenerator.playerGroups(game, player, known, Integer.MAX_VALUE), core);

        assertEquals(1, joined.islands().size());
        assertEquals(1, whole.islands().size());
        assertEquals(2, touchingOnly.islands().size());
        // Split islands are centred on themselves, so neither keeps where it lies relative to the other.
        for (Map<String, Point> island : touchingOnly.islands()) {
            assertEquals(new Point(0, 0), island.values().iterator().next());
        }
    }

    @Test
    void theGmSeesFarApartPiecesThatKeepTheirRealDirection() {
        // Map A's rest becomes a301 and a310, on opposite sides of the outpost sector.
        game.setTile(new Tile("24", "a310"));

        Group rest = group(CompactOverviewGenerator.gmGroups(game, Options.DEFAULT_REACH), galaxyA);

        assertEquals(2, rest.islands().size());
        Point first = rest.islands().get(0).values().iterator().next();
        Point second = rest.islands().get(1).values().iterator().next();
        assertTrue(first.distance(second) > 4 * FocusIslands.COLUMN_STEP, "pieces keep their real distance");
    }

    @Test
    void optionsFollowTheViewersSeat() {
        assertEquals(Options.DEFAULT, Options.of(null));
        // Players start on boxes, one empty hex and codes; a GM without a seat keeps hexes and names.
        assertEquals(new Options(Style.BOXES, 2, true), Options.of(player));

        player.setFogOverviewBoxes(false);
        player.setFogOverviewReach(Options.UNLIMITED_REACH);
        player.setFogOverviewCodes(false);
        Options options = Options.of(player);

        assertEquals(Style.HEXES, options.style());
        assertTrue(options.unlimitedReach());
        assertFalse(options.codes());
        assertEquals(Options.MAX_REACH, new Options(Style.HEXES, 9, false).reach());
    }

    @ParameterizedTest
    @EnumSource(Style.class)
    void packedGroupsNeverOverlapAndFitTheSizeCap(Style style) {
        game.setTile(new Tile("24", "a310"));
        Layout layout = CompactOverviewGenerator.layout(
                CompactOverviewGenerator.gmGroups(game, 1), "header", new Options(style, 1, true));

        List<Placed> placed = layout.placed();
        List<Point> hexes =
                placed.stream().flatMap(box -> box.centres().values().stream()).toList();
        for (int i = 0; i < hexes.size(); i++) {
            for (int j = i + 1; j < hexes.size(); j++) {
                if (!adjacent(hexes.get(i), hexes.get(j))) {
                    assertTrue(HexPacker.hexesClear(hexes.get(i), hexes.get(j), HexPacker.GUTTER - 1), "hexes overlap");
                }
            }
        }
        for (int i = 0; i < placed.size(); i++) {
            for (int j = i + 1; j < placed.size(); j++) {
                assertFalse(placed.get(i).label().intersects(placed.get(j).label()), "labels overlap");
            }
        }
        assertTrue(layout.canvasWidth() <= CompactOverviewGenerator.MAX_SIZE);
        assertTrue(layout.canvasHeight() <= CompactOverviewGenerator.MAX_SIZE);
    }

    private static boolean adjacent(Point a, Point b) {
        return HexPacker.NEIGHBOUR_OFFSETS.contains(new Point(b.x - a.x, b.y - a.y));
    }

    @Test
    void aGroupsIslandsArePackedNextToEachOther() {
        // Touching only: the two leftover hexes of map A are two islands of one group.
        game.setTile(new Tile("24", "a310"));
        Layout layout = CompactOverviewGenerator.layout(
                CompactOverviewGenerator.gmGroups(game, 1), "header", new Options(Style.HEXES, 1, false));

        Placed rest = layout.placed().stream()
                .filter(box -> box.group().title().equals(galaxyA))
                .findFirst()
                .orElseThrow();
        assertEquals(2, rest.islands().size());
        Point first = rest.islands().get(0).values().iterator().next();
        Point second = rest.islands().get(1).values().iterator().next();
        assertTrue(
                first.distance(second) < 2 * FocusIslands.COLUMN_STEP + HexPacker.GUTTER,
                "islands of one group are packed together");
    }

    @Test
    void boxesHoldTheirOwnHexesAndLabelAndNeverTouch() {
        List<Placed> placed = CompactOverviewGenerator.layout(
                        CompactOverviewGenerator.gmGroups(game, Options.DEFAULT_REACH),
                        "header",
                        new Options(Style.BOXES, Options.DEFAULT_REACH, false))
                .placed();

        for (int i = 0; i < placed.size(); i++) {
            Rectangle frame = placed.get(i).frame();
            assertTrue(frame.contains(placed.get(i).label()), "label outside its box");
            placed.get(i)
                    .centres()
                    .values()
                    .forEach(centre ->
                            assertTrue(frame.contains(HexPacker.hexBox(centre)), "hex outside its box at " + centre));
            for (int j = i + 1; j < placed.size(); j++) {
                assertTrue(
                        HexPacker.rectanglesClear(frame, placed.get(j).frame(), HexPacker.GUTTER - 1), "boxes touch");
            }
        }
    }

    @Test
    void codesComeWithALegendBesideTheMap() {
        List<Group> groups = CompactOverviewGenerator.gmGroups(game, Options.DEFAULT_REACH);
        Layout withNames = CompactOverviewGenerator.layout(groups, "header", Options.DEFAULT);
        Layout withCodes = CompactOverviewGenerator.layout(
                groups, "header", new Options(Style.HEXES, Options.DEFAULT_REACH, true));

        assertNull(withNames.legend());
        OverviewLegend legend = withCodes.legend();
        assertNotNull(legend);
        assertEquals(groups.size(), legend.rows().size());
        for (Placed box : withCodes.placed()) {
            assertFalse(legend.bounds().intersects(box.label()), "legend covers a label");
            box.centres()
                    .values()
                    .forEach(centre ->
                            assertFalse(legend.bounds().intersects(HexPacker.hexBox(centre)), "legend covers a hex"));
        }
    }

    @Test
    void neighbouringHexesLandOnTheHexLattice() {
        Point origin = new Point(1000, 1000);
        Point nudged = new Point(1000 + FocusIslands.COLUMN_STEP + 3, 1000 + FocusIslands.HALF_ROW_STEP - 2);

        assertEquals(
                new Point(1000 + FocusIslands.COLUMN_STEP, 1000 + FocusIslands.HALF_ROW_STEP),
                CompactOverviewGenerator.onLattice(nudged, origin));
    }

    @Test
    void onlyFeatureOverlaysAreDrawnAsOverviews() {
        assertTrue(CompactOverviewGenerator.drawsOverlay(DisplayType.wormholes));
        assertTrue(CompactOverviewGenerator.drawsOverlay(DisplayType.spacecannon));
        // Map variants keep the normal full-size map.
        assertFalse(CompactOverviewGenerator.drawsOverlay(DisplayType.shipless));
        assertFalse(CompactOverviewGenerator.drawsOverlay(DisplayType.unlocked));
        assertFalse(CompactOverviewGenerator.drawsOverlay(DisplayType.map));
    }

    @Test
    void overlayOverviewsRenderForTheGmAndAPlayer() {
        giveSolACarrierAtTheCore();
        player.setFogOverviewCodes(true);

        assertNotNull(CompactOverviewGenerator.gmImage(game, DisplayType.spacecannon));
        assertNotNull(CompactOverviewGenerator.playerImage(game, player, DisplayType.wormholes));
    }

    @Test
    void aPlayerOverviewDrawsBordersOfSystemsTheySee() {
        giveSolACarrierAtTheCore();
        BufferedImage withoutBorder = CompactOverviewGenerator.playerImage(game, player);
        game.addBorderAnomaly("000", 0, "spatial_tear");

        BufferedImage withBorder = CompactOverviewGenerator.playerImage(game, player);
        assertFalse(Arrays.equals(pixels(withoutBorder), pixels(withBorder)), "the border is drawn");
    }

    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }
}
