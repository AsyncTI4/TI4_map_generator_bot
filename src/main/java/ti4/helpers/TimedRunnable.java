package ti4.helpers;

import java.util.Objects;

import org.apache.commons.lang3.time.StopWatch;
import ti4.message.BotLogger;

public class TimedRunnable implements Runnable {

    private final String name;
    private final Runnable delegate;
    private final int warningThresholdSeconds;

    public TimedRunnable(String name, Runnable delegate) {
        this.name = Objects.requireNonNull(name, "Runnable name cannot be null");
        this.delegate = Objects.requireNonNull(delegate, "Runnable cannot be null");
        warningThresholdSeconds = 5;
    }

    public TimedRunnable(String name, int warningThresholdSeconds, Runnable delegate) {
        this.name = Objects.requireNonNull(name, "Runnable name cannot be null");
        this.delegate = Objects.requireNonNull(delegate, "Runnable cannot be null");
        this.warningThresholdSeconds = warningThresholdSeconds;
    }

    @Override
    public void run() {
        StopWatch stopWatch = StopWatch.createStarted();
        try {
            delegate.run();
        } finally {
            stopWatch.stop();
            long secondsElapsed = stopWatch.getDuration().toSeconds();
            if (secondsElapsed >= warningThresholdSeconds) {
                BotLogger.warning("'" + name + "' took longer than " + warningThresholdSeconds + " seconds (" + secondsElapsed + ").");
            }
        }
    }
}
