package ti4.ai.runtime;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Where a self-play game's thread spends its time, round by round. The AI's own decisions are already profiled, but
// the harness loop around them (reloading the save after every change, the trackers, the stop checks) was not, and in
// one game of the styx-take-rate batch it took more CPU than the AI did. One HARNESS line per round, printed when the
// round ends, plus a total at the end of the game. Every part is timed in wall time and in the game thread's CPU time,
// since other processes may share the machine. It only reads clocks and the save file's size, so it changes nothing a
// seeded game does.
final class HarnessTiming {

    static final String TICK = "tick";
    static final String RELOAD = "reload";
    static final String HANDS = "hands";

    // Parts timed beside the ticks that the round line lists one by one, largest first, after the fixed ones.
    private static final int LISTED_PARTS = 8;

    private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();

    private final String game;
    private Totals round = new Totals();
    private final Totals all = new Totals();
    private final List<String> lines = new ArrayList<>();
    private int currentRound = -1;
    private boolean finished;
    private long roundWallStart;
    private long roundCpuStart;
    private long gameWallStart;
    private long gameCpuStart;

    HarnessTiming(String game) {
        this.game = game;
    }

    static long cpu() {
        return THREADS.isCurrentThreadCpuTimeSupported() ? THREADS.getCurrentThreadCpuTime() : 0;
    }

    // A point in time on both clocks, to time a part from.
    record Mark(long wall, long cpu) {
        static Mark now() {
            return new Mark(System.nanoTime(), HarnessTiming.cpu());
        }
    }

    void start() {
        gameWallStart = System.nanoTime();
        gameCpuStart = cpu();
        roundWallStart = gameWallStart;
        roundCpuStart = gameCpuStart;
    }

    // Charges the time since the mark to the part and returns a new mark, so consecutive parts can be timed in a row.
    Mark add(String part, Mark since) {
        Mark now = Mark.now();
        add(part, now.wall() - since.wall(), now.cpu() - since.cpu());
        return now;
    }

    void add(String part, long wallNanos, long cpuNanos) {
        round.add(part, wallNanos, cpuNanos, 1);
    }

    void iteration() {
        round.iterations++;
    }

    // Called at the top of every harness iteration with the round the game is in, before anything of the iteration is
    // timed. A new round closes the last one.
    void observeRound(int gameRound, java.util.function.LongSupplier saveBytes) {
        if (currentRound < 0) currentRound = gameRound;
        if (gameRound == currentRound) return;
        closeRound(saveBytes.getAsLong());
        currentRound = gameRound;
    }

    void finish(long saveBytes) {
        if (finished) return;
        finished = true;
        closeRound(saveBytes);
        long wall = System.nanoTime() - gameWallStart;
        long cpuUsed = cpu() - gameCpuStart;
        String line = "HARNESS " + game + " total: " + describe(all, wall, cpuUsed, saveBytes);
        lines.add(line);
        System.out.println(line);
    }

    List<String> lines() {
        return List.copyOf(lines);
    }

    private void closeRound(long saveBytes) {
        if (currentRound < 0) return;
        long now = System.nanoTime();
        long cpuNow = cpu();
        String line = "HARNESS " + game + " r" + currentRound + ": "
                + describe(round, now - roundWallStart, cpuNow - roundCpuStart, saveBytes);
        lines.add(line);
        System.out.println(line);
        all.addAll(round);
        round = new Totals();
        roundWallStart = now;
        roundCpuStart = cpuNow;
        currentRound = -1;
    }

    private static String describe(Totals totals, long wall, long cpuUsed, long saveBytes) {
        long[] tick = totals.get(TICK);
        long wallAccounted = 0;
        long cpuAccounted = 0;
        for (long[] part : totals.parts.values()) {
            wallAccounted += part[0];
            cpuAccounted += part[1];
        }
        StringBuilder text = new StringBuilder();
        text.append("wall ")
                .append(seconds(wall))
                .append(" (CPU ")
                .append(seconds(cpuUsed))
                .append("), ticks ")
                .append(pair(tick))
                .append(" x")
                .append(tick[2])
                .append(" | between ticks wall ")
                .append(seconds(wall - tick[0]))
                .append(", CPU ")
                .append(seconds(cpuUsed - tick[1]))
                .append(':');
        long[] reload = totals.get(RELOAD);
        text.append(" reloads ").append(pair(reload)).append(" x").append(reload[2]);
        if (reload[2] > 0)
            text.append(" (").append(millis(reload[0] / reload[2])).append(" each)");
        text.append(',');
        totals.parts.entrySet().stream()
                .filter(entry -> !TICK.equals(entry.getKey()) && !RELOAD.equals(entry.getKey()))
                .sorted(Comparator.comparingLong((Map.Entry<String, long[]> entry) -> entry.getValue()[1])
                        .reversed())
                .limit(LISTED_PARTS)
                .forEach(entry -> text.append(' ')
                        .append(entry.getKey())
                        .append(' ')
                        .append(pair(entry.getValue()))
                        .append(','));
        text.append(" unaccounted wall ")
                .append(seconds(wall - wallAccounted))
                .append(", CPU ")
                .append(seconds(cpuUsed - cpuAccounted))
                .append(" | iterations ")
                .append(totals.iterations)
                .append(", save ")
                .append(saveBytes / 1024)
                .append(" KB");
        return text.toString();
    }

    // Wall time, then CPU time in brackets.
    private static String pair(long[] part) {
        return seconds(part[0]) + " [" + seconds(part[1]) + "]";
    }

    private static String seconds(long nanos) {
        return String.format(Locale.ROOT, "%.1f s", nanos / 1e9);
    }

    private static String millis(long nanos) {
        return String.format(Locale.ROOT, "%.1f ms", nanos / 1e6);
    }

    private static final class Totals {
        // part -> {wall nanos, CPU nanos, count}, in the order the parts were first timed.
        final Map<String, long[]> parts = new LinkedHashMap<>();
        int iterations;

        long[] get(String part) {
            return parts.getOrDefault(part, new long[3]);
        }

        void add(String part, long wallNanos, long cpuNanos, long count) {
            long[] totals = parts.computeIfAbsent(part, ignored -> new long[3]);
            totals[0] += wallNanos;
            totals[1] += cpuNanos;
            totals[2] += count;
        }

        void addAll(Totals other) {
            other.parts.forEach((part, totals) -> add(part, totals[0], totals[1], totals[2]));
            iterations += other.iterations;
        }
    }
}
