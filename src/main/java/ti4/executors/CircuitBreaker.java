package ti4.executors;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import lombok.Getter;
import ti4.cron.CronManager;
import ti4.message.BotLogger;

public class CircuitBreaker {

    private static final int CIRCUIT_BREAK_THRESHOLD = 5;
    private static final int MINUTES_TO_WAIT_BEFORE_CLOSING = 5;
    private static final int MINUTES_TO_WAIT_BEFORE_RESETTING_THRESHOLD = 1;

    private static int thresholdCount;
    @Getter
    private static boolean open;
    private static LocalDateTime closeDateTime;

    public synchronized static void incrementThresholdCount() {
        if (open) {
            return;
        }
        thresholdCount++;
        if (thresholdCount == 1) {
            CronManager.scheduleOnce(CircuitBreaker.class, CircuitBreaker::resetThreshold, MINUTES_TO_WAIT_BEFORE_RESETTING_THRESHOLD, TimeUnit.MINUTES);
        }
        if (thresholdCount >= CIRCUIT_BREAK_THRESHOLD) {
            open = true;
            CronManager.scheduleOnce(CircuitBreaker.class, CircuitBreaker::reset, MINUTES_TO_WAIT_BEFORE_CLOSING, TimeUnit.MINUTES);
            closeDateTime = LocalDateTime.now().plusMinutes(MINUTES_TO_WAIT_BEFORE_CLOSING);
            BotLogger.error("Excess errors or timeouts have caused the circuit breaker to open. The bot will not accept commands for " +
                MINUTES_TO_WAIT_BEFORE_CLOSING + " minutes.");
        }
    }

    private synchronized static void reset() {
        thresholdCount = 0;
        open = false;
    }

    private synchronized static void resetThreshold() {
        thresholdCount = 0;
    }

    public static Duration getDurationUtilClose() {
        if (!open || closeDateTime == null) return Duration.ZERO;
        return Duration.between(LocalDateTime.now(), closeDateTime).isNegative()
            ? Duration.ZERO
            : Duration.between(LocalDateTime.now(), closeDateTime);
    }
}
