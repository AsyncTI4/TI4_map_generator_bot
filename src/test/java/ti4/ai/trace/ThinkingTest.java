package ti4.ai.trace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import ti4.ai.trace.ThinkingProfile.SpanTotal;

class ThinkingTest {

    private static final ThinkingLimits LIMITS = new ThinkingLimits(0, 3, 10, 5);

    private final AtomicLong clock = new AtomicLong();

    // Outside a decision every span is a shared no-op, so planners can be called from tests and tools untouched.
    @Test
    void spansArePassThroughsWithoutADecision() {
        assertThatCode(() -> nest(500)).doesNotThrowAnyException();
    }

    // Two planners that value each other never return. The cycle is cut at the re-entry limit, long before the stack
    // runs out, and the trail shows the cycle.
    @Test
    void mutualRecursionIsCutAtTheReentryLimitWithTheCycleInTheTrail() {
        try (Thinking.Decision decision = Thinking.open(LIMITS, clock::get)) {
            assertThatThrownBy(() -> ping(0)).isInstanceOfSatisfying(ThinkingAborted.class, aborted -> {
                assertThat(aborted.reason()).contains("ping").contains("re-entered itself 3 deep");
                assertThat(aborted.trail()).isEqualTo("ping > pong > ping > pong > ping > pong > ping");
                assertThat(aborted.innermost(2)).isEqualTo("... > pong > ping");
            });
            assertThat(decision.profile().spans()).extracting(SpanTotal::name).contains("ping", "pong");
        }
    }

    // The abort names the memo keys of the open spans, so a cycle that re-enters the same key is told apart from one
    // that walks different seats or tiles; the trail folds the repeating cycle into one group.
    @Test
    void anAbortCarriesTheKeysOfItsOpenSpansAndFoldsTheCycle() {
        try (Thinking.Decision decision = Thinking.open(LIMITS, clock::get)) {
            assertThatThrownBy(() -> {
                        try (Thinking.Span outer = Thinking.enter("deal")) {
                            keyedPing(0);
                        }
                    })
                    .isInstanceOfSatisfying(ThinkingAborted.class, aborted -> {
                        assertThat(aborted.keys()).containsExactly("ping sol", "pong tile 28");
                        assertThat(aborted.compactTrail()).isEqualTo("deal > ping > (pong > ping) x3");
                    });
        }
    }

    @Test
    void aTrailWithoutARepeatIsLeftAsItIs() {
        ThinkingAborted aborted = new ThinkingAborted("budget", java.util.List.of("a", "b", "c"));

        assertThat(aborted.compactTrail()).isEqualTo("a > b > c");
        assertThat(aborted.keys()).isEmpty();
    }

    @Test
    void nestingDeeperThanTheDepthLimitIsCut() {
        try (Thinking.Decision decision = Thinking.open(LIMITS, clock::get)) {
            assertThatThrownBy(() -> nestDistinct(0))
                    .isInstanceOfSatisfying(
                            ThinkingAborted.class,
                            aborted -> assertThat(aborted.reason()).isEqualTo("it nested 10 spans deep"));
        }
    }

    @Test
    void callingOneSpanTooOftenInADecisionIsCut() {
        try (Thinking.Decision decision = Thinking.open(LIMITS, clock::get)) {
            assertThatThrownBy(() -> {
                        for (int i = 0; i < 10; i++) Thinking.enter("busy").close();
                    })
                    .isInstanceOfSatisfying(
                            ThinkingAborted.class,
                            aborted -> assertThat(aborted.reason()).isEqualTo("busy was called more than 5 times"));
        }
    }

    @Test
    void thinkingPastTheBudgetIsCutAtTheNextSpan() {
        ThinkingLimits budgeted = new ThinkingLimits(1_000_000L, 3, 10, 100);
        try (Thinking.Decision decision = Thinking.open(budgeted, clock::get)) {
            try (Thinking.Span outer = Thinking.enter("slow")) {
                clock.addAndGet(2_000_000L);
                assertThatThrownBy(() -> Thinking.enter("next"))
                        .isInstanceOfSatisfying(ThinkingAborted.class, aborted -> {
                            assertThat(aborted.reason()).isEqualTo("it thought for more than 1 ms");
                            assertThat(aborted.trail()).isEqualTo("slow > next");
                        });
            }
        }
    }

    // A rule that swallowed the first abort would otherwise carry on computing. Every later span in the decision
    // throws the same abort again.
    @Test
    void anAbortIsStickyForTheRestOfTheDecision() {
        try (Thinking.Decision decision = Thinking.open(LIMITS, clock::get)) {
            ThinkingAborted first = abortedByRecursion();
            assertThatThrownBy(() -> Thinking.enter("elsewhere")).isSameAs(first);
        }
    }

    @Test
    void closingTheDecisionRemovesItsLimitsAndRestoresAnOuterOne() {
        try (Thinking.Decision outer = Thinking.open(LIMITS, clock::get)) {
            try (Thinking.Decision inner = Thinking.open(ThinkingLimits.unlimited(), clock::get)) {
                assertThatCode(() -> nest(50)).doesNotThrowAnyException();
            }
            assertThatThrownBy(() -> ping(0)).isInstanceOf(ThinkingAborted.class);
        }
        assertThatCode(() -> ping(0)).doesNotThrowAnyException();
    }

    @Test
    void theProfileSplitsSelfTimeFromTimeSpentInNestedSpans() {
        try (Thinking.Decision decision = Thinking.open(ThinkingLimits.unlimited(), clock::get)) {
            try (Thinking.Span outer = Thinking.enter("outer")) {
                clock.addAndGet(10);
                for (int i = 0; i < 2; i++) {
                    try (Thinking.Span inner = Thinking.enter("inner")) {
                        clock.addAndGet(30);
                    }
                }
                clock.addAndGet(5);
            }
            ThinkingProfile profile = decision.profile();
            assertThat(profile.totalNanos()).isEqualTo(75);
            assertThat(profile.spans())
                    .containsExactly(new SpanTotal("inner", 2, 60, 60, 1), new SpanTotal("outer", 1, 75, 15, 1));
        }
    }

    @Test
    void recursionCountsItsTotalTimeOnceAndRecordsHowDeepItNested() {
        try (Thinking.Decision decision = Thinking.open(ThinkingLimits.unlimited(), clock::get)) {
            try (Thinking.Span outer = Thinking.enter("loop")) {
                clock.addAndGet(10);
                try (Thinking.Span again = Thinking.enter("loop")) {
                    clock.addAndGet(20);
                }
            }
            assertThat(decision.profile().spans()).containsExactly(new SpanTotal("loop", 2, 30, 30, 2));
        }
    }

    // After a real stack overflow the spans have already unwound, so the trail comes from the AI frames of the error.
    @Test
    void aStackOverflowBecomesAnAbortWhoseTrailNamesTheCyclingMethods() {
        StackOverflowError overflow = overflow();

        ThinkingAborted aborted = ThinkingAborted.stackOverflow(overflow);

        assertThat(aborted.reason()).isEqualTo("it ran out of stack");
        assertThat(aborted.spans()).isNotEmpty().hasSizeLessThanOrEqualTo(8);
        assertThat(aborted.trail()).contains("ThinkingTest$Cycle.");
    }

    private ThinkingAborted abortedByRecursion() {
        try {
            ping(0);
        } catch (ThinkingAborted aborted) {
            return aborted;
        }
        throw new AssertionError("the recursion was not cut");
    }

    private void ping(int depth) {
        try (Thinking.Span span = Thinking.enter("ping")) {
            if (depth < 60) pong(depth + 1);
        }
    }

    private void pong(int depth) {
        try (Thinking.Span span = Thinking.enter("pong")) {
            ping(depth + 1);
        }
    }

    private void keyedPing(int depth) {
        try (Thinking.Span span = Thinking.enter("ping", () -> "sol")) {
            try (Thinking.Span inner = Thinking.enter("pong", () -> "tile 28")) {
                keyedPing(depth + 1);
            }
        }
    }

    private void nest(int remaining) {
        if (remaining == 0) return;
        try (Thinking.Span span = Thinking.enter("same")) {
            nest(remaining - 1);
        }
    }

    private void nestDistinct(int level) {
        try (Thinking.Span span = Thinking.enter("level" + level)) {
            nestDistinct(level + 1);
        }
    }

    private static StackOverflowError overflow() {
        try {
            new Cycle().first();
        } catch (StackOverflowError error) {
            return error;
        }
        throw new AssertionError("no overflow");
    }

    private static final class Cycle {
        private int calls;

        private void first() {
            calls++;
            second();
        }

        private void second() {
            calls++;
            first();
        }
    }
}
