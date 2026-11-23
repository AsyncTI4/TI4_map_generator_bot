package ti4.image.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

class FocusIslandsTest extends BaseTi4Test {

    private Game game;
    private Player viewer;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("focus-islands-test");
        MapFrame.positionsWithin("000", 4).forEach(position -> game.setTile(new Tile("19", position)));
        viewer = game.addPlayer("111", "sol");
        viewer.setFaction(game, "sol");
        viewer.setColor("blue");
        viewer.setUnitsOwned(new HashSet<>(Mapper.getFaction("sol").getUnits()));
    }

    @Test
    void neighbouringSystemsFormOneIslandWithoutBadgesAndOnlyTheirNotchIsFilled() {
        List<FocusIslands.Island> islands = FocusIslands.of(game, viewer, Set.of("101", "102", "103"));

        assertEquals(1, islands.size());
        // 000 sits in the bay the three form; 202 and 204 only touch two lit systems that touch each other.
        assertEquals(Set.of("000"), islands.getFirst().dimmed());
        assertEquals(
                Set.of("000", "101", "102", "103"), islands.getFirst().cells().keySet());
        assertTrue(islands.getFirst().badges().isEmpty());
    }

    @Test
    void aOneHexGapStaysOneIslandWithTheGapTileDimmed() {
        List<FocusIslands.Island> islands = FocusIslands.of(game, viewer, Set.of("201", "203"));

        assertEquals(1, islands.size());
        assertEquals(Set.of("202"), islands.getFirst().dimmed());
        assertTrue(islands.getFirst().isLit("201"));
    }

    @Test
    void aWormholeHopBecomesItsOwnIslandLinkedByBadges() {
        game.setTile(new Tile("26", "101"));
        game.setTile(new Tile("39", "401"));
        viewer.setHomeSystemPosition("101");

        List<FocusIslands.Island> islands = FocusIslands.of(game, viewer, Set.of("101", "102", "401"));

        assertEquals(2, islands.size());
        assertEquals("Home", islands.get(0).title());
        assertEquals("A - alpha", islands.get(1).title());
        // The wormhole art on both tiles already shows the link, so neither end gets a badge.
        assertTrue(islands.get(0).badges().isEmpty());
        assertTrue(islands.get(1).badges().isEmpty());
    }

    @Test
    void theHomeIslandIsFoundWithoutAStoredHomePosition() {
        game.setTile(new Tile("01", "102"));

        List<FocusIslands.Island> islands = FocusIslands.of(game, viewer, Set.of("102", "401"));

        assertEquals("Home", islands.getFirst().title());
        assertTrue(islands.getFirst().cells().containsKey("102"));
    }

    @Test
    void moreThanThreeBadgesCollapseIntoACount() {
        assertEquals(List.of("A", "B", "C"), FocusIslandsImage.badgeTexts(List.of("A", "B", "C")));
        assertEquals(List.of("A", "B", "+2"), FocusIslandsImage.badgeTexts(List.of("A", "B", "C", "D")));
    }

    @Test
    void aCornerTileIsItsOwnIsland() {
        game.setTile(new Tile("19", "tl"));

        List<FocusIslands.Island> islands = FocusIslands.of(game, viewer, Set.of("101", "tl"));

        assertEquals(2, islands.size());
        FocusIslands.Island corner = islands.stream()
                .filter(island -> island.cells().containsKey("tl"))
                .findFirst()
                .orElseThrow();
        assertTrue(corner.title().endsWith(" - corner"), corner.title());
    }

    @Test
    void farApartIslandsArePackedIntoASmallImage() {
        game.setTile(new Tile("26", "101"));
        game.setTile(new Tile("39", "401"));
        game.setTile(new Tile("19", "br"));

        BufferedImage image = FocusIslandsImage.image(game, viewer, Set.of("101", "401", "br"), 1);

        // Three single hexes: far less than the ring-4 span they sit across on the real map.
        assertTrue(image.getWidth() < 1600, "width " + image.getWidth());
        assertTrue(image.getHeight() < 1600, "height " + image.getHeight());
    }
}
