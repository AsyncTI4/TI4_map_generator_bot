package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.DecisionMemo;
import ti4.ai.eval.MoveEnablers;
import ti4.ai.eval.MovementCard;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.testUtils.BaseTi4Test;

// The planner runs one extra pass per held movement card (Flank Speed, Lost Star Chart, Nav Suite) and keeps the
// plans that only that card makes reachable, at the card's cost. Here the only way to the free planets two systems
// out runs through an asteroid field: a carrier with Gravity Drive has the range but cannot enter the field without
// Antimass Deflectors, unless the seat plays Nav Suite.
class MovementCardPlanTest extends BaseTi4Test {

    private AiTestGame test;
    private String farTarget;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekro.setTacticalCC(3);
        test.nekro.addTech("gd");
        Tile home = test.nekroHome();
        String neighbour = AiTestGame.neighbourOf(AiTestGame.HOME);
        test.place("44", neighbour);
        farTarget = twoSystemsFrom(neighbour);
        test.place("27", farTarget);
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 3);
    }

    @Test
    void anAsteroidFieldBlocksThePlanWithoutNavSuite() {
        assertThat(planFor(farTarget)).isEmpty();
    }

    @Test
    void navSuiteMakesThePlanThroughTheAsteroidFieldAndOnlyItNeedsTheCard() {
        test.nekro.setActionCard("nav_suite", 403);

        Optional<TacticalPlan> plan = planFor(farTarget);

        assertThat(plan).isPresent();
        assertThat(TacticalPlanner.needsCard(test.game, test.nekro, plan.get(), MovementCard.NAV_SUITE))
                .isTrue();
        assertThat(TacticalPlanner.needsCard(test.game, test.nekro, plan.get(), MovementCard.LOST_STAR_CHART))
                .isFalse();
        assertThat(TacticalPlanner.nextMovementCard(test.game, test.nekro, plan.get()))
                .contains(MovementCard.NAV_SUITE);
    }

    // The card's pass must not leak into the decision's shared ranking or the movement memos.
    @Test
    void theNavSuitePassKeepsTheRealWorldApart() {
        test.nekro.setActionCard("nav_suite", 403);
        try (DecisionMemo.Open ignored = DecisionMemo.open()) {
            double priced = planFor(farTarget).orElseThrow().score();
            double assumed = MoveEnablers.assuming(
                    test.nekro,
                    MoveEnablers.Enabler.NAV_SUITE,
                    () -> planFor(farTarget).orElseThrow().score());

            assertThat(assumed).isEqualTo(priced + 1.5, org.assertj.core.data.Offset.offset(0.001));
        }
    }

    private Optional<TacticalPlan> planFor(String position) {
        return TacticalPlanner.ranked(test.game, test.nekro).stream()
                .filter(plan -> plan.target().equals(position))
                .findFirst();
    }

    private static String twoSystemsFrom(String neighbour) {
        List<String> nearHome = MapPositionMapper.getAdjacentTilePositions(AiTestGame.HOME);
        return MapPositionMapper.getAdjacentTilePositions(neighbour).stream()
                .filter(position ->
                        !"x".equals(position) && !AiTestGame.HOME.equals(position) && !nearHome.contains(position))
                .findFirst()
                .orElseThrow();
    }
}
