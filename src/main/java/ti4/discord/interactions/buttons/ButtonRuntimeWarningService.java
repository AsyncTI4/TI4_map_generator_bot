package ti4.discord.interactions.buttons;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.AsyncTI4DiscordBot;
import ti4.helpers.ButtonHelper;
import ti4.helpers.DateTimeHelper;
import ti4.logging.BotLogger;
import ti4.service.statistics.SREStats;

class ButtonRuntimeWarningService {

    private static final int PREPROCESSING_WARNING_THRESHOLD_MILLISECONDS = 2500;
    private static final int PROCESSING_WARNING_THRESHOLD_MILLISECONDS = 1000;
    private static final int RUNTIME_WARNING_COUNT_THRESHOLD = 15;
    private static final long RESET_WARNING_COUNT_AFTER_SECONDS =
            Duration.ofMinutes(1).toSeconds();
    private static final long PAUSE_AFTER_WARNING_SECONDS =
            Duration.ofMinutes(5).toSeconds();

    private int runtimeWarningCount;
    private Instant pauseWarningsUntil = Instant.now();
    private Instant lastWarningTime = Instant.now();
    private final List<ThresholdWarningReason> thresholdWarningReasons = new ArrayList<>();

    @Getter
    private long runtimeSubmissionCount;

    @Getter
    private long runtimeThresholdMissCount;

    @Getter
    private long totalProcessingTime;

    @Getter
    private long totalPreprocessingTime;

    synchronized void submitNewRuntime(
            ButtonInteractionEvent event,
            long processingStartTimeMs,
            long processingEndTimeMs,
            long contextCreationRuntimeMs,
            long resolveRuntimeMs,
            long saveRuntimeMs) {
        if (AsyncTI4DiscordBot.isUnstable()) return;

        runtimeSubmissionCount++;

        long processingTimeMs = processingEndTimeMs - processingStartTimeMs;
        totalProcessingTime += processingTimeMs;

        long eventTimeMs = DateTimeHelper.getLongDateTimeFromDiscordSnowflake(event.getInteraction());
        long preprocessingTimeMs = processingStartTimeMs - eventTimeMs;
        totalPreprocessingTime += preprocessingTimeMs;

        SREStats.recordButtonPreprocessingMillis(preprocessingTimeMs);
        SREStats.recordButtonProcessingMillis(processingTimeMs);

        var now = Instant.now();
        if (lastWarningTime.isBefore(now.minusSeconds(RESET_WARNING_COUNT_AFTER_SECONDS))) {
            runtimeWarningCount = 0;
            thresholdWarningReasons.clear();
        }

        boolean slowPreprocess = preprocessingTimeMs >= PREPROCESSING_WARNING_THRESHOLD_MILLISECONDS;
        boolean slowExecution = processingTimeMs >= PROCESSING_WARNING_THRESHOLD_MILLISECONDS;

        if (!slowPreprocess && !slowExecution) {
            return;
        }

        runtimeThresholdMissCount++;

        if (pauseWarningsUntil.isAfter(now)) {
            return;
        }

        String eventTime = DateTimeHelper.getTimestampFromMillisecondsEpoch(eventTimeMs);

        String preprocessingTime = formatMillisecondsWithWarning(preprocessingTimeMs);
        String processingTime = formatMillisecondsWithWarning(processingTimeMs);

        String contextTime = formatMillisecondsWithWarning(contextCreationRuntimeMs);
        String resolveTime = formatMillisecondsWithWarning(resolveRuntimeMs);
        String saveTime = formatMillisecondsWithWarning(saveRuntimeMs);
        String responseTime = DateTimeHelper.getTimeRepresentationToMilliseconds(processingEndTimeMs - eventTimeMs);
        String buttonRepresentation = ButtonHelper.getButtonRepresentation(event.getButton());
        thresholdWarningReasons.add(new ThresholdWarningReason(eventTime, buttonRepresentation, responseTime));

        String message = event.getUser().getEffectiveName()
                + " pressed button: "
                + buttonRepresentation
                + " in: [" + event.getChannel().getName() + "]("
                + event.getMessage().getJumpUrl() + ") "
                + "\n> ⚠ **Slow Button Warning:**"
                + "\n> 🕒 Event start: " + eventTime
                + "\n> 🧩 Built context in: `" + contextTime + "`"
                + "\n> 🛠 Executed in: `" + resolveTime + "`"
                + "\n> 💾 Saved in: `" + saveTime + "`"
                + "\n> ⚡ Total preprocessing time: `" + preprocessingTime + "`"
                + "\n> ⚡ Total processing time: `" + processingTime + "`"
                + "\n> 🕒 Total response time: `" + responseTime + "`";

        BotLogger.warning(message);

        runtimeWarningCount++;

        if (runtimeWarningCount >= RUNTIME_WARNING_COUNT_THRESHOLD) {
            pauseWarningsUntil = now.plusSeconds(PAUSE_AFTER_WARNING_SECONDS);
            BotLogger.error(formatPauseWarningMessage());
            runtimeWarningCount = 0;
            thresholdWarningReasons.clear();
        }

        lastWarningTime = now;
    }

    private static String formatMillisecondsWithWarning(long runtimeMs) {
        String formattedRuntime = DateTimeHelper.getTimeRepresentationToMilliseconds(runtimeMs);
        if (runtimeMs >= PROCESSING_WARNING_THRESHOLD_MILLISECONDS) {
            return formattedRuntime + " ❗";
        }
        return formattedRuntime;
    }

    private String formatThresholdWarningReasons() {
        if (thresholdWarningReasons.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n> **Reasons:**");
        for (ThresholdWarningReason reason : thresholdWarningReasons) {
            sb.append("\n> - ")
                    .append(reason.occurredAt())
                    .append(" • ")
                    .append(reason.buttonRepresentation())
                    .append(" • `")
                    .append(reason.totalRuntime())
                    .append("`");
        }
        return sb.toString();
    }

    private String formatPauseWarningMessage() {
        return " **Buttons are processing slowly. Pausing warnings for 5 minutes.**" + formatThresholdWarningReasons();
    }

    synchronized double getAveragePreprocessingTime() {
        return runtimeSubmissionCount == 0 ? 0 : totalPreprocessingTime / (double) runtimeSubmissionCount;
    }

    synchronized double getAverageProcessingTime() {
        return runtimeSubmissionCount == 0 ? 0 : totalProcessingTime / (double) runtimeSubmissionCount;
    }

    synchronized double getThresholdMissPercent() {
        return runtimeSubmissionCount == 0 ? 0 : runtimeThresholdMissCount / (double) runtimeSubmissionCount;
    }

    private record ThresholdWarningReason(String occurredAt, String buttonRepresentation, String totalRuntime) {}
}
