package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// A stack bigger than the seat's fleet supply still marches toward Styx with as many ships as the supply allows,
// instead of being dropped from the gathering altogether.
class StyxFleetSupplyTest extends BaseTi4Test {

    private static Optional<TacticalPlan> stageOntoIngress(int fleetTokens) {
        FractureScenario fracture = new FractureScenario();
        fracture.nekro().setFleetCC(fleetTokens);
        fracture.ships(fracture.board.home, UnitType.Dreadnought, 4);
        fracture.ships(fracture.board.home, UnitType.Carrier, 2);
        fracture.ships(fracture.board.home, UnitType.Infantry, 6);
        return TacticalPlanner.ranked(fracture.board.test.game, fracture.nekro()).stream()
                .filter(plan -> plan.kind() == Kind.STAGE && plan.target().equals(fracture.ingress.getPosition()))
                .findFirst();
    }

    @Test
    void aStackThatFitsTheFleetSupplyStages() {
        assertThat(stageOntoIngress(6)).isPresent();
    }

    @Test
    void aStackBiggerThanTheFleetSupplyStagesWhatFits() {
        assertThat(stageOntoIngress(4)).isPresent();
    }
}
