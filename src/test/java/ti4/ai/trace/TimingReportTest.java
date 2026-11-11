package ti4.ai.trace;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.trace.ThinkingProfile.SpanTotal;

class TimingReportTest {

    private static final long MILLI = 1_000_000L;
    private static final long SECOND = 1000 * MILLI;

    @BeforeEach
    void enable() {
        TimingReport.reset();
        TimingReport.enable(true);
    }

    @AfterEach
    void restore() {
        TimingReport.reset();
        TimingReport.enable(Boolean.getBoolean(TimingReport.PROPERTY));
    }

    @Test
    void theReportRanksSpansAndDecidingRulesAndCountsDecisionsOverTheLiveLimit() {
        TimingReport.decidedBy("TradeRules.answerOffers");
        TimingReport.recordDecision(profile(30 * SECOND, span("tactical.best", 20 * SECOND)), 25 * SECOND, "press");
        TimingReport.decidedBy("NekroBrain.takeTurn");
        TimingReport.recordDecision(profile(5 * MILLI, span("cards.keepValue", 4 * MILLI)), 5 * MILLI, "idle");
        TimingReport.recordOutside("ai.load", 40 * MILLI);

        List<String> lines = TimingReport.lines();

        assertThat(lines.getFirst()).startsWith("TIMING decisions 2:");
        assertThat(lines).anyMatch(line -> line.contains("over the live 20 s limit: 1 (50.00%) by wall, 1 (50.00%)"));
        assertThat(lines).anyMatch(line -> line.contains("TradeRules.answerOffers: 1, 30.0 s"));
        assertThat(lines).anyMatch(line -> line.contains("tactical.best: 1 calls, self 20.0 s"));
        assertThat(lines).anyMatch(line -> line.contains("ai.load: 1, 40 ms"));
        int best = indexOf(lines, "tactical.best");
        int keep = indexOf(lines, "cards.keepValue");
        assertThat(best).isLessThan(keep);
    }

    // The rule a decision came from is read once: a later decision with no deciding rule is not charged to it.
    @Test
    void aDecidingRuleCountsForOneDecisionOnly() {
        TimingReport.decidedBy("TradeRules.answerOffers");
        TimingReport.recordDecision(profile(MILLI), MILLI, "press");
        TimingReport.recordDecision(profile(MILLI), MILLI, "idle");

        assertThat(TimingReport.lines()).anyMatch(line -> line.contains("(no rule): 1,"));
    }

    @Test
    void decisionsAreSplitByWhatChangedSinceTheSeatLastDecided() {
        TimingReport.recordDecision(profile(30 * MILLI), 30 * MILLI, "idle", "unchanged");
        TimingReport.recordDecision(profile(10 * MILLI), 10 * MILLI, "idle", "saved");
        TimingReport.recordDecision(profile(MILLI), MILLI, "press");

        List<String> lines = TimingReport.lines();

        assertThat(lines).anyMatch(line -> line.contains("idle, unchanged: 1, 30 ms (73.2%)"));
        assertThat(indexOf(lines, "idle, unchanged")).isLessThan(indexOf(lines, "idle, saved"));
        assertThat(lines).noneMatch(line -> line.contains("press, "));
    }

    @Test
    void nothingIsRecordedWhileDisabled() {
        TimingReport.enable(false);
        TimingReport.recordDecision(profile(MILLI), MILLI, "press");
        TimingReport.recordOutside("ai.load", MILLI);

        assertThat(TimingReport.lines()).singleElement().asString().contains("no decisions recorded");
    }

    @Test
    void percentilesAndBucketsReadTheSortedTimes() {
        long[] sorted = {MILLI, 5 * MILLI, 50 * MILLI, 3 * SECOND, 70 * SECOND};

        assertThat(TimingReport.percentiles(sorted)).startsWith("p50 50 ms, p90 70.0 s");
        assertThat(TimingReport.buckets(sorted))
                .isEqualTo("<10ms 2, <100ms 1, <500ms 0, <2s 0, <5s 1, <20s 0, <60s 0, >=60s 1");
    }

    private static ThinkingProfile profile(long totalNanos, SpanTotal... spans) {
        return new ThinkingProfile(totalNanos, List.of(spans));
    }

    private static SpanTotal span(String name, long nanos) {
        return new SpanTotal(name, 1, nanos, nanos, 1, nanos);
    }

    private static int indexOf(List<String> lines, String text) {
        for (int i = 0; i < lines.size(); i++) if (lines.get(i).contains(text)) return i;
        return -1;
    }
}
