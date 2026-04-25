package ti4.discord.interactions.buttons;

import java.time.LocalDateTime;
import lombok.Getter;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ButtonHelper;
import ti4.helpers.DateTimeHelper;
import ti4.logging.BotLogger;
import ti4.service.statistics.SREStats;

class ButtonRuntimeWarningService {

    private static final int WARNING_THRESHOLD_MILLISECONDS = 1500;
    private static final int RUNTIME_WARNING_COUNT_THRESHOLD = 20;

    private int runtimeWarningCount;
    private LocalDateTime pauseWarningsUntil = LocalDateTime.now();
    private LocalDateTime lastWarningTime = LocalDateTime.now();

    @Getter
    private int totalRuntimeSubmissionCount;

    @Getter
    private int totalRuntimeThresholdMissCount;

    @Getter
    private double averageProcessingTime;

    @Getter
    private double averagePreprocessingTime;

    void submitNewRuntime(
            ButtonInteractionEvent event,
            long processingStartTimeMs,
            long processingEndTimeMs,
            long contextCreationRuntimeMs,
            long timeInQueueMs,
            long resolveRuntimeMs,
            long saveRuntimeMs) {

        totalRuntimeSubmissionCount++;

        long processingTimeMs = processingEndTimeMs - processingStartTimeMs;
        averageProcessingTime = ((averageProcessingTime * (totalRuntimeSubmissionCount - 1)) + processingTimeMs)
                / totalRuntimeSubmissionCount;

        long eventTimeMs = DateTimeHelper.getLongDateTimeFromDiscordSnowflake(event.getInteraction());
        long preprocessingTimeMs = processingStartTimeMs - eventTimeMs;

        averagePreprocessingTime =
                ((averagePreprocessingTime * (totalRuntimeSubmissionCount - 1)) + preprocessingTimeMs)
                        / totalRuntimeSubmissionCount;

        SREStats.recordButtonPreprocessingMillis(preprocessingTimeMs);
        SREStats.recordButtonProcessingMillis(processingTimeMs);

        var now = LocalDateTime.now();
        if (now.minusMinutes(1).isAfter(lastWarningTime)) {
            runtimeWarningCount = 0;
        }

        boolean slowPreprocess = preprocessingTimeMs > WARNING_THRESHOLD_MILLISECONDS;
        boolean slowExecution = processingTimeMs > WARNING_THRESHOLD_MILLISECONDS;

        if (!slowPreprocess && !slowExecution) {
            return;
        }

        totalRuntimeThresholdMissCount++;

        if (pauseWarningsUntil.isAfter(now)) {
            return;
        }

        String eventTime = DateTimeHelper.getTimestampFromMillisecondsEpoch(eventTimeMs);

        String preprocessingTime = formatMillisecondsWithWarning(preprocessingTimeMs);
        String processingTime = formatMillisecondsWithWarning(processingTimeMs);

        String contextTime = formatMillisecondsWithWarning(contextCreationRuntimeMs);
        String queueTime = formatMillisecondsWithWarning(timeInQueueMs);
        String resolveTime = formatMillisecondsWithWarning(resolveRuntimeMs);
        String saveTime = formatMillisecondsWithWarning(saveRuntimeMs);
        String responseTime = formatMillisecondsWithWarning(processingEndTimeMs - eventTimeMs);

        String message = event.getUser().getEffectiveName()
                + " pressed button: "
                + ButtonHelper.getButtonRepresentation(event.getButton())
                + " in: [" + event.getChannel().getName() + "]("
                + event.getMessage().getJumpUrl() + ") "
                + "\n> ⚠ **Slow Button Warning:** Took over " + WARNING_THRESHOLD_MILLISECONDS + "ms"
                + "\n> 🕒 Event start: `" + eventTime + "`"
                + "\n> 🧩 Built context in: `" + contextTime + "`"
                + "\n> 📦 Queued for: `" + queueTime + "`"
                + "\n> 🛠 Executed in: `" + resolveTime + "`"
                + "\n> 💾 Saved in: `" + saveTime + "`"
                + "\n> ⚡ Total preprocessing time: `" + preprocessingTime + "`"
                + "\n> ⚡ Total processing time: `" + processingTime + "`"
                + "\n> 🕒 Total response time: `" + responseTime + "`";

        BotLogger.warning(message);

        ++runtimeWarningCount;

        if (runtimeWarningCount > RUNTIME_WARNING_COUNT_THRESHOLD) {
            pauseWarningsUntil = now.plusMinutes(5);
            BotLogger.error("**Buttons are processing slowly. Pausing warnings for 5 minutes.**");
            runtimeWarningCount = 0;
        }

        lastWarningTime = now;
    }

    private static String formatMillisecondsWithWarning(long runtimeMs) {
        String formattedRuntime = DateTimeHelper.getTimeRepresentationToMilliseconds(runtimeMs);
        if (runtimeMs > 500) {
            return formattedRuntime + " ❗";
        }
        return formattedRuntime;
    }
}
