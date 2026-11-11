package ti4.helpers.adjacency;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.model.MapPositionMapper;
import ti4.service.map.CustomHyperlaneService;
import ti4.service.map.HyperlanePortals;
import ti4.testUtils.BaseTi4Test;

// Roundabout R1 at 000 with one spoke north into system 101; roundabout R2 at 304 with one spoke south into system X.
// The two are far apart; a portal letter on both links them.
class HyperlanePortalRulesTest extends BaseTi4Test {

    private static final String R1 = "000", R2 = "304", INTERCHANGE = "210";

    private Game game;
    private String x;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setTile(new Tile("19", "101"));
        x = MapPositionMapper.getAdjacentTilePositions(R2).get(3);
        game.setTile(new Tile("20", x));
        roundabout(R1, 0);
        roundabout(R2, 3);
    }

    @Test
    void withoutPortalsTheRoundaboutsAreUnrelated() {
        assertThat(movement().from("101")).doesNotContain(x);
    }

    @Test
    void aLetterLinksTheRoundaboutsBothWays() {
        portals(R1, "0=A");
        portals(R2, "1=A");
        assertThat(movement().from("101")).contains(x);
        assertThat(movement().from(x)).contains("101");
        assertThat(movement().into(x)).contains("101");
    }

    // Entry-only on R1: ships jump from R1 and arrive at R2, never back.
    @Test
    void oneWaySlotsKeepTheirDirection() {
        portals(R1, "0=A>");
        portals(R2, "1=A");
        assertThat(movement().from("101")).contains(x);
        assertThat(movement().from(x)).doesNotContain("101");
        assertThat(movement().into("101")).doesNotContain(x);
        assertThat(Adjacency.of(game).forEffects().from(x)).contains("101");
    }

    // A spokeless hyperlane with A and B joins the A network to the B network.
    @Test
    void aSpokelessInterchangeLinksTwoLetters() {
        portals(R1, "0=A");
        game.setTile(new Tile("hl", INTERCHANGE));
        portals(INTERCHANGE, "2=A, 5=B");
        portals(R2, "1=B");
        assertThat(movement().from("101")).contains(x);
        assertThat(movement().into(x)).contains("101");
    }

    // R1's lanes only lead from N to S; its SW spoke is not reachable from the north. A portal hop to R2 and back must
    // not open it: the walk never bounces back onto the ring it left.
    @Test
    void aPortalHopNeverBouncesBackToItsOwnRing() {
        game.setTile(new Tile("hl", R1));
        game.getCustomHyperlaneData()
                .put(R1, "1,0,0,1,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,1,0,0;0,0,0,0,1,0;0,0,0,0,0,0");
        game.setTile(new Tile("21", "104"));
        game.setTile(new Tile("22", "105"));
        portals(R1, "A");
        portals(R2, "A");
        assertThat(movement().from("101")).contains("104", x).doesNotContain("105");
    }

    // Tokens the walk passes are recorded even when ships can't use them, so Hide unseen hyperlanes still shows them.
    @Test
    void visionRecordsEveryTokenOnARingItReaches() {
        portals(R1, "A, C<");
        portals(R2, "A");
        game.setFowMode(true);
        assertThat(GridRule.hyperlaneLanesFrom(game, null, "101")).contains(GridRule.portalLane(R1, 'C'));
    }

    @Test
    void visionThroughAPortalRecordsIt() {
        portals(R1, "0=A");
        portals(R2, "1=A");
        game.setFowMode(true);
        assertThat(Adjacency.of(game).forVision().from("101")).contains(x);
        assertThat(GridRule.hyperlaneLanesFrom(game, null, "101"))
                .contains(GridRule.portalLane(R1, 'A'), GridRule.portalLane(R2, 'A'));
    }

    private Adjacency movement() {
        return Adjacency.of(game).forMovement();
    }

    private void portals(String position, String typed) {
        HyperlanePortals.set(game, position, HyperlanePortals.parseTyped(typed).slots());
    }

    private void roundabout(String position, int spoke) {
        int[][] lanes = new int[6][6];
        lanes[spoke][spoke] = 1;
        game.setTile(new Tile("hl", position));
        game.getCustomHyperlaneData().put(position, CustomHyperlaneService.formatMatrix(lanes));
    }
}
