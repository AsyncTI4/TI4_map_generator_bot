package ti4.image.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
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
import ti4.image.map.CompactOverviewGenerator.Layout;
import ti4.image.map.CompactOverviewGenerator.Panel;
import ti4.image.map.CompactOverviewGenerator.Placed;
import ti4.model.GalaxyNames;
import ti4.testUtils.BaseTi4Test;

class CompactOverviewGeneratorTest extends BaseTi4Test {

    private Game game;
    private Player player;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("compact-overview-test");
        game.setFowMode(true);
        player = game.addPlayer("player-id", "sol");
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
    }

    private static Set<String> titles(List<Panel> panels) {
        return panels.stream().map(Panel::title).collect(Collectors.toSet());
    }

    private static Set<String> positions(List<Panel> panels) {
        return panels.stream()
                .flatMap(panel -> panel.centres().keySet().stream())
                .collect(Collectors.toSet());
    }

    @Test
    void theGmSeesOnePanelPerSectorPlusTheLeftoversAndCorners() {
        List<Panel> panels = CompactOverviewGenerator.gmPanels(game);
        String galaxyA = GalaxyNames.name(game, "a");
        String main = GalaxyNames.name(game, GalaxyNames.MAIN_ID);

        assertEquals(
                Set.of(main + " / core", main + " / south", galaxyA + " / outpost", galaxyA, "corners"),
                titles(panels));
        assertEquals(new HashSet<>(game.getTileMap().keySet()), positions(panels), "every placed tile is shown");
    }

    @Test
    void aPlayerOnlyGetsPanelsAndHexesTheyKnow() {
        // The player knows the core and one hex of the outpost; south, map A's rest and the corner are unknown.
        Set<String> known = new HashSet<>(MapFrame.positionsWithin("000", 1));
        known.add("a000");
        game.getTileByPosition("000")
                .addUnit(
                        "space",
                        ti4.helpers.Units.getUnitKey(ti4.helpers.Units.UnitType.Carrier, player.getColorID()),
                        1);
        player.addFogTile("21", "a000", "Rnd 1");

        List<Panel> panels = CompactOverviewGenerator.playerPanels(game, player, known);

        String main = GalaxyNames.name(game, GalaxyNames.MAIN_ID);
        String galaxyA = GalaxyNames.name(game, "a");
        assertEquals(Set.of(main + " / core", galaxyA + " / outpost"), titles(panels));
        assertEquals(known, positions(panels), "no unknown hex of a known sector is drawn");
        assertFalse(positions(panels).contains("a101"));
    }

    @ParameterizedTest
    @EnumSource(CompactOverviewGenerator.Style.class)
    void packedPanelsNeverOverlapAndFitTheSizeCap(CompactOverviewGenerator.Style style) {
        Layout layout = CompactOverviewGenerator.layout(CompactOverviewGenerator.gmPanels(game), "header", style);

        List<Placed> placed = layout.placed();
        for (int i = 0; i < placed.size(); i++) {
            for (int j = i + 1; j < placed.size(); j++) {
                for (Point p : placed.get(i).centres().values()) {
                    for (Point q : placed.get(j).centres().values()) {
                        assertTrue(HexPacker.hexesClear(p, q, HexPacker.GUTTER - 1), "panels overlap");
                    }
                }
                assertFalse(placed.get(i).label().intersects(placed.get(j).label()), "labels overlap");
            }
        }
        assertTrue(layout.canvasWidth() <= CompactOverviewGenerator.MAX_SIZE);
        assertTrue(layout.canvasHeight() <= CompactOverviewGenerator.MAX_SIZE);
    }

    @Test
    void boxesHoldTheirOwnHexesAndLabelAndNeverTouch() {
        List<Placed> placed = CompactOverviewGenerator.layout(
                        CompactOverviewGenerator.gmPanels(game), "header", CompactOverviewGenerator.Style.BOXES)
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
    void theOverviewLayoutFollowsTheViewersSeat() {
        assertEquals(CompactOverviewGenerator.Style.HEXES, CompactOverviewGenerator.Style.of(player));
        assertEquals(CompactOverviewGenerator.Style.HEXES, CompactOverviewGenerator.Style.of(null));
        player.setFogOverviewBoxes(true);
        assertEquals(CompactOverviewGenerator.Style.BOXES, CompactOverviewGenerator.Style.of(player));
    }

    @Test
    void aPlayerSeesSeparatePiecesOfASectorAsLooseIslandsButTheGmSeesOnePanel() {
        // Two known hexes of the core that do not touch: 101 and 104 sit on opposite sides of 000.
        Set<String> known = Set.of("101", "104");
        game.getTileByPosition("000")
                .addUnit(
                        "space",
                        ti4.helpers.Units.getUnitKey(ti4.helpers.Units.UnitType.Carrier, player.getColorID()),
                        1);
        String core = GalaxyNames.name(game, GalaxyNames.MAIN_ID) + " / core";

        List<Panel> panels = CompactOverviewGenerator.playerPanels(game, player, known);

        assertEquals(Set.of(core + " - 1", core + " - 2"), titles(panels));
        // Each island is centred on itself, so neither keeps where it lies relative to the other.
        for (Panel panel : panels) {
            assertEquals(new Point(0, 0), panel.centres().values().iterator().next());
        }
        assertTrue(titles(CompactOverviewGenerator.gmPanels(game)).contains(core));
    }

    @Test
    void theGmSeesADisconnectedPanelAsPartsThatKeepTheirRealDirection() {
        // Map A's rest becomes a301 and a310, on opposite sides of the outpost sector.
        game.setTile(new Tile("24", "a310"));
        String galaxyA = GalaxyNames.name(game, "a");

        List<Panel> panels = CompactOverviewGenerator.gmPanels(game);

        Point first = onlyCentre(panels, galaxyA + " - 1");
        Point second = onlyCentre(panels, galaxyA + " - 2");
        assertFalse(titles(panels).contains(galaxyA));
        assertTrue(first.distance(second) > 4 * FocusIslands.COLUMN_STEP, "parts keep their real distance");
    }

    private static Point onlyCentre(List<Panel> panels, String title) {
        return panels.stream()
                .filter(panel -> panel.title().equals(title))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no panel " + title + " in " + titles(panels)))
                .centres()
                .values()
                .iterator()
                .next();
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
        game.getTileByPosition("000")
                .addUnit(
                        "space",
                        ti4.helpers.Units.getUnitKey(ti4.helpers.Units.UnitType.Carrier, player.getColorID()),
                        1);

        assertNotNull(CompactOverviewGenerator.gmImage(game, DisplayType.spacecannon));
        assertNotNull(CompactOverviewGenerator.playerImage(game, player, DisplayType.wormholes));
    }

    @Test
    void aPlayerOverviewDrawsBordersOfSystemsTheySee() {
        game.getTileByPosition("000")
                .addUnit(
                        "space",
                        ti4.helpers.Units.getUnitKey(ti4.helpers.Units.UnitType.Carrier, player.getColorID()),
                        1);
        BufferedImage withoutBorder = CompactOverviewGenerator.playerImage(game, player);
        game.addBorderAnomaly("000", 0, "spatial_tear");

        BufferedImage withBorder = CompactOverviewGenerator.playerImage(game, player);
        assertFalse(Arrays.equals(pixels(withoutBorder), pixels(withBorder)), "the border is drawn");
    }

    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }
}
