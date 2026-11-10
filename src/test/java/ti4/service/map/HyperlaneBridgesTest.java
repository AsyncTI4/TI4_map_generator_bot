package ti4.service.map;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.testUtils.BaseTi4Test;

class HyperlaneBridgesTest extends BaseTi4Test {

    // N -> S one-way, NE <-> SW two-way.
    private static final String TYPED = "0,0,0,2,0,0;0,0,0,0,1,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0";
    private static final String NORTH_TO_SOUTH =
            "0,0,0,2,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0";
    private static final String EMPTY = "0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0";

    private Game game;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setTile(new Tile("19", "000"));
        game.setTile(new Tile("hl", "101"));
    }

    @Test
    void storesTheLanesOfASystem() {
        HyperlaneBridges.set(game, "000", TYPED);
        BridgeMatrix matrix = HyperlaneBridges.matrixOf(game, "000");
        assertThat(matrix).isNotNull();
        assertThat(matrix.leads(0, 3)).isTrue();
        assertThat(matrix.leads(3, 0)).isFalse();
        assertThat(matrix.leads(1, 4) && matrix.leads(4, 1)).isTrue();
        assertThat(matrix.ends())
                .containsExactly(
                        new BridgeEnd(0, true, false),
                        new BridgeEnd(1, true, true),
                        new BridgeEnd(3, false, true),
                        new BridgeEnd(4, true, true));
        assertThat(HyperlaneBridges.exits(game, "000", 0, false)).containsExactly(3);
        assertThat(HyperlaneBridges.exits(game, "000", 3, false)).isEmpty();
        assertThat(HyperlaneBridges.exits(game, "000", 2, false)).isNull();
        assertThat(HyperlaneBridges.entriesLeadingTo(game, "000", 3, true)).containsExactly(0);
    }

    @Test
    void anEmptyMatrixOrRemoveClearsTheStoredValue() {
        HyperlaneBridges.set(game, "000", TYPED);
        HyperlaneBridges.set(game, "000", EMPTY);
        assertThat(game.getStoredValueMap()).doesNotContainKey(HyperlaneBridges.KEY);

        HyperlaneBridges.set(game, "000", TYPED);
        HyperlaneBridges.remove(game, "000");
        assertThat(HyperlaneBridges.all(game)).isEmpty();
    }

    @Test
    void refusesHyperlanesMissingTilesAndRoundabouts() {
        assertThat(HyperlaneBridges.problemWith(game, "101", TYPED)).isPresent();
        assertThat(HyperlaneBridges.problemWith(game, "999", TYPED)).isPresent();
        assertThat(HyperlaneBridges.problemWith(game, "000", TYPED.replaceFirst("0", "1")))
                .isPresent();
        assertThat(HyperlaneBridges.problemWith(game, "000", "nonsense")).isPresent();
        assertThat(HyperlaneBridges.problemWith(game, "000", NORTH_TO_SOUTH)).isEmpty();
    }

    // Live games carry stale lane data on system positions; only the bridge store makes a bridge, and a bridge whose
    // tile has become a hyperlane no longer counts.
    @Test
    void onlyTheBridgeStoreOnASystemCounts() {
        game.getCustomHyperlaneData()
                .put("000", "0,0,0,1,0,0;0,0,0,0,0,0;0,0,0,0,0,0;1,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0");
        assertThat(HyperlaneBridges.matrixOf(game, "000")).isNull();

        HyperlaneBridges.set(game, "000", TYPED);
        game.setTile(new Tile("hl", "000"));
        assertThat(HyperlaneBridges.matrixOf(game, "000")).isNull();
    }

    // Every lane needs its own stretch of rim: no splits, merges, crossings or lanes that can only overlap.
    @Test
    void refusesLanesThatCantEachHaveTheirOwnStretchOfRim() {
        assertThat(problem(new int[][] {{0, 3}, {0, 4}})).contains("N is used by");
        assertThat(problem(new int[][] {{0, 3}, {1, 3}})).contains("S is used by");
        assertThat(problem(new int[][] {{0, 3}, {1, 4}})).contains("would cross");
        assertThat(problem(new int[][] {{0, 3}, {1, 2}, {4, 5}})).contains("own stretch of the rim");
        assertThat(problem(new int[][] {{0, 3}, {2, 1}})).isEmpty();
        assertThat(problem(new int[][] {{0, 1}, {2, 3}, {4, 5}})).isEmpty();
    }

    private String problem(int[][] oneWayLanes) {
        int[][] grid = new int[6][6];
        for (int[] lane : oneWayLanes) grid[lane[0]][lane[1]] = 2;
        return HyperlaneBridges.problemWith(game, "000", CustomHyperlaneService.formatMatrix(grid))
                .orElse("");
    }

    @Test
    void spinningRingsMovesTheBridgeWithItsTile() {
        HyperlaneBridges.set(game, "000", TYPED);
        HyperlaneBridges.moveAll(game, Map.of("000", "102", "102", "000"));
        assertThat(HyperlaneBridges.all(game)).containsOnlyKeys("102");
    }

    @Test
    void keepsOnlyTheRequestedPairs() {
        HyperlaneBridges.set(game, "000", TYPED);
        BridgeMatrix kept = HyperlaneBridges.matrixOf(game, "000").keepingPairs((a, b) -> a == 0);
        assertThat(kept.sides()).containsExactlyElementsOf(List.of(0, 3));
    }
}
