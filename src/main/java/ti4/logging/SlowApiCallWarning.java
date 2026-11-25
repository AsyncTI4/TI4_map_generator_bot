package ti4.logging;

import java.time.Duration;
import lombok.experimental.UtilityClass;
import ti4.AsyncTI4DiscordBot;
import ti4.helpers.DateTimeHelper;

@UtilityClass
public class SlowApiCallWarning {

    static final Duration INCOMING_THRESHOLD = Duration.ofSeconds(5);
    static final Duration OUTGOING_THRESHOLD = Duration.ofSeconds(5);

    public static void warnIfSlowIncoming(String call, String outcome, long startNanos) {
        warnIfSlow("Incoming", call, outcome, elapsedMillisSince(startNanos), INCOMING_THRESHOLD);
    }

    public static void warnIfSlowOutgoing(String call, String outcome, long startNanos) {
        warnIfSlow("Outgoing", call, outcome, elapsedMillisSince(startNanos), OUTGOING_THRESHOLD);
    }

    private static void warnIfSlow(String direction, String call, String outcome, long elapsedMs, Duration threshold) {
        if (!isSlow(elapsedMs, threshold) || AsyncTI4DiscordBot.isUnstable()) return;
        BotLogger.warning(message(direction, call, outcome, elapsedMs, threshold));
    }

    static boolean isSlow(long elapsedMs, Duration threshold) {
        return elapsedMs >= threshold.toMillis();
    }

    static String message(String direction, String call, String outcome, long elapsedMs, Duration threshold) {
        return "\n> ⚠ **Slow " + direction + " API Call Warning:** Took over " + threshold.toMillis() + "ms"
                + "\n> 🌐 Call: `" + call + "`"
                + "\n> 📬 Outcome: `" + outcome + "`"
                + "\n> ⏱ Took: `" + DateTimeHelper.getTimeRepresentationToMilliseconds(elapsedMs) + "`";
    }

    private static long elapsedMillisSince(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }
}
