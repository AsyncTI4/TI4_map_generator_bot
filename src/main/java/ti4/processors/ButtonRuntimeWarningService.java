package ti4.processors;

import java.time.LocalDateTime;
import lombok.Getter;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ButtonHelper;
import ti4.helpers.DateTimeHelper;
import ti4.message.logging.BotLogger;
import ti4.message.logging.LogOrigin;
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
            long startTime,
            long endTime,
            long contextRuntime,
            long resolveRuntime,
            long saveRuntime) {
        totalRuntimeSubmissionCount++;
        long processingTime = endTime - startTime;
        averageProcessingTime = ((averageProcessingTime * (totalRuntimeSubmissionCount - 1)) + processingTime)
                / totalRuntimeSubmissionCount;

        long eventTime = DateTimeHelper.getLongDateTimeFromDiscordSnowflake(event.getInteraction());
        long eventDelay = startTime - eventTime;
        averagePreprocessingTime = ((averagePreprocessingTime * (totalRuntimeSubmissionCount - 1)) + eventDelay)
                / totalRuntimeSubmissionCount;

        // Record metrics for histogram and percentiles
        SREStats.recordButtonPreprocessingMillis(eventDelay);
        SREStats.recordButtonProcessingMillis(processingTime);

        var now = LocalDateTime.now();
        if (now.minusMinutes(1).isAfter(lastWarningTime)) {
            runtimeWarningCount = 0;
        }
        if (eventDelay > WARNING_THRESHOLD_MILLISECONDS || processingTime > WARNING_THRESHOLD_MILLISECONDS) {
            totalRuntimeThresholdMissCount++;
            if (pauseWarningsUntil.isBefore(now)) {
                String responseTime = DateTimeHelper.getTimeRepresentationToMilliseconds(eventDelay);
                String executionTime = DateTimeHelper.getTimeRepresentationToMilliseconds(processingTime);
                String message = "[" + event.getChannel().getName() + "]("
                        + event.getMessage().getJumpUrl() + ") "
                        + event.getUser().getEffectiveName() + " pressed button: "
                        + ButtonHelper.getButtonRepresentation(event.getButton())
                        + "\n> Warning: This button took over "
                        + WARNING_THRESHOLD_MILLISECONDS + "ms to respond or execute\n> "
                        + DateTimeHelper.getTimestampFromMillisecondsEpoch(eventTime)
                        + " button was pressed by user\n> "
                        + DateTimeHelper.getTimestampFromMillisecondsEpoch(startTime)
                        + " `" + responseTime + "` to respond\n> "
                        + DateTimeHelper.getTimestampFromMillisecondsEpoch(endTime)
                        + " `" + executionTime + "` to execute" + (processingTime > eventDelay ? "😲" : "");
                message += "\nContext time: " + contextRuntime + "ms\nResolve time: " + resolveRuntime
                        + "ms\nSave time: " + saveRuntime + "ms";
                BotLogger.warning(new LogOrigin(event), message);
                ++runtimeWarningCount;
                if (runtimeWarningCount > RUNTIME_WARNING_COUNT_THRESHOLD) {
                    pauseWarningsUntil = now.plusMinutes(5);
                    BotLogger.error(
                            new LogOrigin(event), "**Buttons are processing slowly. Pausing warnings for 5 minutes.**");
                    runtimeWarningCount = 0;
                }
                lastWarningTime = now;
            }
        }
    }
}
