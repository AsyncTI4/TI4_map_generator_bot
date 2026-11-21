package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import ti4.ai.tactical.TacticalPlan;

class PlanInputsFreeingTest {

    // Seed 9508 stalled a seat for good: ranking a freed position valued an action card that lifts a command
    // token, which asked for a freed ranking on the same map from inside ConcurrentHashMap.computeIfAbsent and
    // threw "Recursive update" on every tick.
    @Test
    void aRankingThatAsksForTheSamePositionAgainGetsNoPlansInsteadOfThrowing() {
        Map<String, List<TacticalPlan>> freeing = new ConcurrentHashMap<>();
        List<TacticalPlan> outer = new ArrayList<>();
        AtomicReference<List<TacticalPlan>> nested = new AtomicReference<>();

        List<TacticalPlan> ranked = PlanInputs.rememberedFreeing(freeing, "301", position -> {
            nested.set(PlanInputs.rememberedFreeing(freeing, position, again -> new ArrayList<>()));
            return outer;
        });

        assertThat(ranked).isSameAs(outer);
        assertThat(nested.get()).isEmpty();
        assertThat(freeing).containsOnlyKeys("301");
    }

    @Test
    void aNestedRankingOfAnotherPositionIsNotComputedOrRemembered() {
        Map<String, List<TacticalPlan>> freeing = new ConcurrentHashMap<>();
        AtomicReference<List<TacticalPlan>> nested = new AtomicReference<>();

        PlanInputs.rememberedFreeing(freeing, "301", position -> {
            nested.set(PlanInputs.rememberedFreeing(freeing, "205", again -> {
                throw new AssertionError("a nested freed ranking must not run");
            }));
            return new ArrayList<>();
        });

        assertThat(nested.get()).isEmpty();
        assertThat(freeing).containsOnlyKeys("301");
    }

    @Test
    void aRememberedRankingIsReturnedWithoutRankingAgain() {
        Map<String, List<TacticalPlan>> freeing = new ConcurrentHashMap<>();
        List<TacticalPlan> first = PlanInputs.rememberedFreeing(freeing, "301", position -> new ArrayList<>());

        List<TacticalPlan> second = PlanInputs.rememberedFreeing(freeing, "301", position -> {
            throw new AssertionError("ranked twice");
        });

        assertThat(second).isSameAs(first);
    }

    @Test
    void aRankingThatThrowsLeavesLaterRankingsFree() {
        Map<String, List<TacticalPlan>> freeing = new ConcurrentHashMap<>();
        try {
            PlanInputs.rememberedFreeing(freeing, "301", position -> {
                throw new IllegalStateException("boom");
            });
        } catch (IllegalStateException expected) {
            // the guard must reset even when the ranking fails
        }
        List<TacticalPlan> later = new ArrayList<>();

        assertThat(PlanInputs.rememberedFreeing(freeing, "205", position -> later))
                .isSameAs(later);
    }
}
