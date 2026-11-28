package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.ValueScale;
import ti4.ai.tactical.ProductionPlanner.BuildOrder;
import ti4.ai.tactical.ProductionPlanner.BuildPlan;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// LRR 14.1 (docs/rules/reference/blockaded.md): a blockaded unit cannot produce ships, but it can still produce ground
// forces. A Sardakk fleet sitting in Nekro's home system blockades the space dock on Mordai II; the planner should
// still garrison the planet with infantry or mechs rather than build nothing.
class BlockadedProductionTest extends BaseTi4Test {

    private static final String HOME_PLANET = "mordaiii";

    private DefenseScenario board;

    @BeforeEach
    void setUp() {
        board = new DefenseScenario();
        board.test.nekro.setTg(6);
        board.reveal("conquer");
        board.test.units(board.home, "space", board.sardakk, UnitType.Carrier, 1);
        board.test.units(board.home, "space", board.sardakk, UnitType.Cruiser, 2);
        board.test.units(board.home, "space", board.sardakk, UnitType.Infantry, 4);
        board.test.units(board.home, HOME_PLANET, board.test.nekro, UnitType.Infantry, 1);
    }

    private BuildPlan plan() {
        return ProductionPlanner.plan(board.test.game, board.test.nekro, board.home);
    }

    @Test
    void blockadedDockStillBuildsGroundForcesOnItsPlanet() {
        assertThat(BoardView.hasEnemyShips(board.test.game, board.test.nekro, board.home))
                .isTrue();

        BuildPlan plan = plan();

        assertThat(plan.orders()).isNotEmpty();
        assertThat(plan.orders())
                .allSatisfy(order -> assertThat(BoardView.GROUND_FORCES).contains(order.type()));
        assertThat(plan.orders())
                .allSatisfy(order -> assertThat(order.location()).isEqualTo(HOME_PLANET));
        assertThat(plan.orders())
                .allSatisfy(order -> assertThat(order.defense()).isTrue());
    }

    // Fighters are ships too, so the blockaded dock never adds them even though it normally fills with fighters.
    @Test
    void blockadedDockBuildsNoShipsOrFighters() {
        assertThat(plan().orders().stream().map(BuildOrder::type))
                .doesNotContain(UnitType.Fighter, UnitType.Carrier, UnitType.Cruiser, UnitType.Destroyer);
    }

    @Test
    void withTheExperimentOffABlockadedDockBuildsNothing() {
        AiExperiments.disable(board.test.nekro, EnumSet.of(Experiment.BLOCKADE_GROUND));

        assertThat(plan().isEmpty()).isTrue();
    }

    // A blockading carrier with two infantry against one defending infantry is a small threat: the planner buys only
    // the ground forces whose defence gain pays for their resources, which here is a single infantry, although the
    // dock could produce several more and Nekro has 20 trade goods.
    @Test
    void aSmallThreatBuysASmallGarrison() {
        DefenseScenario small = new DefenseScenario();
        small.test.nekro.setTg(20);
        small.test.units(small.home, "space", small.sardakk, UnitType.Carrier, 1);
        small.test.units(small.home, "space", small.sardakk, UnitType.Infantry, 2);
        small.test.units(small.home, HOME_PLANET, small.test.nekro, UnitType.Infantry, 1);

        BuildPlan plan = ProductionPlanner.plan(small.test.game, small.test.nekro, small.home);
        int capacity = Helper.getProductionValue(small.test.nekro, small.test.game, small.home, false);

        assertThat(plan.units()).isEqualTo(1);
        assertThat(plan.units()).isLessThan(capacity);
    }

    // The bigger landing in setUp (four infantry) earns a bigger garrison, but still not a full dock: every order must
    // be worth at least its resources (0.8 trade goods each, ValueScale.RESOURCE) in reduced defence cost.
    @Test
    void everyBlockadedOrderPaysForItsResources() {
        BuildPlan plan = plan();
        int capacity = Helper.getProductionValue(board.test.nekro, board.test.game, board.home, false);

        assertThat(plan.units()).isBetween(2, capacity - 1);
        assertThat(plan.orders())
                .allSatisfy(
                        order -> assertThat(order.value()).isGreaterThanOrEqualTo(ValueScale.RESOURCE * order.cost()));
    }

    // A lone blockading destroyer carries no ground forces, so nothing threatens the planet and nothing is built.
    @Test
    void noThreatBuildsNothing() {
        DefenseScenario calm = new DefenseScenario();
        calm.test.nekro.setTg(20);
        calm.test.units(calm.home, "space", calm.sardakk, UnitType.Destroyer, 1);
        calm.test.units(calm.home, HOME_PLANET, calm.test.nekro, UnitType.Infantry, 1);

        assertThat(ProductionPlanner.plan(calm.test.game, calm.test.nekro, calm.home)
                        .isEmpty())
                .isTrue();
    }

    // A lone destroyer cannot land troops; whatever the blockaded dock buys is still ground forces only.
    @Test
    void blockadedDockFacingNoLandingStillBuildsOnlyGroundForces() {
        DefenseScenario calm = new DefenseScenario();
        calm.test.nekro.setTg(6);
        calm.test.units(calm.home, "space", calm.sardakk, UnitType.Destroyer, 1);
        calm.test.units(calm.home, HOME_PLANET, calm.test.nekro, UnitType.Infantry, 3);
        calm.test.units(calm.home, HOME_PLANET, calm.test.nekro, UnitType.Mech, 1);

        BuildPlan plan = ProductionPlanner.plan(calm.test.game, calm.test.nekro, calm.home);

        assertThat(plan.orders())
                .allSatisfy(order -> assertThat(BoardView.GROUND_FORCES).contains(order.type()));
    }
}
