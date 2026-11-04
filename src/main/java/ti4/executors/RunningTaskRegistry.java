package ti4.executors;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class RunningTaskRegistry {

    private static final AtomicLong NEXT_ID = new AtomicLong();
    private static final Map<Long, Execution> RUNNING = new ConcurrentHashMap<>();

    private RunningTaskRegistry() {}

    public static Registration start(String name) {
        Objects.requireNonNull(name);
        long id = NEXT_ID.incrementAndGet();
        RUNNING.put(id, new Execution(name, Instant.now(), System.nanoTime(), Thread.currentThread()));
        return new Registration(id);
    }

    public static Runnable wrap(String name, Runnable runnable) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(runnable);
        return () -> {
            try (var registration = start(name)) {
                runnable.run();
            }
        };
    }

    public static List<RunningTask> snapshot() {
        return RUNNING.entrySet().stream()
                .map(entry -> {
                    Execution execution = entry.getValue();
                    return new RunningTask(
                            entry.getKey(),
                            execution.name,
                            execution.startedAt,
                            Duration.ofNanos(System.nanoTime() - execution.startedNanos),
                            execution.thread.getName(),
                            execution.thread.getState());
                })
                .sorted(Comparator.comparing(RunningTask::startedAt).thenComparingLong(RunningTask::id))
                .toList();
    }

    public static final class Registration implements AutoCloseable {
        private final long id;

        private Registration(long id) {
            this.id = id;
        }

        @Override
        public void close() {
            RUNNING.remove(id);
        }
    }

    public record RunningTask(
            long id, String name, Instant startedAt, Duration elapsed, String threadName, Thread.State threadState) {}

    private record Execution(String name, Instant startedAt, long startedNanos, Thread thread) {}
}
