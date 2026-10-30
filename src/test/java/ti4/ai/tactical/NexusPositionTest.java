package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.scoring.ObjectiveValue;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Defy Space and Time needs a unit in the wormhole nexus. Once the nexus is active (82b) it has an alpha and a beta
// wormhole, so a destroyer in any other alpha or beta system can reach it; the locked side (82a) only has gamma.
class NexusPositionTest extends BaseTi4Test {

    private static final String NEXUS_SPOT = "310";

    private AiTestGame test;
    private Tile alpha;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setPhaseOfGame("action");
        test.nekroHome();
        String spot = AiTestGame.neighbourOf(AiTestGame.HOME);
        alpha = test.place("39", spot);
        test.solHomeBeside(spot);
        test.units(alpha, "space", test.sol, UnitType.Destroyer, 2);
        test.sol.setTacticalCC(3);
        test.sol.setSecret("dfat");
    }

    private Optional<TacticalPlan> planToNexus() {
        return TacticalPlanner.forTarget(test.game, test.sol, NEXUS_SPOT);
    }

    @Test
    void sendsADestroyerThroughTheAlphaWormholeIntoTheActiveNexus() {
        test.place("82b", NEXUS_SPOT);

        TacticalPlan plan = planToNexus().orElseThrow();

        assertThat(plan.moves()).anyMatch(move -> move.type() == UnitType.Destroyer);
    }

    @Test
    void cannotReachTheLockedNexusFromAnAlphaSystem() {
        test.place("82a", NEXUS_SPOT);

        assertThat(planToNexus()).isEmpty();
    }

    // A ship parked in the nexus is a finished objective: pulling it out breaks Defy Space and Time, so the movement
    // that does so is charged for it.
    @Test
    void chargesForPullingTheParkedShipOutOfTheNexus() {
        Tile nexus = test.place("82b", NEXUS_SPOT);
        test.units(nexus, "space", test.sol, UnitType.Destroyer, 1);
        ObjectiveValue view = new ObjectiveValue(test.game, test.sol);

        assertThat(view.brokenReady(view.before().withoutShipsIn(NEXUS_SPOT))).containsExactly("dfat");
        assertThat(view.gain(view.before().withoutShipsIn(NEXUS_SPOT))).isNegative();
    }
}
