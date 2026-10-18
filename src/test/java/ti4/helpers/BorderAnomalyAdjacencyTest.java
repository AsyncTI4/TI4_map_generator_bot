package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.persistence.TestGameHarness;
import ti4.image.PositionMapper;
import ti4.testUtils.BaseTi4Test;

class BorderAnomalyAdjacencyTest extends BaseTi4Test {

    // Holders live on one side of an edge: blocksOut stops the walk from the holder tile across the edge,
    // blocksIn stops the walk from the neighbour into the holder tile.
    private static final String HOLDER_TILE = "101";

    @Test
    void noAnomalyKeepsBothSidesAdjacent() {
        try (var harness = TestGameHarness.fromSourceGame("game-with-border-anomalies")) {
            Game game = harness.load();
            Edge edge = findEdge(game);
            game.setBorderAnomalies(new ArrayList<>());

            assertThat(FoWHelper.traverseAdjacencies(game, false, HOLDER_TILE)).contains(edge.neighbour);
            assertThat(FoWHelper.traverseAdjacencies(game, false, edge.neighbour))
                    .contains(HOLDER_TILE);
        }
    }

    @Test
    void spatialTearBlocksBothDirections() {
        assertAdjacency("spatial_tear", false, false);
    }

    @Test
    void gravityWaveBlocksOnlyEnteringTheHolderTile() {
        assertAdjacency("gravity_wave", true, false);
    }

    @Test
    void nebulaBorderBlocksNothing() {
        assertAdjacency("nebula", true, true);
    }

    // The "not adjacent for other players" rule is not automated yet; a tether must stay neutral until it is.
    @Test
    void voidTetherBlocksNothing() {
        assertAdjacency("void_tether", true, true);
    }

    @Test
    void legacyUpperCaseTypeStillBlocks() {
        assertAdjacency("SPATIAL_TEAR", false, false);
    }

    private static void assertAdjacency(String type, boolean holderReachesNeighbour, boolean neighbourReachesHolder) {
        try (var harness = TestGameHarness.fromSourceGame("game-with-border-anomalies")) {
            Game game = harness.load();
            Edge edge = findEdge(game);
            game.setBorderAnomalies(new ArrayList<>());
            game.addBorderAnomaly(HOLDER_TILE, edge.direction, type);

            assertThat(FoWHelper.traverseAdjacencies(game, false, HOLDER_TILE).contains(edge.neighbour))
                    .as(type + ": holder tile reaches neighbour")
                    .isEqualTo(holderReachesNeighbour);
            assertThat(FoWHelper.traverseAdjacencies(game, false, edge.neighbour)
                            .contains(HOLDER_TILE))
                    .as(type + ": neighbour reaches holder tile")
                    .isEqualTo(neighbourReachesHolder);
        }
    }

    private static Edge findEdge(Game game) {
        assertThat(game.getTileByPosition(HOLDER_TILE)).isNotNull();
        List<String> adjacent = PositionMapper.getAdjacentTilePositions(HOLDER_TILE);
        for (int direction = 0; direction < adjacent.size(); direction++) {
            String neighbour = adjacent.get(direction);
            if (game.getTileByPosition(neighbour) != null
                    && game.getAdjacentTileOverride(HOLDER_TILE, direction) == null) {
                return new Edge(direction, neighbour);
            }
        }
        throw new AssertionError("Test map has no plain neighbour for " + HOLDER_TILE);
    }

    private record Edge(int direction, String neighbour) {}
}
