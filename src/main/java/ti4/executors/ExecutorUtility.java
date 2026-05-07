package ti4.executors;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ExecutorUtility {

    public static ShutdownResult shutdownAndAwaitTermination(ExecutorService service, long timeout, TimeUnit unit) {
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
            Thread.currentThread().interrupt();
            service.shutdownNow();
            return ShutdownResult.INTERRUPTED;
        }
    }
}
