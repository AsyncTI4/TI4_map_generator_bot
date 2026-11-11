package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.eval.BoardView;
import ti4.ai.tactical.ProductionPlanner.BuildOrder;
import ti4.ai.tactical.ProductionPlanner.BuildPlan;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// When the fleet pool is the limit, the planner fills its last slots with the ships worth the most, not with whatever
// an earlier phase asked for first. The reported case: Nekro, rich, one dock near a Fracture ingress, built a cheap
// dreadnought for the Fracture fleet and had no slot left for its flagship.
class ProductionFleetSlotTest extends BaseTi4Test {

    private FractureScenario fracture;
    private Tile home;

    @BeforeEach
    void setUp() {
        fracture = new FractureScenario();
        home = fracture.board.home;
        fracture.ships(home, UnitType.Carrier, 2);
        fracture.ships(home, UnitType.Dreadnought, 1);
        fracture.board.test.units(home, "mordaiii", fracture.nekro(), UnitType.Infantry, 4);
        // Three ships at home and a fleet pool of four: one slot left.
        fracture.nekro().setFleetCC(4);
    }

    private BuildPlan plan() {
        return ProductionPlanner.plan(fracture.board.test.game, fracture.nekro(), home);
    }

    @Test
    void theFlagshipTakesTheLastFleetSlotWhenResourcesArePlentiful() {
        fracture.nekro().setTg(30);

        BuildPlan plan = plan();

        assertThat(plan.units(UnitType.Flagship)).isEqualTo(1);
        assertThat(plan.units(UnitType.Dreadnought)).isZero();
    }

    // Ten trade goods pay for the two mechs and the dreadnought; the flagship in its place would leave nothing for the
    // rest of the build.
    @Test
    void cashShortKeepsTheCheaperShip() {
        fracture.nekro().exhaustPlanet("mordaiii");
        fracture.nekro().setTg(10);

        BuildPlan plan = plan();

        assertThat(plan.units(UnitType.Flagship)).isZero();
        assertThat(plan.units(UnitType.Dreadnought)).isEqualTo(1);
    }

    // Make an Example of Them wants two ships with bombardment and the seat has one dreadnought: the second dreadnought
    // built for it is locked in and the richer flagship cannot push it out of the last slot.
    @Test
    void aSwapNeverDisplacesALockedScoringShip() {
        fracture.nekro().setSecret("mew");
        fracture.nekro().setTg(30);

        List<BuildOrder> orders = plan().orders();

        assertThat(orders.getFirst().type()).isEqualTo(UnitType.Dreadnought);
        assertThat(orders.getFirst().scoring()).isTrue();
    }

    // Raise a Fleet wants five ships in one system. Three dreadnoughts sit at home and the fleet pool holds five, so
    // two
    // ships are missing. Both used to be destroyers that nothing could replace; any ship counts for the fleet, so the
    // flagship takes one of the two slots and the objective still gets its five ships.
    @Test
    void theFlagshipCanTakeAFleetObjectiveSlot() {
        AiTestGame test = new AiTestGame();
        test.game.setRound(4);
        Tile dock = test.nekroHome();
        carriersAway(test);
        test.units(dock, "space", test.nekro, UnitType.Dreadnought, 3);
        test.nekro.setFleetCC(5);
        test.nekro.setTg(30);
        test.game.getRevealedPublicObjectives().put("raise_fleet", 1);

        BuildPlan plan = ProductionPlanner.plan(test.game, test.nekro, dock);

        assertThat(plan.units(UnitType.Flagship)).isEqualTo(1);
        assertThat(plan.orders().stream().filter(BuildOrder::scoring)).hasSize(2);
    }

    // Building one ship with plenty of cash: the flagship is the option offered first, ahead of the cheapest destroyer
    // that is always offered too.
    @Test
    void oneShipOptionsOffersTheMostValuableShip() {
        AiTestGame test = new AiTestGame();
        test.game.setRound(4);
        Tile dock = test.nekroHome();
        carriersAway(test);
        test.nekro.setFleetCC(5);

        List<BuildOrder> options = ProductionPlanner.oneShipOptions(test.game, test.nekro, dock, 20);

        assertThat(options.getFirst().type()).isEqualTo(UnitType.Flagship);
        assertThat(options).extracting(BuildOrder::type).contains(UnitType.Destroyer);
    }

    // Eight infantry wait at home, so a carrier is planned first; the flagship then takes the one unit of production.
    // The carrier it pushed out is still offered, so a caller that prices trade goods differently can pick it.
    @Test
    void oneShipOptionsStillOffersAShipASwapPushedOut() {
        AiTestGame test = new AiTestGame();
        test.game.setRound(4);
        Tile dock = test.nekroHome();
        carriersAway(test);
        test.units(dock, "mordaiii", test.nekro, UnitType.Infantry, 8);
        test.nekro.setFleetCC(5);

        List<BuildOrder> options = ProductionPlanner.oneShipOptions(test.game, test.nekro, dock, 20);

        assertThat(options.getFirst().type()).isEqualTo(UnitType.Flagship);
        assertThat(options).extracting(BuildOrder::type).contains(UnitType.Carrier, UnitType.Destroyer);
    }

    // Late in the game, two fleet slots at a Fracture dock and no carrier there: the Fracture fleet wants a carrier and
    // dreadnoughts, and the dreadnoughts must not push out the only ship the Fracture infantry can ride in.
    @Test
    void theFractureDreadnoughtsKeepTheCarrierForItsInfantry() {
        FractureScenario late = new FractureScenario();
        Tile dock = late.board.home;
        late.board.test.game.setRound(6);
        late.ships(late.board.staging, UnitType.Carrier, 2);
        late.nekro().setFleetCC(2);
        late.nekro().setTg(30);

        BuildPlan plan = ProductionPlanner.plan(late.board.test.game, late.nekro(), dock);

        int capacity = plan.orders().stream()
                .mapToInt(order -> order.units() * BoardView.capacity(late.nekro(), order.type()))
                .sum();
        assertThat(plan.units(UnitType.Dreadnought)).isPositive();
        assertThat(plan.units(UnitType.Infantry)).isPositive();
        assertThat(capacity).isGreaterThanOrEqualTo(plan.units(UnitType.Infantry) + plan.units(UnitType.Mech));
    }

    private static void carriersAway(AiTestGame test) {
        Tile away = test.place("22", AiTestGame.neighbourOf(AiTestGame.HOME));
        test.units(away, "space", test.nekro, UnitType.Carrier, 2);
    }
}
