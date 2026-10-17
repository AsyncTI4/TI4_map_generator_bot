package ti4.ai.sol;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.eval.BoardView;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.tactical.ProductionPlanner;
import ti4.ai.tactical.ProductionPlanner.BuildOrder;
import ti4.ai.tactical.ProductionPlanner.BuildPlan;
import ti4.ai.tactical.TacticalRules;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Bellum Gloriosum is extra production capacity, not a discount: every ground force and fighter it adds is paid for
// like any other unit, and it only lifts how many units the seat may produce, by the capacity of the ships it builds.
class BellumGloriosumPlanningTest extends BaseTi4Test {

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
        sol.setFleetCC(5);
        sol.setTg(40);
    }

    private void unlockBellumGloriosum() {
        sol.addBreakthrough("solbt");
        sol.setBreakthroughUnlocked("solbt", true);
    }

    private int productionLimit() {
        return Helper.getProductionValue(sol, test.game, home, false);
    }

    private AiTurnContext context(AiPrompt... prompts) {
        return test.contextFor(sol, Set.of(), NOW, prompts);
    }

    private static boolean isGroundOrFighter(BuildOrder order) {
        return order.type() == UnitType.Infantry || order.type() == UnitType.Mech || order.type() == UnitType.Fighter;
    }

    @Test
    void theEngineChargesNormallyForUnitsProducedThroughTheBreakthrough() {
        unlockBellumGloriosum();
        sol.addSpentThing("solBtBuild_301_4");
        for (int i = 0; i < 4; i++) sol.produceUnit("gf_301_jord");
        for (int i = 0; i < 2; i++) sol.produceUnit("ff_301_space");

        assertThat(Helper.calculateCostOfProducedUnits(sol, test.game, true)).isEqualTo(3);
    }

    @Test
    void theBreakthroughLetsThePlanProduceMoreThanThePRODUCTIONLimit() {
        BuildPlan without = ProductionPlanner.plan(test.game, sol, home);
        unlockBellumGloriosum();

        BuildPlan with = ProductionPlanner.plan(test.game, sol, home);

        assertThat(without.units()).isLessThanOrEqualTo(productionLimit());
        assertThat(with.units()).isGreaterThan(productionLimit());
    }

    @Test
    void theNormalPartOfThePlanStaysWithinThePRODUCTIONLimit() {
        unlockBellumGloriosum();

        BuildPlan plan = ProductionPlanner.plan(test.game, sol, home);

        assertThat(plan.withoutBonus().units()).isLessThanOrEqualTo(productionLimit());
        assertThat(plan.orders().stream().filter(BuildOrder::bonus))
                .isNotEmpty()
                .allMatch(order -> isGroundOrFighter(order));
    }

    @Test
    void theExtraUnitsCostResourcesLikeAnyOther() {
        unlockBellumGloriosum();

        BuildPlan plan = ProductionPlanner.plan(test.game, sol, home);

        assertThat(plan.orders().stream().filter(BuildOrder::bonus))
                .isNotEmpty()
                .allMatch(order -> order.cost() > 0);
        assertThat(plan.totalCost()).isGreaterThan(plan.withoutBonus().totalCost());
    }

    @Test
    void theExtraUnitsNeverExceedTheCapacityOfTheShipsBeingBuilt() {
        unlockBellumGloriosum();

        BuildPlan plan = ProductionPlanner.plan(test.game, sol, home);

        int capacity = plan.orders().stream()
                .filter(order -> !isGroundOrFighter(order))
                .mapToInt(order -> order.units() * BoardView.capacity(sol, order.type()))
                .sum();
        int extra = plan.orders().stream()
                .filter(BuildOrder::bonus)
                .mapToInt(BuildOrder::units)
                .sum();
        assertThat(extra).isLessThanOrEqualTo(capacity);
    }

    @Test
    void withoutTheBreakthroughNothingIsExtra() {
        BuildPlan plan = ProductionPlanner.plan(test.game, sol, home);

        assertThat(plan.orders().stream().noneMatch(BuildOrder::bonus)).isTrue();
    }

    @Test
    void thePlanForTheBreakthroughBuildPaysForEveryUnit() {
        unlockBellumGloriosum();

        BuildPlan plan = ProductionPlanner.plan(test.game, sol, home);
        BuildPlan afterwards = BellumGloriosumRules.plan(test.game, sol, home, 4);

        assertThat(plan.totalCost()).isPositive();
        assertThat(afterwards.orders()).isNotEmpty().allMatch(order -> order.cost() > 0 && order.bonus());
        assertThat(afterwards.units()).isLessThanOrEqualTo(4);
    }

    @Test
    void thePlanSurvivesBeingStoredAndRestored() {
        unlockBellumGloriosum();
        BuildPlan plan = ProductionPlanner.plan(test.game, sol, home);

        assertThat(BuildPlan.decode(plan.encode())).contains(plan);
    }

    private AiPrompt placement(String source) {
        return prompt(
                "place-" + source,
                PromptSource.PUBLIC,
                NOW,
                List.of(
                        "FFCC_sol_place_2gf_jord",
                        "FFCC_sol_place_infantry_jord",
                        "FFCC_sol_deleteButtons_" + source + "_301"),
                List.of("2", "1", "Done Producing Units"));
    }

    private BuildPlan onlyExtraInfantry() {
        return new BuildPlan(
                List.of(new BuildOrder("2gf", "jord", UnitType.Infantry, 2, 1.0, 0.5, false, false, true)));
    }

    private static String pressed(Optional<AiDecision> decision) {
        return decision.map(AiTestGame::pressedId).orElse("");
    }

    @Test
    void theNormalBuildLeavesTheExtraUnitsToTheBreakthroughBuild() {
        AiTurnContext context = context(placement("tacticalAction"));

        Optional<AiDecision> normal = TacticalRules.placeUnits(
                context, context.prompts(), "tacticalAction", "301", Optional.of(onlyExtraInfantry()));

        assertThat(pressed(normal)).isEqualTo("FFCC_sol_deleteButtons_tacticalAction_301");
    }

    @Test
    void theBreakthroughBuildPlacesTheExtraUnits() {
        AiTurnContext context = context(placement(ProductionPlanner.BELLUM_SOURCE));

        Optional<AiDecision> extra = TacticalRules.placeUnits(
                context, context.prompts(), ProductionPlanner.BELLUM_SOURCE, "301", Optional.of(onlyExtraInfantry()));

        assertThat(pressed(extra)).isEqualTo("FFCC_sol_place_2gf_jord");
    }
}
