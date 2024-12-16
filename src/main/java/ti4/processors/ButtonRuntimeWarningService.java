package ti4.processors;

import java.time.LocalDateTime;

import lombok.Getter;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ButtonHelper;
import ti4.helpers.DateTimeHelper;
import ti4.message.BotLogger;

class ButtonRuntimeWarningService {

    private static final int warningThresholdMilliseconds = 2000;

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

    void submitNewRuntime(ButtonInteractionEvent event, long startTime, long endTime, long contextRuntime, long resolveRuntime, long saveRuntime) {
        totalRuntimeSubmissionCount++;
        long processingTime = endTime - startTime;
        averageProcessingTime = ((averageProcessingTime * (totalRuntimeSubmissionCount - 1)) + processingTime) / totalRuntimeSubmissionCount;

        long eventStartTime = DateTimeHelper.getLongDateTimeFromDiscordSnowflake(event.getInteraction());
        long preprocessingTime = startTime - eventStartTime;
        averagePreprocessingTime = ((averagePreprocessingTime * (totalRuntimeSubmissionCount - 1)) + preprocessingTime) / totalRuntimeSubmissionCount;

        var now = LocalDateTime.now();
        if (now.minusMinutes(1).isAfter(lastWarningTime)) {
            runtimeWarningCount = 0;
        }
        if (startTime - eventStartTime > warningThresholdMilliseconds || endTime - startTime > warningThresholdMilliseconds) {
            totalRuntimeThresholdMissCount++;
            if (pauseWarningsUntil.isBefore(now)) {
                String responseTime = DateTimeHelper.getTimeRepresentationToMilliseconds(startTime - eventStartTime);
                String executionTime = DateTimeHelper.getTimeRepresentationToMilliseconds(endTime - startTime);
                String message = "[" + event.getChannel().getName() + "](" + event.getMessage().getJumpUrl() + ") " + event.getUser().getEffectiveName() + " pressed button: " + ButtonHelper.getButtonRepresentation(event.getButton()) +
                    "\n> Warning: This button took over " + warningThresholdMilliseconds + "ms to respond or execute\n> " +
                    DateTimeHelper.getTimestampFromMillisecondsEpoch(eventStartTime) + " button was pressed by user\n> " +
                    DateTimeHelper.getTimestampFromMillisecondsEpoch(startTime) + " `" + responseTime + "` to respond\n> " +
                    DateTimeHelper.getTimestampFromMillisecondsEpoch(endTime) + " `" + executionTime + "` to execute" + (endTime - startTime > startTime - eventStartTime ? "😲" : "");
                message += "\nContext time: " + contextRuntime + "ms\nResolve time: " + resolveRuntime + "ms\nSave time: " + saveRuntime + "ms";
                BotLogger.log(message);
                if (++runtimeWarningCount > 20) {
                    pauseWarningsUntil = now.plusMinutes(5);
                    BotLogger.log("**Buttons are processing slowly. Pausing warnings for 5 minutes.**");
                    runtimeWarningCount = 0;
                }
                lastWarningTime = now;
            }
        }
    }
}
