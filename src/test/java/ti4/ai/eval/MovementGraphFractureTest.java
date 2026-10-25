package ti4.ai.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.service.map.FractureService;
import ti4.testUtils.BaseTi4Test;

// The Fracture is reached only through ingress tokens: a system with an ingress is adjacent to the two egress systems
// (frac2 and frac6), the Fracture's own systems form a row, and nothing else joins it to the board.
class MovementGraphFractureTest extends BaseTi4Test {

    private AiTestGame test;
    private Tile ingress;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        FractureService.spawnFracture(null, test.game);
        ingress = test.place("26", AiTestGame.neighbourOf(AiTestGame.HOME));
        ingress.addToken(Constants.TOKEN_INGRESS, "space");
    }

    private Map<String, Integer> reach(Tile from, int move) {
        return MovementGraph.reach(test.game, test.nekro, from.getPosition(), move);
    }

    @Test
    void anIngressSystemIsAdjacentToBothEgressSystems() {
        Map<String, Integer> reach = reach(ingress, 1);

        assertThat(reach).containsEntry("frac2", 1).containsEntry("frac6", 1);
        assertThat(reach).doesNotContainKeys("frac1", "frac3", "frac4", "frac5", "frac7");
    }

    @Test
    void cocytusAndLetheAreTwoMovesAndStyxIsThree() {
        assertThat(reach(ingress, 2)).containsEntry("frac1", 2).containsEntry("frac7", 2);
        assertThat(reach(ingress, 2)).doesNotContainKey("frac4");
        assertThat(reach(ingress, 3)).containsEntry("frac4", 3);
    }

    @Test
    void theFractureIsNotAdjacentToTheSystemsItTouches() {
        ingress.getSpaceUnitHolder().removeToken(Constants.TOKEN_INGRESS);
        Tile plain = test.place("27", AiTestGame.neighbourOf(ingress.getPosition()));

        assertThat(reach(plain, 8)).doesNotContainKeys("frac1", "frac2", "frac4", "frac6");
    }

    @Test
    void ingressSystemsAreOnlyJoinedThroughAnEgressSystem() {
        Tile other = test.place(
                "28", MapPositionMapper.getAdjacentTilePositions("000").getFirst());
        other.addToken(Constants.TOKEN_INGRESS, "space");

        assertThat(reach(ingress, 1)).doesNotContainKey(other.getPosition());
        assertThat(reach(ingress, 2)).containsEntry(other.getPosition(), 2);
    }

    @Test
    void shipsOnBothApproachesToStyxScreenIt() {
        test.units(test.game.getTileByPosition("frac3"), "space", test.sol, UnitType.Destroyer, 1);
        assertThat(reach(ingress, 4)).containsKey("frac4");

        test.units(test.game.getTileByPosition("frac5"), "space", test.sol, UnitType.Destroyer, 1);
        Map<String, Integer> reach = reach(ingress, 4);

        assertThat(reach).containsKeys("frac3", "frac5").doesNotContainKey("frac4");
    }
}
