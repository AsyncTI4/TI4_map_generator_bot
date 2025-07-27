package ti4.executors;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import lombok.Getter;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import ti4.cron.CronManager;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

public class CircuitBreaker {

    private static final int CIRCUIT_BREAK_THRESHOLD = 10;
    private static final int MINUTES_TO_WAIT_BEFORE_CLOSING = 5;
    private static final int MINUTES_TO_WAIT_BEFORE_RESETTING_THRESHOLD = 1;

    private static int thresholdCount;
    @Getter
    private static boolean open;
    private static LocalDateTime closeDateTime;

    public synchronized static boolean incrementThresholdCount() {
        if (open) {
            return false;
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
        return true;
    }

    private synchronized static void reset() {
        thresholdCount = 0;
        open = false;
    }

    private synchronized static void resetThreshold() {
        thresholdCount = 0;
    }

    public static boolean checkIsOpenAndPostWarningIfTrue(MessageChannel messageChannel) {
        if (CircuitBreaker.isOpen()) {
            Duration durationUntilCircuitCloses = CircuitBreaker.getDurationUtilClose();
            MessageHelper.sendMessageToChannel(messageChannel, "The bot is taking a breather. Try again in " +
                durationUntilCircuitCloses.toMinutes() + " minutes and " + durationUntilCircuitCloses.getSeconds() % 60 + " seconds.");
        }
        return isOpen();
    }

    private static Duration getDurationUtilClose() {
        if (!open || closeDateTime == null) return Duration.ZERO;
        return Duration.between(LocalDateTime.now(), closeDateTime).isNegative()
            ? Duration.ZERO
            : Duration.between(LocalDateTime.now(), closeDateTime);
    }
}
