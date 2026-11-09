package ti4.image.tile;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.model.MapPositionMapper;
import ti4.service.fow.FogViewer;
import ti4.service.map.CustomHyperlaneService;
import ti4.service.map.HyperlaneBridgeEnds.BridgeEnd;
import ti4.testUtils.BaseTi4Test;

// System 19 in the centre; hyperlanes placed on its sides by direction (0 = north, clockwise).
class BridgeEndsLookupTest extends BaseTi4Test {

    private static final List<String> AROUND = MapPositionMapper.getAdjacentTilePositions("000");

    private Game game;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setTile(new Tile("19", "000"));
    }

    @Test
    void oneWayLaneInFromTheNorthAndOutToTheSouth() {
        customHyperlane(0, 0, 3);
        customHyperlane(3, 0, 3);
        assertThat(ends()).containsExactly(new BridgeEnd(0, true, false), new BridgeEnd(3, false, true));
    }

    // Bridges are the pass-through rule, which is for custom hyperlanes only: official tiles keep LRR 44.1.
    @Test
    void staticHyperlaneTilesDoNotCount() {
        customHyperlane(0, 0, 3);
        game.setTile(new Tile("83a", AROUND.get(1)));
        assertThat(ends()).isEmpty();
    }

    @Test
    void aSingleLaneEndMakesNoBridge() {
        customHyperlane(0, 0, 3);
        assertThat(ends()).isEmpty();
    }

    // An adjacency override on the hyperlane's shared edge sends its lane elsewhere.
    @Test
    void overriddenEdgeIsSkipped() {
        customHyperlane(0, 0, 3);
        customHyperlane(3, 0, 3);
        game.addAdjacentTileOverride(AROUND.get(0), 3, "201");
        assertThat(ends()).isEmpty();
    }

    @Test
    void hyperlaneSystemsGetNoBridge() {
        customHyperlane(0, 0, 3);
        customHyperlane(3, 0, 3);
        game.setTile(new Tile("83a", "000"));
        assertThat(ends()).isEmpty();
    }

    @Test
    void privateViewIgnoresHyperlanesInFog() {
        game.setFowMode(true);
        Player viewer = game.addPlayer("111", "sol");
        viewer.setFaction(game, "sol");
        viewer.setColor("blue");
        customHyperlane(0, 0, 3);
        customHyperlane(3, 0, 3);
        game.getTileByPosition(AROUND.get(0)).setTileFog(viewer, false);
        game.getTileByPosition(AROUND.get(3)).setTileFog(viewer, true);
        TileScene scene = new TileScene(game, null, new FogViewer(game, true, viewer), new RenderWarnings());
        assertThat(BridgeEndsLookup.forSystem(scene, game.getTileByPosition("000")))
                .isEmpty();
    }

    // Two lanes that only lead in carry no traffic, so the rules keep the system a normal stop: draw no bridge either.
    @Test
    void lanesThatOnlyLeadInMakeNoBridge() {
        customHyperlane(0, 0, 3);
        customHyperlane(3, 3, 0);
        assertThat(ends()).isEmpty();
    }

    // Border anomalies only count where a hyperlane starts or ends; a bridge carries the lane on, so a tear on its edge
    // changes nothing, for a hidden system (fog safe) as for a visible one.
    @Test
    void borderAnomalyOnABridgeEdgeDoesNotCutIt() {
        game.setFowMode(true);
        Player viewer = game.addPlayer("111", "sol");
        viewer.setFaction(game, "sol");
        viewer.setColor("blue");
        customHyperlane(0, 0, 3);
        customHyperlane(3, 0, 3);
        game.getTileByPosition(AROUND.get(0)).setTileFog(viewer, false);
        game.getTileByPosition(AROUND.get(3)).setTileFog(viewer, false);
        game.getTileByPosition("000").setTileFog(viewer, true);
        game.addBorderAnomaly("000", 3, "spatial_tear");
        TileScene scene = new TileScene(game, null, new FogViewer(game, true, viewer), new RenderWarnings());

        assertThat(BridgeEndsLookup.forSystem(scene, game.getTileByPosition("000")))
                .containsExactly(new BridgeEnd(0, true, false), new BridgeEnd(3, false, true));
        assertThat(ends()).containsExactly(new BridgeEnd(0, true, false), new BridgeEnd(3, false, true));
    }

    private List<BridgeEnd> ends() {
        TileScene scene = new TileScene(game, null, FogViewer.unfogged(game), new RenderWarnings());
        return BridgeEndsLookup.forSystem(scene, game.getTileByPosition("000"));
    }

    private void customHyperlane(int systemSide, int from, int to) {
        String position = AROUND.get(systemSide);
        int[][] lanes = new int[6][6];
        lanes[from][to] = 1;
        game.setTile(new Tile("hl", position));
        game.getCustomHyperlaneData().put(position, CustomHyperlaneService.formatMatrix(lanes));
    }
}
