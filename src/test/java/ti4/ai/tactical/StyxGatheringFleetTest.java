package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.eval.FractureCampaign;
import ti4.ai.nekro.CommandTokenPolicy;
import ti4.ai.nekro.CommandTokenPolicy.Pool;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// A forced-Fracture replay (seed 9700) showed seats with 15 to 35 resources of ships two or three moves from Styx and
// only three or four fleet tokens, so the stack they could bring never beat the guard and they never gathered. Ships
// that can reach Styx within two tactical actions now ask for a fleet token each, up to six.
class StyxGatheringFleetTest extends BaseTi4Test {

    private static final String BESIDE_THE_INGRESS = "frac2";

    private FractureScenario fracture;

    @BeforeEach
    void setUp() {
        fracture = new FractureScenario();
    }

    // Styx is two moves from the fracture system next to the ingress, so dreadnoughts there reach it in two actions.
    @Test
    void shipsTwoMovesFromStyxAskForAFleetTokenEach() {
        fracture.ships(BESIDE_THE_INGRESS, UnitType.Dreadnought, 3);
        fracture.ships(BESIDE_THE_INGRESS, UnitType.Carrier, 1);

        assertThat(FractureCampaign.gatheringFleet(fracture.board.test.game, fracture.nekro()))
                .isEqualTo(4);
    }

    // Styx is four moves from home: dreadnoughts there need four actions, cruisers only two.
    @Test
    void onlyShipsFastEnoughToArriveInTwoActionsCount() {
        fracture.ships(fracture.board.home, UnitType.Dreadnought, 3);

        assertThat(FractureCampaign.gatheringFleet(fracture.board.test.game, fracture.nekro()))
                .isZero();

        fracture.ships(fracture.board.home, UnitType.Cruiser, 2);

        assertThat(FractureCampaign.gatheringFleet(fracture.board.test.game, fracture.nekro()))
                .isEqualTo(2);
    }

    @Test
    void theDemandStopsAtSixShips() {
        fracture.ships(BESIDE_THE_INGRESS, UnitType.Dreadnought, 8);

        assertThat(FractureCampaign.gatheringFleet(fracture.board.test.game, fracture.nekro()))
                .isEqualTo(FractureCampaign.GATHER_FLEET_MAX);
    }

    @Test
    void aSeatHoldingStyxAsksForNoGatheringFleet() {
        fracture.ships(BESIDE_THE_INGRESS, UnitType.Dreadnought, 4);
        fracture.nekro().addPlanet("styx");

        assertThat(FractureCampaign.gatheringFleet(fracture.board.test.game, fracture.nekro()))
                .isZero();
    }

    // Six ships within reach of Styx, in two stacks of three, keep six fleet tokens out of the status phase: more than
    // the largest stack and past the four of the dock's fleet. Twelve tokens leave room for the tactics and strategy
    // tokens that come first.
    @Test
    void theStatusPhaseKeepsAFleetTokenForEveryShipThatCanGather() {
        threeAndThreeThatCanGather();
        fracture.board.test.game.setPhaseOfGame("statusHomework");

        Map<Pool, Integer> ideal = CommandTokenPolicy.ideal(fracture.board.test.game, fracture.nekro(), 12);

        assertThat(ideal.get(Pool.FLEET)).isEqualTo(6);
    }

    // In the action phase a Leadership token goes to the fleet while ships that can gather outnumber the pool.
    @Test
    void anActionPhaseTokenGrowsTheFleetTowardTheShipsThatCanGather() {
        threeAndThreeThatCanGather();
        fracture.board.test.game.setPhaseOfGame("action");
        fracture.nekro().setTacticalCC(3);
        fracture.nekro().setFleetCC(4);
        fracture.nekro().setStrategicCC(2);

        assertThat(CommandTokenPolicy.poolToGrow(fracture.board.test.game, fracture.nekro()))
                .isEqualTo("increase_fleet_cc");
    }

    private void threeAndThreeThatCanGather() {
        fracture.ships(BESIDE_THE_INGRESS, UnitType.Dreadnought, 3);
        fracture.ships(fracture.board.home, UnitType.Cruiser, 3);
    }
}
