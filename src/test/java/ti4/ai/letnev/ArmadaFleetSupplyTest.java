package ti4.ai.letnev;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.FleetLimit;
import ti4.ai.nekro.CommandTokenPolicy;
import ti4.ai.tactical.ProductionPlanner;
import ti4.ai.tactical.ProductionPlanner.BuildPlan;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.ai.tactical.TacticalPlanner;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Armada raises the number of non-fighter ships allowed in a system by 2 over the fleet pool, so everything that plans
// with the fleet pool has to plan with that limit, and the Barony needs two tokens fewer in the pool for the same
// fleet.
class ArmadaFleetSupplyTest extends BaseTi4Test {

    private AiTestGame test;
    private Tile home;
    private String neighbour;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setRound(3);
        home = test.nekroHome();
        neighbour = AiTestGame.neighbourOf(AiTestGame.HOME);
        test.nekro.setFleetCC(1);
    }

    @Test
    void theLimitIsThePoolPlusTwoWithArmada() {
        assertThat(FleetLimit.ships(test.nekro)).isEqualTo(1);

        test.nekro.addAbility("armada");

        assertThat(FleetLimit.ships(test.nekro)).isEqualTo(3);
        assertThat(FleetLimit.bonus(test.nekro)).isEqualTo(2);
    }

    @Test
    void theHeroLiftsTheLimitAltogether() {
        test.nekro.addLeader("letnevhero");
        assertThat(FleetLimit.ships(test.nekro)).isEqualTo(1);

        test.nekro.getLeader("letnevhero").orElseThrow().setActive(true);

        assertThat(FleetLimit.ships(test.nekro)).isGreaterThan(100);
    }

    @Test
    void anAssumptionOfUnlimitedFleetLiftsTheLimitOnlyInsideTheEvaluation() {
        assertThat(FleetLimit.assumingUnlimited(test.nekro, () -> FleetLimit.ships(test.nekro)))
                .isGreaterThan(100);
        assertThat(FleetLimit.assumingUnlimited(test.nekro, () -> FleetLimit.ships(test.sol)))
                .isEqualTo(test.sol.getFleetCC());
        assertThat(FleetLimit.ships(test.nekro)).isEqualTo(1);
    }

    private void bigFleetAtHomeAgainstSolOnLodor() {
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 3);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 3);
        Tile lodor = test.place("26", neighbour);
        test.sol.addPlanet("lodor");
        test.units(lodor, "space", test.sol, UnitType.Cruiser, 2);
        test.units(lodor, "lodor", test.sol, UnitType.Infantry, 1);
    }

    private int dreadnoughtsSent() {
        Optional<TacticalPlan> plan = TacticalPlanner.forTarget(test.game, test.nekro, neighbour);
        return plan.map(found -> found.moves().stream()
                        .filter(move -> move.type() == UnitType.Dreadnought)
                        .mapToInt(UnitMove::count)
                        .sum())
                .orElse(0);
    }

    @Test
    void anAttackMayStackTwoMoreShipsThanThePoolWithArmada() {
        bigFleetAtHomeAgainstSolOnLodor();
        int withoutArmada = dreadnoughtsSent();

        test.nekro.addAbility("armada");

        assertThat(withoutArmada).isLessThanOrEqualTo(1);
        assertThat(dreadnoughtsSent()).isEqualTo(3);
    }

    @Test
    void buildsUpToTheArmadaLimitInAHomeSystemHoldingOneShip() {
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 5);
        test.nekro.setTg(10);
        int crowded = shipsBuilt();
        test.nekro.addAbility("armada");

        int roomy = shipsBuilt();

        assertThat(crowded).isZero();
        assertThat(roomy).isEqualTo(2);
        assertThat(BoardView.nonFighterShips(BoardView.space(home), test.nekro)).isEqualTo(1);
    }

    private int shipsBuilt() {
        BuildPlan plan = ProductionPlanner.plan(test.game, test.nekro, home);
        return BoardView.MOVING_SHIPS.stream().mapToInt(plan::units).sum();
    }

    @Test
    void needsTwoFewerFleetTokensForTheSameLargestStack() {
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 5);
        test.nekro.setFleetCC(0);
        test.nekro.setTacticalCC(0);
        test.nekro.setStrategicCC(0);
        int plain = CommandTokenPolicy.ideal(test.game, test.nekro, 6).get(CommandTokenPolicy.Pool.FLEET);

        test.nekro.addAbility("armada");
        int armada = CommandTokenPolicy.ideal(test.game, test.nekro, 6).get(CommandTokenPolicy.Pool.FLEET);

        assertThat(plain).isEqualTo(5);
        assertThat(armada).isEqualTo(3);
    }
}
