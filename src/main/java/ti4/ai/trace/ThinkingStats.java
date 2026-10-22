package ti4.ai.trace;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import ti4.ai.trace.ThinkingProfile.SpanTotal;

public final class ThinkingStats {

    private static final int SLOWEST_KEPT = 5;
    private static final int TOP_SPANS_PER_DECISION = 3;
    private static final long NANOS_PER_MILLI = 1_000_000L;

    private static final class Total {
        private long calls;
        private long totalNanos;
        private long selfNanos;
        private int maxReentry;
    }

    private record Slow(String faction, String outcome, long nanos, String top) {}

    private final Map<String, Total> spans = new HashMap<>();
    private final List<Slow> slowest = new ArrayList<>();
    private long decisions;
    private long cutShort;
    private long totalNanos;
    private long longestNanos;

    public synchronized void record(ThinkingProfile profile, String faction, String outcome, boolean wasCutShort) {
        decisions++;
        if (wasCutShort) cutShort++;
        totalNanos += profile.totalNanos();
        longestNanos = Math.max(longestNanos, profile.totalNanos());
        for (SpanTotal span : profile.spans()) {
            Total total = spans.computeIfAbsent(span.name(), name -> new Total());
            total.calls += span.calls();
            total.totalNanos += span.totalNanos();
            total.selfNanos += span.selfNanos();
            total.maxReentry = Math.max(total.maxReentry, span.maxReentry());
        }
        keepIfSlow(new Slow(faction, outcome, profile.totalNanos(), summary(profile)));
    }

    public synchronized void recordOutside(String name, long nanos) {
        Total total = spans.computeIfAbsent(name, key -> new Total());
        total.calls++;
        total.totalNanos += nanos;
        total.selfNanos += nanos;
    }

    public synchronized long decisions() {
        return decisions;
    }

    public synchronized long cutShort() {
        return cutShort;
    }

    public synchronized List<String> lines(int spanRows, int slowRows) {
        List<String> lines = new ArrayList<>();
        lines.add(decisions
                + " decisions ("
                + cutShort
                + " cut short), "
                + millis(totalNanos)
                + " in all, "
                + millis(decisions == 0 ? 0 : totalNanos / decisions)
                + " on average, "
                + millis(longestNanos)
                + " longest");
        spans.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<String, Total> entry) -> entry.getValue().selfNanos)
                        .reversed())
                .limit(spanRows)
                .forEach(entry -> lines.add(spanLine(entry.getKey(), entry.getValue())));
        slowest.stream().limit(slowRows).forEach(slow -> lines.add(slowLine(slow)));
        return lines;
    }

    private static String spanLine(String name, Total total) {
        return name + ": " + total.calls + " calls, self " + millis(total.selfNanos) + ", total "
                + millis(total.totalNanos) + (total.maxReentry > 1 ? ", nested " + total.maxReentry + " deep" : "");
    }

    private static String slowLine(Slow slow) {
        return "slow: " + slow.faction() + " " + slow.outcome() + " " + millis(slow.nanos())
                + (slow.top().isEmpty() ? "" : " in " + slow.top());
    }

    public static String summary(ThinkingProfile profile) {
        return String.join(
                ", ",
                profile.spans().stream()
                        .limit(TOP_SPANS_PER_DECISION)
                        .map(span -> span.name() + " " + millis(span.selfNanos()))
                        .toList());
    }

    private void keepIfSlow(Slow slow) {
        slowest.add(slow);
        slowest.sort(Comparator.comparingLong(Slow::nanos).reversed());
        while (slowest.size() > SLOWEST_KEPT) slowest.removeLast();
    }

    public static String millis(long nanos) {
        long millis = nanos / NANOS_PER_MILLI;
        return millis < 1000 ? millis + " ms" : String.format(Locale.ROOT, "%.1f s", millis / 1000.0);
    }
}
