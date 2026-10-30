package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// An Entropic Scar (tile 114) lets a seat with ships in it gain a faction technology for a strategy token at each
// status
// phase. While Sol holds Adapt New Strategies, a destroyer next door moves in to sit there.
class EntropicScarPositionTest extends BaseTi4Test {

    private AiTestGame test;
    private String scar;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setPhaseOfGame("action");
        test.nekroHome();
        scar = AiTestGame.neighbourOf(AiTestGame.HOME);
        Tile solHome = test.solHomeBeside(scar);
        test.place("114", scar);
        test.units(solHome, "space", test.sol, UnitType.Destroyer, 2);
        test.sol.setTacticalCC(3);
    }

    @Test
    void parksAShipInTheScarWhileHoldingAdaptNewStrategies() {
        Optional<TacticalPlan> without = TacticalPlanner.forTarget(test.game, test.sol, scar);
        test.sol.setSecret("ans");

        TacticalPlan plan = TacticalPlanner.forTarget(test.game, test.sol, scar).orElseThrow();

        assertThat(without.map(TacticalPlan::kind)).isNotEqualTo(Optional.of(Kind.POSITION));
        assertThat(plan.kind()).isEqualTo(Kind.POSITION);
        assertThat(plan.moves()).anyMatch(move -> move.type() == UnitType.Destroyer);
    }
}
