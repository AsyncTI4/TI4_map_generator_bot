package ti4.ai.sol;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.eval.BoardView;
import ti4.ai.tactical.ProductionPlanner;
import ti4.ai.tactical.ProductionPlanner.BuildPlan;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Advanced Carrier II carries 8 and sustains damage, so one of them does the work the planner otherwise buys two
// carriers for.
class SolProductionTest extends BaseTi4Test {

    private AiTestGame test;
    private Player sol;
    private Tile home;

    @BeforeEach
    void setUp() {
        test = AiTestGame.withSolAi();
        sol = test.sol;
        test.game.setRound(4);
        home = test.place("01", AiTestGame.HOME);
        sol.addPlanet("jord");
        test.units(home, "jord", sol, UnitType.Spacedock, 1);
        test.units(home, "jord", sol, UnitType.Infantry, 4);
        sol.setFleetCC(5);
        sol.setTg(6);
    }

    @Test
    void anAdvancedCarrierIIHasCapacityEightAndSustains() {
        sol.addTech("ac2");

        assertThat(BoardView.capacity(sol, UnitType.Carrier)).isEqualTo(8);
        assertThat(sol.getUnitByType(UnitType.Carrier).getSustainDamage()).isTrue();
    }

    @Test
    void buildsASecondCarrierWhileTheCarriersCarryLittle() {
        test.units(home, "space", sol, UnitType.Carrier, 1);
        BuildPlan plan = ProductionPlanner.plan(test.game, sol, home);

        assertThat(plan.units(UnitType.Carrier)).isEqualTo(1);
    }

    @Test
    void doesNotBuyASecondCarrierOnceOneCarriesEight() {
        test.units(home, "space", sol, UnitType.Carrier, 1);
        sol.addTech("ac2");

        BuildPlan plan = ProductionPlanner.plan(test.game, sol, home);

        assertThat(plan.units(UnitType.Carrier)).isZero();
    }

    @Test
    void stillBuysTheFirstAdvancedCarrierWhenThereIsNone() {
        sol.addTech("ac2");

        BuildPlan plan = ProductionPlanner.plan(test.game, sol, home);

        assertThat(plan.units(UnitType.Carrier)).isEqualTo(1);
    }
}
