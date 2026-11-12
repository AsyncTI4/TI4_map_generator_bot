package ti4.helpers.adjacency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.CheckDistanceHelper;
import ti4.helpers.PdsCoverageHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.service.map.CustomHyperlaneService;
import ti4.service.map.HyperlaneBridges;
import ti4.service.map.MapStringMapper;
import ti4.service.option.FOWOptionService.FOWOption;
import ti4.service.testbed.TestBedPresetService;
import ti4.testUtils.BaseTi4Test;

// A north-south column: 301 -> custom hyperlane 201 (N -> S) -> Wellon 101 -> custom hyperlane 000 (N -> S) -> 104.
// Without a bridge matrix the lanes stop at Wellon (LRR 44.1). Wellon's matrix decides which lanes pass through it.
class HyperlanePassThroughTest extends BaseTi4Test {

    private static final int N = 0, NE = 1, SE = 2, S = 3, SW = 4;

    private Game game;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setTile(new Tile("20", "301"));
        game.setTile(new Tile("19", "101"));
        game.setTile(new Tile("22", "104"));
        customLane("201", N, S);
        customLane("000", N, S);
    }

    @Test
    void withoutAMatrixTheLanesStopAtTheSystem() {
        assertThat(movement().from("301")).contains("101").doesNotContain("104");
    }

    @Test
    void theMatrixCarriesTheLaneThroughWellon() {
        bridge("101", N, S, false);
        assertThat(movement().from("301")).contains("104").doesNotContain("101");
        assertThat(movement().into("104")).contains("301");
        assertThat(movement().into("101")).doesNotContain("301");
        assertThat(movement().from("104")).doesNotContain("301");
    }

    // Ships in Wellon can't join its bridge lanes, and the edges those lanes use are lane-only.
    @Test
    void wellonCannotUseItsOwnLanes() {
        bridge("101", N, S, false);
        assertThat(movement().from("101")).doesNotContain("104", "301");
        assertThat(movement().into("101")).doesNotContain("301", "104");
    }

    // A lane that arrives at a side the matrix doesn't list ends in Wellon, while N -> S still passes through.
    @Test
    void aLaneOutsideTheMatrixEndsInTheSystem() {
        bridge("101", N, S, false);
        String northEast = neighbour("101", NE);
        customLane(northEast, NE, SW);
        game.setTile(new Tile("21", neighbour(northEast, NE)));
        assertThat(movement().from(neighbour(northEast, NE))).contains("101").doesNotContain("104");
        assertThat(movement().from("301")).contains("104").doesNotContain("101");
    }

    // With four ends only the listed pairs connect: N -> S and NE -> SE.
    @Test
    void onlyTheListedPairsConnect() {
        String northEast = neighbour("101", NE);
        String southEast = neighbour("101", SE);
        customLane(northEast, NE, SW);
        game.setTile(new Tile("21", neighbour(northEast, NE)));
        game.setTile(new Tile("23", southEast));
        HyperlaneBridges.set(game, "101", typed(new int[][] {{N, S, 2}, {NE, SE, 2}}));
        assertThat(movement().from("301")).contains("104").doesNotContain(southEast, "101");
        assertThat(movement().from(neighbour(northEast, NE)))
                .contains(southEast)
                .doesNotContain("104", "101");
    }

    // 301 -> 201 -> Wellon (N -> S) -> system 000 (N -> S) -> 104: two bridges in a row, and the two bridged systems
    // don't border each other across the edge their lane uses.
    @Test
    void twoBridgesInARow() {
        game.getCustomHyperlaneData().remove("000");
        game.setTile(new Tile("24", "000"));
        bridge("101", N, S, false);
        bridge("000", N, S, false);
        assertThat(movement().from("301")).contains("104").doesNotContain("101", "000");
        assertThat(movement().from("101")).doesNotContain("000");
        assertThat(movement().from("000")).doesNotContain("101");
        assertThat(movement().into("104")).contains("301");
    }

    // Plain systems at both ends of a two-way bridge: 201 and 000 reach each other through Wellon, Wellon reaches
    // neither, and a tear where the lane ends (Wellon | 000) cuts it.
    @Test
    void aBridgeLinksTwoPlainSystems() {
        game.getCustomHyperlaneData().clear();
        game.setTile(new Tile("24", "201"));
        game.setTile(new Tile("25", "000"));
        bridge("101", N, S, true);
        assertThat(movement().from("201")).contains("000").doesNotContain("101");
        assertThat(movement().from("000")).contains("201");
        assertThat(movement().into("000")).contains("201");
        assertThat(movement().from("101")).doesNotContain("201", "000");

        game.addBorderAnomaly("101", S, "spatial_tear");
        assertThat(movement().from("201")).doesNotContain("000");
        assertThat(movement().into("000")).doesNotContain("201");
    }

    // A tear on an edge where the lane carries on into a hyperlane doesn't cut it: border anomalies only count where
    // a lane starts or ends.
    @Test
    void aTearWhereTheLaneCarriesOnIsIgnored() {
        bridge("101", N, S, false);
        game.addBorderAnomaly("101", S, "spatial_tear");
        assertThat(movement().from("301")).contains("104");
        assertThat(movement().into("104")).contains("301");
    }

    // Standard fog: Wellon still sees the hyperlane tiles on its lane sides. FoW+ only lets vision into a hyperlane
    // along a lane, and Wellon can't use its own lanes, so there it sees neither, asked from either side.
    @Test
    void fowPlusHidesTheHyperlanesAcrossWellonsLaneSides() {
        bridge("101", N, S, false);
        game.setFowMode(true);
        assertThat(Adjacency.of(game).forVision().from("101")).contains("201", "000");

        game.setFowOption(FOWOption.FOW_PLUS, true);
        assertThat(Adjacency.of(game).forVision().from("101")).doesNotContain("201", "000");
        assertThat(Adjacency.of(game).forVision().into("201")).doesNotContain("101");
        assertThat(Adjacency.of(game).forVision().from("301")).contains("104");
    }

    @Test
    void effectsCountTheBridgeBothWays() {
        bridge("101", N, S, false);
        assertThat(Adjacency.of(game).forEffects().from("104")).contains("301").doesNotContain("101");
    }

    @Test
    void visionPassesThroughAndRecordsTheBridgeLane() {
        bridge("101", N, S, false);
        game.setFowMode(true);
        assertThat(Adjacency.of(game).forVision().from("301")).contains("104").doesNotContain("101");
        assertThat(GridRule.hyperlaneLanesFrom(game, null, "301"))
                .contains(GridRule.lane("101", N, S), GridRule.lane("000", N, S));
    }

    @Test
    void spaceCannonFollowsTheBridge() {
        Player shooter = pds2Shooter();
        game.getTileByPosition("301").addUnit("vefutii", Units.getUnitKey(UnitType.Pds, "blue"), 1);
        assertThat(PdsCoverageHelper.calculatePdsCoverage(game, game.getTileByPosition("104")))
                .isNull();
        bridge("101", N, S, false);
        assertThat(PdsCoverageHelper.calculatePdsCoverage(game, game.getTileByPosition("104")))
                .containsKey(shooter.getFaction());
    }

    @Test
    void spaceCannonInTheBridgedSystemDoesNotUseItsLanes() {
        pds2Shooter();
        game.getTileByPosition("101").addUnit("wellon", Units.getUnitKey(UnitType.Pds, "blue"), 1);
        bridge("101", N, S, false);
        assertThat(PdsCoverageHelper.calculatePdsCoverage(game, game.getTileByPosition("104")))
                .isNull();
        assertThat(PdsCoverageHelper.calculatePdsCoverage(game, game.getTileByPosition("301")))
                .isNull();
    }

    // The hyperlane-bridges-2p testbed map: Empyrean home 301, Wellon 101 bridged N -> S.
    @Test
    void tacticalDistanceOnTheTestbedMapIsOneStep() {
        Game full = new Game();
        full.newGameSetup();
        MapStringMapper.getMappedTilesToPosition(TestBedPresetService.DEFAULT_MAP_STRING, full)
                .forEach((position, tileId) -> full.setTile(new Tile(tileId, position)));
        String oneWay = "0,0,0,1,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0";
        for (String position : List.of("201", "000")) {
            full.setTile(new Tile("hl", position));
            full.getCustomHyperlaneData().put(position, oneWay);
        }
        HyperlaneBridges.set(full, "101", typed(new int[][] {{N, S, 2}}));
        Player empyrean = full.addPlayer("111", "empyrean");
        empyrean.setFaction(full, "empyrean");
        empyrean.setColor("blue");
        assertThat(CheckDistanceHelper.getTileDistances(full, empyrean, "301", 15, true))
                .containsEntry("104", 1);
    }

    private Player pds2Shooter() {
        Player shooter = game.addPlayer("202", "shooterUser");
        shooter.setFaction("sol");
        shooter.setColor("blue");
        shooter.addOwnedUnitByID("pds2");
        return shooter;
    }

    private Adjacency movement() {
        return Adjacency.of(game).forMovement();
    }

    private void bridge(String position, int from, int to, boolean twoWay) {
        HyperlaneBridges.set(game, position, typed(new int[][] {{from, to, twoWay ? 1 : 2}}));
    }

    private static String typed(int[][] lanes) {
        int[][] grid = new int[6][6];
        for (int[] lane : lanes) grid[lane[0]][lane[1]] = lane[2];
        return CustomHyperlaneService.formatMatrix(grid);
    }

    private static String neighbour(String position, int side) {
        return MapPositionMapper.getAdjacentTilePositions(position).get(side);
    }

    private void customLane(String position, int from, int to) {
        int[][] lanes = new int[6][6];
        lanes[from][to] = 1;
        game.setTile(new Tile("hl", position));
        game.getCustomHyperlaneData().put(position, CustomHyperlaneService.formatMatrix(lanes));
    }
}
