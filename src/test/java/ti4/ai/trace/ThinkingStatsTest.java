package ti4.ai.trace;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.ai.trace.ThinkingProfile.SpanTotal;

class ThinkingStatsTest {

    private static final long MILLI = 1_000_000L;

    // The table ranks spans by the time spent in the span itself, so a caller does not hide the callee that is slow.
    @Test
    void spansAreRankedBySelfTimeAndAddedUpAcrossDecisions() {
        ThinkingStats stats = new ThinkingStats();
        stats.record(profile(300, span("outer", 1, 300, 20, 1), span("inner", 4, 280, 280, 2)), "sol", "press", false);
        stats.record(profile(100, span("outer", 1, 100, 60, 1)), "sol", "idle", false);

        List<String> lines = stats.lines(5, 0);

        assertThat(lines.getFirst())
                .isEqualTo("2 decisions (0 cut short), 400 ms in all, 200 ms on average, 300 ms longest");
        assertThat(lines.subList(1, lines.size()))
                .containsExactly(
                        "inner: 4 calls, self 280 ms, total 280 ms, nested 2 deep",
                        "outer: 2 calls, self 80 ms, total 400 ms");
    }

    @Test
    void onlyTheSlowestDecisionsAreKeptWithTheirTopSpans() {
        ThinkingStats stats = new ThinkingStats();
        for (int i = 1; i <= 8; i++) {
            stats.record(profile(i * 100, span("planner", 1, i * 100, i * 100, 1)), "faction" + i, "idle", false);
        }

        List<String> lines = stats.lines(0, 10);

        assertThat(lines.subList(1, lines.size()))
                .containsExactly(
                        "slow: faction8 idle 800 ms in planner 800 ms",
                        "slow: faction7 idle 700 ms in planner 700 ms",
                        "slow: faction6 idle 600 ms in planner 600 ms",
                        "slow: faction5 idle 500 ms in planner 500 ms",
                        "slow: faction4 idle 400 ms in planner 400 ms");
    }

    @Test
    void timeSpentOutsideTheDecisionIsKeptUnderItsOwnName() {
        ThinkingStats stats = new ThinkingStats();
        stats.recordOutside("ai.perceive", 40 * MILLI);
        stats.recordOutside("ai.perceive", 2500 * MILLI);

        assertThat(stats.lines(3, 0)).contains("ai.perceive: 2 calls, self 2.5 s, total 2.5 s");
    }

    @Test
    void cutShortDecisionsAreCounted() {
        ThinkingStats stats = new ThinkingStats();
        stats.record(profile(5, span("a", 1, 5, 5, 1)), "sol", "cut short", true);

        assertThat(stats.cutShort()).isEqualTo(1);
        assertThat(stats.decisions()).isEqualTo(1);
    }

    private static ThinkingProfile profile(long millis, SpanTotal... spans) {
        return new ThinkingProfile(millis * MILLI, List.of(spans));
    }

    private static SpanTotal span(String name, long calls, long totalMillis, long selfMillis, int reentry) {
        return new SpanTotal(name, calls, totalMillis * MILLI, selfMillis * MILLI, reentry);
    }
}
