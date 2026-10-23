package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.image.PositionMapper;
import ti4.testUtils.BaseTi4Test;

// Flank Speed is played after the system is activated and gives each of the seat's ships +1 move for that tactical
// action. A carrier (move 1) with infantry on the home planet reaches the neighbour, but the free planets two systems
// away are out of range unless the seat holds the card.
class FlankSpeedPlanTest extends BaseTi4Test {

    private AiTestGame test;
    private String neighbour;
    private String farTarget;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekro.setTacticalCC(3);
        Tile home = test.nekroHome();
        neighbour = AiTestGame.neighbourOf(AiTestGame.HOME);
        test.place("47", neighbour);
        farTarget = twoSystemsFromHome();
        test.place("27", farTarget);
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 3);
    }

    @Test
    void aTargetOneJumpOutOfRangeIsNotPlannedWithoutTheCard() {
        assertThat(planFor(farTarget)).isEmpty();
    }

    @Test
    void theCardMakesTheTargetOneJumpOutOfRangeAPlan() {
        test.nekro.setActionCard("fs1", 401);

        Optional<TacticalPlan> plan = planFor(farTarget);

        assertThat(plan).isPresent();
        assertThat(TacticalPlanner.needsFlankSpeed(test.game, test.nekro, plan.get()))
                .isTrue();
    }

    @Test
    void onlyTheTargetOutOfRangeNeedsTheCard() {
        test.nekro.setActionCard("fs1", 401);

        List<TacticalPlan> ranked = TacticalPlanner.ranked(test.game, test.nekro);

        assertThat(ranked).extracting(TacticalPlan::target).contains(farTarget);
        assertThat(ranked.stream()
                        .filter(plan -> TacticalPlanner.needsFlankSpeed(test.game, test.nekro, plan))
                        .map(TacticalPlan::target))
                .containsOnly(farTarget);
    }

    @Test
    void theBoostedPlanCarriesTheMarginForTheCard() {
        test.nekro.setActionCard("fs1", 401);
        double boosted = planFor(farTarget).orElseThrow().score();
        test.nekro.removeActionCard(401);
        test.nekro.setActionCard("fs1", 401);

        double unboosted = ti4.ai.eval.MoveBoost.withOneMore(
                test.nekro,
                () -> TacticalPlanner.forTarget(test.game, test.nekro, farTarget)
                        .orElseThrow()
                        .score());

        assertThat(boosted).isEqualTo(unboosted - 1.5, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void theSharedRankingOfADecisionKeepsTheBoostedWorldApart() {
        test.nekro.setActionCard("fs1", 401);

        try (ti4.ai.brain.DecisionMemo.Open ignored = ti4.ai.brain.DecisionMemo.open()) {
            double normal = planFor(farTarget).orElseThrow().score();
            double faster = ti4.ai.eval.MoveBoost.withOneMore(
                    test.nekro, () -> planFor(farTarget).orElseThrow().score());

            assertThat(faster).isEqualTo(normal + 1.5, org.assertj.core.data.Offset.offset(0.001));
        }
    }

    private Optional<TacticalPlan> planFor(String position) {
        return TacticalPlanner.ranked(test.game, test.nekro).stream()
                .filter(plan -> plan.target().equals(position))
                .findFirst();
    }

    private String twoSystemsFromHome() {
        List<String> nearHome = PositionMapper.getAdjacentTilePositions(AiTestGame.HOME);
        return PositionMapper.getAdjacentTilePositions(neighbour).stream()
                .filter(position ->
                        !"x".equals(position) && !AiTestGame.HOME.equals(position) && !nearHome.contains(position))
                .findFirst()
                .orElseThrow();
    }
}
