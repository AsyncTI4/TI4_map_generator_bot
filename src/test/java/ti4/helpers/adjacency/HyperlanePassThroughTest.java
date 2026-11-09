package ti4.helpers.adjacency;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.CheckDistanceHelper;
import ti4.helpers.PdsCoverageHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.service.map.CustomHyperlaneService;
import ti4.service.map.HyperlaneStyleService;
import ti4.service.map.MapStringMapper;
import ti4.service.testbed.TestBedPresetService;
import ti4.testUtils.BaseTi4Test;

// One-way chain 301 -> custom hyperlane 201 -> Wellon 101 -> custom hyperlane 000 -> 104 (all lanes N -> S).
// With bridges on the drawn line continues through Wellon, so by LRR 44.1 it connects 301 and 104; Wellon is not
// linked to it.
class HyperlanePassThroughTest extends BaseTi4Test {

    private Game game;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setTile(new Tile("20", "301"));
        game.setTile(new Tile("19", "101"));
        game.setTile(new Tile("22", "104"));
        customLane("201", 0, 3);
        customLane("000", 0, 3);
    }

    @Test
    void bridgesOffFollowTheOfficialRule() {
        assertThat(movement().from("301")).contains("101").doesNotContain("104");
    }

    @Test
    void bridgesOnPassThroughWellon() {
        HyperlaneStyleService.setShowsBridges(game, true);
        assertThat(movement().from("301")).contains("104").doesNotContain("101");
        assertThat(movement().into("104")).contains("301");
        assertThat(movement().into("101")).doesNotContain("301");
    }

    // The bridged system keeps its natural neighbours but cannot use the lanes that pass through it.
    @Test
    void wellonCannotUseTheLanesThroughIt() {
        HyperlaneStyleService.setShowsBridges(game, true);
        assertThat(movement().from("101")).doesNotContain("104", "301");
        assertThat(movement().into("101")).doesNotContain("301", "104");
    }

    @Test
    void oneWayPassThroughOnlyFollowsTheLanes() {
        HyperlaneStyleService.setShowsBridges(game, true);
        assertThat(movement().from("104")).doesNotContain("301");
        assertThat(movement().into("301")).doesNotContain("104");
    }

    // Outside movement, with one-way adjacency off, lanes count both ways; so does the pass-through.
    @Test
    void effectsCountThePassThroughBothWays() {
        HyperlaneStyleService.setShowsBridges(game, true);
        assertThat(Adjacency.of(game).forEffects().from("104")).contains("301").doesNotContain("101");
    }

    // Official hyperlane tiles keep LRR 44.1: a static lane next to Wellon leaves only one custom end, so no bridge.
    @Test
    void staticHyperlaneTilesDoNotPassThrough() {
        HyperlaneStyleService.setShowsBridges(game, true);
        game.getCustomHyperlaneData().remove("000");
        game.setTile(new Tile("83a120", "000"));
        assertThat(movement().from("301")).contains("101").doesNotContain("104");
    }

    // Fog vision passes through without reaching Wellon, and the lane beyond counts as seen.
    @Test
    void visionPassesThroughAndSeesTheLaneBeyond() {
        HyperlaneStyleService.setShowsBridges(game, true);
        game.setFowMode(true);
        assertThat(Adjacency.of(game).forVision().from("301")).contains("104").doesNotContain("101");
        assertThat(GridRule.hyperlaneLanesFrom(game, null, "301")).contains(GridRule.lane("000", 0, 3));
    }

    // Space Cannon (PDS II) follows the same walk: from 301 it reaches 104 through Wellon, from Wellon it reaches
    // neither end of the lanes that pass through it.
    @Test
    void spaceCannonFollowsThePassThrough() {
        Player shooter = pds2Shooter();
        game.getTileByPosition("301").addUnit("vefutii", Units.getUnitKey(UnitType.Pds, "blue"), 1);
        assertThat(PdsCoverageHelper.calculatePdsCoverage(game, game.getTileByPosition("104")))
                .isNull();

        HyperlaneStyleService.setShowsBridges(game, true);
        assertThat(PdsCoverageHelper.calculatePdsCoverage(game, game.getTileByPosition("104")))
                .containsKey(shooter.getFaction());
    }

    @Test
    void spaceCannonInTheBridgedSystemDoesNotUseTheLanes() {
        Player shooter = pds2Shooter();
        game.getTileByPosition("101").addUnit("wellon", Units.getUnitKey(UnitType.Pds, "blue"), 1);
        assertThat(PdsCoverageHelper.calculatePdsCoverage(game, game.getTileByPosition("104")))
                .containsKey(shooter.getFaction());

        HyperlaneStyleService.setShowsBridges(game, true);
        assertThat(PdsCoverageHelper.calculatePdsCoverage(game, game.getTileByPosition("104")))
                .isNull();
        assertThat(PdsCoverageHelper.calculatePdsCoverage(game, game.getTileByPosition("301")))
                .isNull();
    }

    private Player pds2Shooter() {
        Player shooter = game.addPlayer("202", "shooterUser");
        shooter.setFaction("sol");
        shooter.setColor("blue");
        shooter.addOwnedUnitByID("pds2");
        return shooter;
    }

    // The hyperlane-bridges-2p testbed map: default map, hyperlanes at 201/000/203/206/208, Empyrean home 301.
    @Test
    void tacticalDistanceOnTheTestbedMapIsOneStep() {
        Game full = new Game();
        full.newGameSetup();
        MapStringMapper.getMappedTilesToPosition(TestBedPresetService.DEFAULT_MAP_STRING, full)
                .forEach((position, tileId) -> full.setTile(new Tile(tileId, position)));
        String oneWay = "0,0,0,1,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0";
        for (String position : new String[] {"201", "000"}) {
            full.setTile(new Tile("hl", position));
            full.getCustomHyperlaneData().put(position, oneWay);
        }
        full.getCustomHyperlaneData()
                .put("000", "0,0,0,1,0,0;0,0,0,0,1,0;0,0,0,0,0,0;0,0,0,0,0,0;0,1,0,0,0,0;0,0,0,0,0,0");
        Player empyrean = full.addPlayer("111", "empyrean");
        empyrean.setFaction(full, "empyrean");
        empyrean.setColor("blue");
        HyperlaneStyleService.setShowsBridges(full, true);

        assertThat(CheckDistanceHelper.getTileDistances(full, empyrean, "301", 15, true))
                .containsEntry("104", 1);
    }

    private Adjacency movement() {
        return Adjacency.of(game).forMovement();
    }

    private void customLane(String position, int from, int to) {
        int[][] lanes = new int[6][6];
        lanes[from][to] = 1;
        game.setTile(new Tile("hl", position));
        game.getCustomHyperlaneData().put(position, CustomHyperlaneService.formatMatrix(lanes));
    }
}
