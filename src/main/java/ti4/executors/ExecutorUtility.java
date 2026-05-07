package ti4.executors;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import ti4.logging.BotLogger;

@UtilityClass
public class ExecutorUtility {

    public enum ShutdownResult {
        GRACEFUL_TERMINATION,
        FORCED_TERMINATION,
        TIMED_OUT,
        INTERRUPTED
    }

    public static ShutdownResult shutdownAndAwaitTermination(
            String executorName, ExecutorService service, long timeout, TimeUnit unit) {
        long halfTimeoutNanos = unit.toNanos(timeout) / 2;
        service.shutdown();
        try {
            if (service.awaitTermination(halfTimeoutNanos, TimeUnit.NANOSECONDS)) {
                return ShutdownResult.GRACEFUL_TERMINATION;
            }

            service.shutdownNow();
            if (service.awaitTermination(halfTimeoutNanos, TimeUnit.NANOSECONDS)) {
                return ShutdownResult.FORCED_TERMINATION;
            }

            return ShutdownResult.TIMED_OUT;
        } catch (InterruptedException e) {
            BotLogger.error("ExecutorService shutdown interrupted for " + executorName + ".", e);
            Thread.currentThread().interrupt();
            service.shutdownNow();
            return ShutdownResult.INTERRUPTED;
        }
    }
}
