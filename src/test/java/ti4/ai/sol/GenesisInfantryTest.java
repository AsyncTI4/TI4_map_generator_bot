package ti4.ai.sol;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.ai.tactical.TacticalPlanner;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Genesis leaves an infantry in the space area of its system at the end of every status phase. It is a ground force
// like any other: the planner lands it on a free planet in that system, and carries it along when the fleet moves.
class GenesisInfantryTest extends BaseTi4Test {

    private AiTestGame test;
    private Player sol;
    private Tile home;
    private Tile outpost;

    @BeforeEach
    void setUp() {
        test = AiTestGame.withSolAi();
        sol = test.sol;
        test.game.setRound(3);
        home = test.place("01", AiTestGame.HOME);
        sol.addPlanet("jord");
        test.units(home, "jord", sol, UnitType.Spacedock, 1);
        test.units(home, "jord", sol, UnitType.Infantry, 3);
        outpost = test.place("26", AiTestGame.neighbourOf(AiTestGame.HOME));
        sol.setTacticalCC(3);
        sol.setStrategicCC(2);
        sol.setFleetCC(5);
        test.isActive(sol, "action");
    }

    @Test
    void landsTheFlagshipsInfantryOnAFreePlanetInItsSystem() {
        test.units(outpost, "space", sol, UnitType.Flagship, 1);
        test.units(outpost, "space", sol, UnitType.Infantry, 1);

        Optional<TacticalPlan> plan = TacticalPlanner.best(test.game, sol);

        assertThat(plan).isPresent();
        assertThat(plan.get().kind()).isEqualTo(Kind.EXPAND);
        assertThat(plan.get().target()).isEqualTo(outpost.getPosition());
        assertThat(plan.get().landings()).containsEntry("lodor", 1);
    }

    @Test
    void leavesTheInfantryAloneWhenThereIsNothingToTake() {
        test.units(home, "space", sol, UnitType.Flagship, 1);
        test.units(home, "space", sol, UnitType.Infantry, 1);
        sol.addPlanet("lodor");

        Optional<TacticalPlan> plan = TacticalPlanner.best(test.game, sol);

        assertThat(plan.map(TacticalPlan::landings).orElse(java.util.Map.of())).isEmpty();
    }
}
