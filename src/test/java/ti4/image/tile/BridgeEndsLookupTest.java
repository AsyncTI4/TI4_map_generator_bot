package ti4.image.tile;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.service.fow.FogViewer;
import ti4.service.map.BridgeEnd;
import ti4.service.map.CustomHyperlaneService;
import ti4.service.map.HyperlaneBridges;
import ti4.service.option.FOWOptionService.FOWOption;
import ti4.testUtils.BaseTi4Test;

// System 19 in the centre bridged N -> S; neighbours placed by side (0 = north, clockwise).
class BridgeEndsLookupTest extends BaseTi4Test {

    private static final List<String> AROUND = MapPositionMapper.getAdjacentTilePositions("000");
    private static final String NORTH_TO_SOUTH =
            "0,0,0,2,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0";

    private Game game;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setTile(new Tile("19", "000"));
        HyperlaneBridges.set(game, "000", NORTH_TO_SOUTH);
    }

    @Test
    void theMatrixIsDrawnInFullForAnUnfoggedView() {
        assertThat(BridgeEndsLookup.drawnMatrix(unfogged(), system()).ends())
                .containsExactly(new BridgeEnd(0, true, false), new BridgeEnd(3, false, true));
    }

    @Test
    void noMatrixDrawsNothing() {
        HyperlaneBridges.remove(game, "000");
        assertThat(BridgeEndsLookup.drawnMatrix(unfogged(), system()).isEmpty()).isTrue();
    }

    // A fogged system shows only lanes whose both ends the viewer can see.
    @Test
    void foggedSystemShowsOnlyLanesBetweenVisibleNeighbours() {
        for (String position : AROUND) game.setTile(new Tile("20", position));
        Player viewer = fogViewer();
        system().setTileFog(viewer, true);
        game.getTileByPosition(AROUND.get(0)).setTileFog(viewer, false);
        game.getTileByPosition(AROUND.get(3)).setTileFog(viewer, true);
        assertThat(BridgeEndsLookup.drawnMatrix(privateScene(viewer), system()).isEmpty())
                .isTrue();

        game.getTileByPosition(AROUND.get(3)).setTileFog(viewer, false);
        assertThat(BridgeEndsLookup.drawnMatrix(privateScene(viewer), system()).isEmpty())
                .isFalse();
    }

    // Hide unseen hyperlanes: no vision runs along the bridge lane, but a player with ships in the bridged system
    // still sees that system's own lanes. Under FoW+ only lanes whose far tiles they see stay drawn.
    @Test
    void aPlayerInsideTheBridgedSystemSeesItsLanes() {
        for (String position : AROUND) game.setTile(new Tile("20", position));
        Player viewer = fogViewer();
        game.setFowOption(FOWOption.HIDE_UNSEEN_HYPERLANES, true);
        for (Tile tile : game.getTileMap().values()) tile.setTileFog(viewer, false);
        assertThat(BridgeEndsLookup.drawnMatrix(privateScene(viewer), system()).isEmpty())
                .isTrue();

        system().addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Destroyer, viewer.getColorID()), 1);
        assertThat(BridgeEndsLookup.drawnMatrix(privateScene(viewer), system()).isEmpty())
                .isFalse();

        game.setFowOption(FOWOption.FOW_PLUS, true);
        game.getTileByPosition(AROUND.get(3)).setTileFog(viewer, true);
        assertThat(BridgeEndsLookup.drawnMatrix(privateScene(viewer), system()).isEmpty())
                .isTrue();
    }

    // Tunnels go toward neighbouring systems on sides without lanes; a lane ending in the system on such a side gets
    // the glow; lane sides, hyperlanes and border anomalies get neither.
    @Test
    void tunnelsAndLaneEnds() {
        game.setTile(new Tile("20", AROUND.get(1)));
        game.setTile(new Tile("21", AROUND.get(2)));
        game.setTile(new Tile("22", AROUND.get(3)));
        customHyperlane(5, 5, 2);
        game.addBorderAnomaly("000", 2, "spatial_tear");
        HyperlaneBridgeOverlay.Marks marks = BridgeEndsLookup.marks(unfogged(), system());
        assertThat(marks.tunnels()).containsExactly(1);
        assertThat(marks.laneEnds()).containsExactly(5);
    }

    private Tile system() {
        return game.getTileByPosition("000");
    }

    private Player fogViewer() {
        game.setFowMode(true);
        Player viewer = game.addPlayer("111", "sol");
        viewer.setFaction(game, "sol");
        viewer.setColor("blue");
        return viewer;
    }

    private TileScene unfogged() {
        return new TileScene(game, null, FogViewer.unfogged(game), new RenderWarnings());
    }

    private TileScene privateScene(Player viewer) {
        return new TileScene(game, null, new FogViewer(game, true, viewer), new RenderWarnings());
    }

    private void customHyperlane(int systemSide, int from, int to) {
        String position = AROUND.get(systemSide);
        int[][] lanes = new int[6][6];
        lanes[from][to] = 1;
        game.setTile(new Tile("hl", position));
        game.getCustomHyperlaneData().put(position, CustomHyperlaneService.formatMatrix(lanes));
    }
}
