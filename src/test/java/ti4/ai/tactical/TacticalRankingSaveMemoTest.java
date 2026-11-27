package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.brain.DecisionMemo;
import ti4.ai.brain.Reentry;
import ti4.ai.brain.SaveMemo;
import ti4.ai.eval.MoveEnablers;
import ti4.ai.eval.MoveEnablers.Enabler;
import ti4.ai.eval.MovementGraph;
import ti4.game.Player;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// The tactical ranking is kept across decisions until the save changes. A ranking worked out under a movement what-if
// or a re-entry guard answers a different question, so it must not be handed to a later plain decision. A cache hit
// hands back the very plans that were kept (the caller filters them into a new list), so plan identity tells a reused
// ranking from a fresh one.
class TacticalRankingSaveMemoTest extends BaseTi4Test {

    private final DefenseScenario board = new DefenseScenario();

    // A carrier and a destroyer at home with an objective to reach out for, so the ranking holds plans.
    {
        board.reveal("deep_space");
        board.test.units(board.home, "space", board.test.nekro, UnitType.Carrier, 1);
        board.test.units(board.home, "space", board.test.nekro, UnitType.Destroyer, 1);
    }

    @BeforeEach
    @AfterEach
    void forget() {
        SaveMemo.forget(board.test.game.getName());
    }

    private Player nekro() {
        return board.test.nekro;
    }

    private List<TacticalPlan> ranked() {
        return TacticalPlanner.ranked(board.test.game, nekro());
    }

    private static List<TacticalPlan> decide(Supplier<List<TacticalPlan>> ranking) {
        try (DecisionMemo.Open decision = DecisionMemo.open()) {
            return ranking.get();
        }
    }

    private static boolean reused(List<TacticalPlan> later, List<TacticalPlan> earlier) {
        if (later.size() != earlier.size()) return false;
        for (int i = 0; i < later.size(); i++) {
            if (later.get(i) != earlier.get(i)) return false;
        }
        return true;
    }

    @Test
    void aPlainRankingIsReusedByALaterDecisionOnTheSameSave() {
        List<TacticalPlan> first = decide(this::ranked);

        assertThat(first).isNotEmpty();
        assertThat(reused(decide(this::ranked), first)).isTrue();
    }

    @Test
    void aRankingWithAnAssumedLostStarChartIsNotServedToALaterPlainDecision() {
        List<TacticalPlan> assumed =
                decide(() -> MoveEnablers.assuming(nekro(), Enabler.LOST_STAR_CHART, this::ranked));
        List<TacticalPlan> plain = decide(this::ranked);

        assertThat(assumed).isNotEmpty();
        assertThat(reused(plain, assumed)).isFalse();
        assertThat(reused(decide(() -> MoveEnablers.assuming(nekro(), Enabler.LOST_STAR_CHART, this::ranked)), assumed))
                .isTrue();
    }

    @Test
    void aRankingWithAssumedAntimassDeflectorsIsNotServedToALaterPlainDecision() {
        List<TacticalPlan> assumed = decide(() -> MovementGraph.withAntimassDeflectors(this::ranked));

        assertThat(assumed).isNotEmpty();
        assertThat(reused(decide(this::ranked), assumed)).isFalse();
    }

    // Holding a re-entry guard the ranking never runs into changes nothing, so the ranking is still kept. Skipping the
    // memo whenever any guard was held dropped all 623 reuses of a seeded self-play game.
    @Test
    void aRankingAskedForWhileAnUnrelatedGuardIsHeldIsStillReused() {
        List<TacticalPlan> guarded = decide(() -> holding("test.unrelated", this::ranked));

        assertThat(guarded).isNotEmpty();
        assertThat(reused(decide(this::ranked), guarded)).isTrue();
    }

    // Every ranking looks at whether a plan estimate is in progress (the technology follow reserve depends on it). A
    // ranking kept on a plain decision is not handed to a caller in the middle of such an estimate. The guard name is
    // the one TokenPurchase holds.
    @Test
    void aKeptRankingIsNotServedInTheMiddleOfAPlanEstimate() {
        List<TacticalPlan> plain = decide(this::ranked);

        assertThat(plain).isNotEmpty();
        assertThat(reused(decide(() -> holding("strategy.estimatingPlans", this::ranked)), plain))
                .isFalse();
    }

    private static <T> T holding(String guard, Supplier<T> computation) {
        Reentry.enter(guard);
        try {
            return computation.get();
        } finally {
            Reentry.exit(guard);
        }
    }
}
