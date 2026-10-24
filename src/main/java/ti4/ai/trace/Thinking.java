package ti4.ai.trace;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import lombok.experimental.UtilityClass;
import ti4.ai.trace.ThinkingProfile.SpanTotal;

@UtilityClass
public class Thinking {

    private static final ThreadLocal<Session> CURRENT = new ThreadLocal<>();
    private static final Span NO_SPAN = () -> {};
    private static final int MAX_KEYS = 12;

    public interface Span extends AutoCloseable {
        @Override
        void close();
    }

    public interface Decision extends AutoCloseable {
        ThinkingProfile profile();

        @Override
        void close();
    }

    public static Decision open(ThinkingLimits limits) {
        return open(limits, System::nanoTime);
    }

    public static Decision open(ThinkingLimits limits, LongSupplier clock) {
        Session session = new Session(limits, clock, CURRENT.get());
        CURRENT.set(session);
        return session;
    }

    public static Span enter(String name) {
        return enter(name, null);
    }

    public static Span enter(String name, Supplier<String> key) {
        Session session = CURRENT.get();
        return session == null ? NO_SPAN : session.enter(name, key);
    }

    private static final class Stat {
        private final String name;
        private long calls;
        private int active;
        private int maxActive;
        private long totalNanos;
        private long selfNanos;

        private Stat(String name) {
            this.name = name;
        }
    }

    private static final class Session implements Decision {
        private final ThinkingLimits limits;
        private final LongSupplier clock;
        private final Session previous;
        private final long start;
        private final List<Frame> stack = new ArrayList<>();
        private final Map<String, Stat> stats = new HashMap<>();
        private ThinkingAborted aborted;
        private long end;

        private Session(ThinkingLimits limits, LongSupplier clock, Session previous) {
            this.limits = limits;
            this.clock = clock;
            this.previous = previous;
            this.start = clock.getAsLong();
        }

        private Span enter(String name, Supplier<String> key) {
            if (aborted != null) throw aborted;
            long now = clock.getAsLong();
            Stat stat = stats.computeIfAbsent(name, Stat::new);
            stat.calls++;
            String reason = violation(stat, now);
            if (reason != null) throw abort(name, key, reason);
            stat.active++;
            stat.maxActive = Math.max(stat.maxActive, stat.active);
            Frame frame = new Frame(this, stat, key, now);
            stack.add(frame);
            return frame;
        }

        private String violation(Stat stat, long now) {
            if (stat.calls > limits.callCap()) {
                return stat.name + " was called more than " + limits.callCap() + " times";
            }
            if (stat.active >= limits.reentryLimit()) {
                return stat.name + " re-entered itself " + stat.active + " deep";
            }
            if (stack.size() >= limits.depthLimit()) return "it nested " + stack.size() + " spans deep";
            if (limits.hasBudget() && now - start > limits.budgetNanos()) {
                return "it thought for more than " + limits.budgetNanos() / 1_000_000L + " ms";
            }
            return null;
        }

        private ThinkingAborted abort(String entering, Supplier<String> enteringKey, String reason) {
            List<String> names = new ArrayList<>(stack.size() + 1);
            Set<String> keys = new LinkedHashSet<>();
            for (Frame frame : stack) {
                names.add(frame.stat.name);
                addKey(keys, frame.stat.name, frame.key);
            }
            names.add(entering);
            addKey(keys, entering, enteringKey);
            aborted = new ThinkingAborted(reason, names, List.copyOf(keys));
            return aborted;
        }

        private static void addKey(Set<String> keys, String name, Supplier<String> key) {
            if (key != null && keys.size() < MAX_KEYS) keys.add(name + " " + key.get());
        }

        private void exit(Frame frame) {
            long elapsed = clock.getAsLong() - frame.start;
            int index = stack.lastIndexOf(frame);
            if (index < 0) return;
            while (stack.size() > index + 1) stack.getLast().close();
            stack.remove(index);
            Stat stat = frame.stat;
            stat.active--;
            stat.selfNanos += elapsed - frame.childNanos;
            if (stat.active == 0) stat.totalNanos += elapsed;
            if (!stack.isEmpty()) stack.getLast().childNanos += elapsed;
        }

        @Override
        public ThinkingProfile profile() {
            long finished = end == 0 ? clock.getAsLong() : end;
            List<SpanTotal> spans = stats.values().stream()
                    .map(stat -> new SpanTotal(stat.name, stat.calls, stat.totalNanos, stat.selfNanos, stat.maxActive))
                    .sorted(Comparator.comparingLong(SpanTotal::selfNanos).reversed())
                    .toList();
            return new ThinkingProfile(finished - start, spans);
        }

        @Override
        public void close() {
            end = clock.getAsLong();
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    private static final class Frame implements Span {
        private final Session session;
        private final Stat stat;
        private final Supplier<String> key;
        private final long start;
        private long childNanos;

        private Frame(Session session, Stat stat, Supplier<String> key, long start) {
            this.session = session;
            this.stat = stat;
            this.key = key;
            this.start = start;
        }

        @Override
        public void close() {
            session.exit(this);
        }
    }
}
