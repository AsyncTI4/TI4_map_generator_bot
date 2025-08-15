package ti4.executors;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import ti4.cron.CronManager;
import ti4.helpers.TimedRunnable;
import ti4.message.BotLogger;

public final class ExecutionHistoryManager {

    private static final AtomicInteger EXECUTION_COUNTER = new AtomicInteger();
    private static final Map<Integer, Execution> executionStartTimes = new ConcurrentHashMap<>();

    static {
        Runnable logLongExecutions = () -> {
            var now = Instant.now();
            for (Execution execution : executionStartTimes.values()) {
                var elapsedDuration = Duration.between(execution.startTime, now);
                var elapsedMinutes = elapsedDuration.toMinutes();
                var elapsedSeconds = elapsedDuration.toSeconds() % 60;
                if (elapsedMinutes >= 2) {
                    BotLogger.error("A task has been executing for " + elapsedMinutes + " minutes and " + elapsedSeconds
                            + " seconds: " + execution.name);
                } else if (elapsedMinutes == 1 && CircuitBreaker.incrementThresholdCount()) {
                    BotLogger.warning("Incremented circuit breaker threshold. Task name: " + execution.name);
                }
            }
        };
        CronManager.schedulePeriodically(ExecutionHistoryManager.class, logLongExecutions, 0, 1, TimeUnit.MINUTES);
    }

    private ExecutionHistoryManager() {}

    public static void runWithExecutionHistory(ExecutorService executorService, TimedRunnable timedRunnable) {
        var executionHistoryRunnable = wrapWithExecutionHistory(timedRunnable);
        executorService.execute(executionHistoryRunnable);
    }

    private static Runnable wrapWithExecutionHistory(TimedRunnable timedRunnable) {
        return () -> {
            int id = EXECUTION_COUNTER.incrementAndGet();
            executionStartTimes.put(id, new Execution(timedRunnable.getName(), Instant.now()));
            try {
                timedRunnable.run();
            } finally {
                executionStartTimes.remove(id);
            }
        };
    }

    private record Execution(String name, Instant startTime) {}
}
