package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ti4.ai.tactical.TacticalPlan.Kind;

// Live game aiplay1, round 1: Sardakk held 3 tactical tokens and had no move worth one, so the planner fell back on
// the cheaper plans it accepts while tokens outnumber worthwhile moves, and spent a token building 2 infantry at home
// with its last resource (a plan worth 0.5). That fallback is meant for positional moves that set up next round; a
// minor build is not worth a token that carries over.
class SpareTokenBudgetTest {

    private static final double MINOR = 0.5;

    private static TacticalPlan plan(Kind kind, String target, double score) {
        return new TacticalPlan(kind, target, List.of(), Map.of(), score);
    }

    @Test
    void spareTokensStillTakeAPositionalMove() {
        TacticalPlan advance = plan(Kind.POSITION, "203", MINOR);

        assertThat(TacticalPlanner.withinTokenBudget(List.of(advance), 3)).containsExactly(advance);
    }

    @Test
    void spareTokensAreNotSpentOnAMinorBuild() {
        TacticalPlan build = plan(Kind.PRODUCE, "304", MINOR);

        assertThat(TacticalPlanner.withinTokenBudget(List.of(build), 3)).isEmpty();
    }

    @Test
    void aBuildWorthATokenIsStillTaken() {
        TacticalPlan build = plan(Kind.PRODUCE, "304", TacticalPlanner.MIN_SCORE + 1);

        assertThat(TacticalPlanner.withinTokenBudget(List.of(build), 3)).containsExactly(build);
    }
}
