package ti4.ai.trace;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.experimental.UtilityClass;
import ti4.ai.trace.ThinkingProfile.SpanTotal;

@UtilityClass
public class TimingReport {

    public static final String PROPERTY = "ai.timing";
    static final long LIVE_LIMIT_NANOS = 20_000_000_000L;
    private static final long NANOS_PER_MILLI = 1_000_000L;
    private static final long[] BUCKET_MILLIS = {10, 100, 500, 2000, 5000, 20_000, 60_000};
    private static final double[] PERCENTILES = {50, 90, 99, 99.9};
    private static final int SPAN_ROWS = 80;
    private static final int RULE_ROWS = 40;
    private static final ThreadLocal<String> DECIDED_BY = new ThreadLocal<>();
    private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();
    private static boolean enabled = Boolean.getBoolean(PROPERTY);

    private static final class Tally {
        private long count;
        private long totalNanos;
        private long selfNanos;
        private long maxNanos;

        private void add(long calls, long total, long self, long max) {
            count += calls;
            totalNanos += total;
            selfNanos += self;
            maxNanos = Math.max(maxNanos, max);
        }
    }

    private static final Map<String, Tally> SPANS = new HashMap<>();
    private static final Map<String, Tally> OUTSIDE = new HashMap<>();
    private static final Map<String, Tally> OUTCOMES = new HashMap<>();
    private static final Map<String, Tally> DECIDING_RULES = new HashMap<>();
    private static final Map<String, Tally> INPUT_CHANGES = new HashMap<>();
    private static final List<Long> WALL = new ArrayList<>();
    private static final List<Long> CPU = new ArrayList<>();

    public static boolean enabled() {
        return enabled;
    }

    static void enable(boolean on) {
        enabled = on;
    }

    public static long cpuNow() {
        if (!enabled || !THREADS.isCurrentThreadCpuTimeSupported()) return 0;
        return THREADS.getCurrentThreadCpuTime();
    }

    public static void startDecision() {
        DECIDED_BY.remove();
    }

    public static void decidedBy(String rule) {
        if (enabled) DECIDED_BY.set(rule);
    }

    public static void recordDecision(ThinkingProfile profile, long cpuNanos, String outcome) {
        recordDecision(profile, cpuNanos, outcome, "");
    }

    public static void recordDecision(ThinkingProfile profile, long cpuNanos, String outcome, String inputChange) {
        String rule = DECIDED_BY.get();
        DECIDED_BY.remove();
        if (!enabled) return;
        long wall = profile.totalNanos();
        synchronized (TimingReport.class) {
            WALL.add(wall);
            CPU.add(cpuNanos);
            OUTCOMES.computeIfAbsent(outcome, key -> new Tally()).add(1, wall, cpuNanos, wall);
            if (!inputChange.isEmpty()) {
                INPUT_CHANGES
                        .computeIfAbsent(outcome + ", " + inputChange, key -> new Tally())
                        .add(1, wall, cpuNanos, wall);
            }
            DECIDING_RULES
                    .computeIfAbsent(rule == null ? "(no rule)" : rule, key -> new Tally())
                    .add(1, wall, cpuNanos, wall);
            for (SpanTotal span : profile.spans()) {
                SPANS.computeIfAbsent(span.name(), key -> new Tally())
                        .add(span.calls(), span.totalNanos(), span.selfNanos(), span.maxNanos());
            }
        }
    }

    public static void recordOutside(String name, long nanos) {
        if (!enabled) return;
        synchronized (TimingReport.class) {
            OUTSIDE.computeIfAbsent(name, key -> new Tally()).add(1, nanos, nanos, nanos);
        }
    }

    public static synchronized void reset() {
        SPANS.clear();
        OUTSIDE.clear();
        OUTCOMES.clear();
        DECIDING_RULES.clear();
        INPUT_CHANGES.clear();
        WALL.clear();
        CPU.clear();
    }

    public static synchronized List<String> lines() {
        List<String> lines = new ArrayList<>();
        if (WALL.isEmpty()) {
            lines.add("TIMING no decisions recorded (run with -D" + PROPERTY + "=true)");
            return lines;
        }
        long[] wall = sorted(WALL);
        long[] cpu = sorted(CPU);
        long wallTotal = Arrays.stream(wall).sum();
        long cpuTotal = Arrays.stream(cpu).sum();
        lines.add(String.format(
                Locale.ROOT,
                "TIMING decisions %d: wall %s (mean %s), CPU %s (mean %s, %.0f%% of wall)",
                wall.length,
                millis(wallTotal),
                millis(wallTotal / wall.length),
                millis(cpuTotal),
                millis(cpuTotal / cpu.length),
                wallTotal == 0 ? 0 : 100.0 * cpuTotal / wallTotal));
        lines.add("TIMING decision wall " + percentiles(wall) + "; CPU " + percentiles(cpu));
        lines.add("TIMING decision wall buckets " + buckets(wall) + "; over the live 20 s limit: "
                + over(wall, LIVE_LIMIT_NANOS) + " by wall, " + over(cpu, LIVE_LIMIT_NANOS) + " by CPU");
        lines.add("TIMING by outcome (count, wall total, wall mean, CPU total, wall max):");
        rows(OUTCOMES, Integer.MAX_VALUE, Comparator.comparingLong(tally -> -tally.totalNanos))
                .forEach(row -> lines.add("TIMING   " + decisionRow(row.getKey(), row.getValue(), wallTotal)));
        if (!INPUT_CHANGES.isEmpty()) {
            lines.add("TIMING by outcome and what changed since the seat's last decision ended"
                    + " (count, wall total and share, wall mean, CPU total, wall max):");
            rows(INPUT_CHANGES, Integer.MAX_VALUE, Comparator.comparingLong(tally -> -tally.totalNanos))
                    .forEach(row -> lines.add("TIMING   " + decisionRow(row.getKey(), row.getValue(), wallTotal)));
        }
        lines.add("TIMING by deciding rule (count, wall total and share, wall mean, CPU total, wall max):");
        rows(DECIDING_RULES, RULE_ROWS, Comparator.comparingLong(tally -> -tally.totalNanos))
                .forEach(row -> lines.add("TIMING   " + decisionRow(row.getKey(), row.getValue(), wallTotal)));
        lines.add(
                "TIMING spans by self time (calls, self and share of decision wall, total, mean per call, max call):");
        rows(SPANS, SPAN_ROWS, Comparator.comparingLong(tally -> -tally.selfNanos))
                .forEach(row -> lines.add("TIMING   " + spanRow(row.getKey(), row.getValue(), wallTotal)));
        lines.add("TIMING around decisions; ai.tick and ai.seat include the decisions (calls, total, mean, max):");
        rows(OUTSIDE, Integer.MAX_VALUE, Comparator.comparingLong(tally -> -tally.totalNanos))
                .forEach(row -> lines.add("TIMING   " + outsideRow(row.getKey(), row.getValue())));
        return lines;
    }

    private static List<Map.Entry<String, Tally>> rows(Map<String, Tally> tallies, int limit, Comparator<Tally> order) {
        return tallies.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(order))
                .limit(limit)
                .toList();
    }

    private static String decisionRow(String name, Tally tally, long wallTotal) {
        return String.format(
                Locale.ROOT,
                "%s: %d, %s (%.1f%%), mean %s, CPU %s, max %s",
                name,
                tally.count,
                millis(tally.totalNanos),
                share(tally.totalNanos, wallTotal),
                millis(tally.totalNanos / Math.max(1, tally.count)),
                millis(tally.selfNanos),
                millis(tally.maxNanos));
    }

    private static String spanRow(String name, Tally tally, long wallTotal) {
        return String.format(
                Locale.ROOT,
                "%s: %d calls, self %s (%.1f%%), total %s, mean %s, max %s",
                name,
                tally.count,
                millis(tally.selfNanos),
                share(tally.selfNanos, wallTotal),
                millis(tally.totalNanos),
                micros(tally.totalNanos / Math.max(1, tally.count)),
                millis(tally.maxNanos));
    }

    private static String outsideRow(String name, Tally tally) {
        return name + ": " + tally.count + ", " + millis(tally.totalNanos) + ", mean "
                + micros(tally.totalNanos / Math.max(1, tally.count)) + ", max " + millis(tally.maxNanos);
    }

    private static long[] sorted(List<Long> values) {
        long[] array = values.stream().mapToLong(Long::longValue).toArray();
        Arrays.sort(array);
        return array;
    }

    static String percentiles(long[] sorted) {
        List<String> parts = new ArrayList<>();
        for (double percentile : PERCENTILES) {
            int index = (int) Math.min(sorted.length - 1, Math.ceil(percentile / 100 * sorted.length) - 1);
            parts.add("p" + trimmed(percentile) + " " + millis(sorted[Math.max(0, index)]));
        }
        parts.add("max " + millis(sorted[sorted.length - 1]));
        return String.join(", ", parts);
    }

    static String buckets(long[] sorted) {
        List<String> parts = new ArrayList<>();
        long below = 0;
        int index = 0;
        for (long limit : BUCKET_MILLIS) {
            long count = 0;
            while (index < sorted.length && sorted[index] < limit * NANOS_PER_MILLI) {
                count++;
                index++;
            }
            parts.add("<" + bucketName(limit) + " " + count);
            below += count;
        }
        parts.add(">=" + bucketName(BUCKET_MILLIS[BUCKET_MILLIS.length - 1]) + " " + (sorted.length - below));
        return String.join(", ", parts);
    }

    private static String over(long[] sorted, long limit) {
        long count = Arrays.stream(sorted).filter(value -> value > limit).count();
        return String.format(Locale.ROOT, "%d (%.2f%%)", count, share(count, sorted.length));
    }

    private static String bucketName(long millis) {
        return millis < 1000 ? millis + "ms" : millis / 1000 + "s";
    }

    private static String trimmed(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    private static double share(long part, long whole) {
        return whole == 0 ? 0 : 100.0 * part / whole;
    }

    private static String millis(long nanos) {
        return ThinkingStats.millis(nanos);
    }

    private static String micros(long nanos) {
        long micros = nanos / 1000;
        return micros < 1000 ? micros + " us" : millis(nanos);
    }
}
