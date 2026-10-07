package ti4.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.image.SectorConnections.Connection;
import ti4.testUtils.BaseTi4Test;

class SectorConnectionsTest extends BaseTi4Test {

    private static final String ALPHA_WORMHOLE = "39";
    private static final String EMPTY = "19";

    private Game game;
    private Player player;
    private MapSegment home;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("sector-connections-test");
        game.setFowMode(true);
        player = game.addPlayer("player-id", "sol");
        player.setFaction("sol");
        player.setColor("blue");

        // Home sector: an alpha wormhole at 000 with empty neighbours. Two far sectors each hold an alpha too.
        MapFrame.positionsWithin("000", 1).forEach(position -> game.setTile(new Tile(EMPTY, position)));
        game.setTile(new Tile(ALPHA_WORMHOLE, "000"));
        game.setTile(new Tile(ALPHA_WORMHOLE, "1237"));
        game.setTile(new Tile(ALPHA_WORMHOLE, "1201"));
        home = new MapSegment("home", "000", 1);
        MapSegment.put(game, home);
        MapSegment.put(game, new MapSegment("north", "1201", 0));
        MapSegment.put(game, new MapSegment("south", "1237", 0));
    }

    @Test
    void wormholePartnersInOtherSectorsAreListedInPositionOrder() {
        List<Connection> connections =
                SectorConnections.find(game, player, home, Set.of("000"), Set.of("000", "101", "1201", "1237"));

        assertEquals(List.of(new Connection("1201", "north"), new Connection("1237", "south")), connections);
    }

    @Test
    void partnersThePlayerHasNeverSeenAreHidden() {
        List<Connection> connections =
                SectorConnections.find(game, player, home, Set.of("000"), Set.of("000", "101", "1237"));

        assertEquals(List.of(new Connection("1237", "south")), connections);
    }

    @Test
    void neighboursInsideTheShownSectorAreNotConnections() {
        // 000's hex neighbours (101..106) are adjacent but share the home sector.
        List<Connection> connections =
                SectorConnections.find(game, player, home, Set.of("000"), MapFrame.positionsWithin("000", 1));

        assertTrue(connections.isEmpty());
    }

    @Test
    void anExtraMapConnectsToSectorsOnTheMainMap() {
        // Map A has no sectors of its own, so it is the detached "board-a" view; the main map keeps its sectors.
        game.setTile(new Tile(ALPHA_WORMHOLE, "a000"));
        MapSegment board = MapSegment.find(game, "board-a").orElseThrow();

        List<Connection> fromBoard =
                SectorConnections.find(game, player, board, Set.of("a000"), Set.of("a000", "000", "1201", "1237"));

        // With a second galaxy in play, main-map sectors carry the main galaxy's name.
        String main = GalaxyNames.name(game, GalaxyNames.MAIN_ID);
        assertEquals(
                List.of(
                        new Connection("000", main + " / home"),
                        new Connection("1201", main + " / north"),
                        new Connection("1237", main + " / south")),
                fromBoard);
    }

    @Test
    void mainMapSystemsOutsideEverySectorAreLabelledMain() {
        game.setTile(new Tile(ALPHA_WORMHOLE, "301"));
        game.setTile(new Tile(ALPHA_WORMHOLE, "a000"));
        MapSegment board = MapSegment.find(game, "board-a").orElseThrow();

        List<Connection> fromBoard = SectorConnections.find(game, player, board, Set.of("a000"), Set.of("a000", "301"));
        assertEquals(List.of(new Connection("301", MapSegment.mainDisplayName(game))), fromBoard);

        List<Connection> fromMain = SectorConnections.find(game, player, null, Set.of("301"), Set.of("301", "a000"));
        assertTrue(fromMain.contains(new Connection("a000", GalaxyNames.name(game, "a"))));
    }

    @Test
    void onlyCurrentlyVisibleSourcesInTheShownSectorCount() {
        // 1201 is known and visible, but it is not in the shown sector, so it cannot be a source.
        List<Connection> connections =
                SectorConnections.find(game, player, home, Set.of("1201"), Set.of("000", "1201", "1237"));

        assertTrue(connections.isEmpty());
    }
}
