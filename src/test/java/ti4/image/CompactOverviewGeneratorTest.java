package ti4.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Rectangle;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.image.CompactOverviewGenerator.Layout;
import ti4.image.CompactOverviewGenerator.Panel;
import ti4.image.CompactOverviewGenerator.Placed;
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
        return panels.stream().flatMap(panel -> panel.hexes().keySet().stream()).collect(Collectors.toSet());
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

    @Test
    void packedPanelsNeverOverlapAndFitTheSizeCap() {
        Layout layout = CompactOverviewGenerator.layout(
                CompactOverviewGenerator.gmPanels(game), CompactOverviewGenerator.PLAYER_MAX_SIZE);

        List<Placed> placed = layout.placed();
        for (int i = 0; i < placed.size(); i++) {
            for (int j = i + 1; j < placed.size(); j++) {
                assertFalse(box(placed.get(i)).intersects(box(placed.get(j))), "panels overlap");
            }
        }
        assertTrue(layout.canvasWidth() <= CompactOverviewGenerator.PLAYER_MAX_SIZE);
        assertTrue(layout.canvasHeight() <= CompactOverviewGenerator.PLAYER_MAX_SIZE);
    }

    @Test
    void packingRemovesTheEmptySpaceBetweenFarApartSectors() {
        // core (around 000) and south (around 1237) are ~7000px apart on the real map.
        List<Panel> twoSectors = CompactOverviewGenerator.gmPanels(game).stream()
                .filter(panel -> panel.title().endsWith("core") || panel.title().endsWith("south"))
                .toList();

        Layout layout = CompactOverviewGenerator.layout(twoSectors, CompactOverviewGenerator.GM_MAX_SIZE);

        assertEquals(1.0, layout.scale(), "two small sectors fit at full size once packed");
        assertTrue(layout.width() < 3000 && layout.height() < 3000);
    }

    @Test
    void theGmOverviewRenders() {
        assertNotNull(CompactOverviewGenerator.gmOverview(game));
    }

    private static Rectangle box(Placed placed) {
        return new Rectangle(placed.x(), placed.y(), placed.width(), placed.height());
    }

    @Test
    void theCornersPanelLeavesRoomForOversizedCornerArt() {
        Panel corners = CompactOverviewGenerator.gmPanels(game).stream()
                .filter(panel -> "corners".equals(panel.title()))
                .findFirst()
                .orElseThrow();
        Rectangle hex = corners.hexes().get("tl");

        // Corner tiles can draw art over the whole 600px tile image, i.e. 100px beyond the hex on every side.
        assertTrue(
                corners.content().contains(new Rectangle(hex.x - 100, hex.y - 100, hex.width + 200, hex.height + 200)));
    }
}
