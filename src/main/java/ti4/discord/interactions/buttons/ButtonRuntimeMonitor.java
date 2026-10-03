package ti4.discord.interactions.buttons;

import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ButtonHelper;
import ti4.helpers.DateTimeHelper;
import ti4.logging.BotLogger;
import ti4.service.statistics.SREStats;

class ButtonRuntimeMonitor {

    private static final int PREPROCESSING_WARNING_THRESHOLD_MILLISECONDS = 2500;
    private static final int PROCESSING_WARNING_THRESHOLD_MILLISECONDS = 1000;
    private static final int RUNTIME_WARNING_COUNT_THRESHOLD = 15;
    private static final long RESET_WARNING_COUNT_AFTER_SECONDS =
            Duration.ofMinutes(1).toSeconds();
    private static final long PAUSE_AFTER_WARNING_SECONDS =
            Duration.ofMinutes(5).toSeconds();
    private static final String STATISTICS_ROW_FORMAT = "%-15s %7s %8s %7s %7s%n";

    private int runtimeWarningCount;
    private Instant pauseWarningsUntil = Instant.now();
    private Instant lastWarningTime = Instant.now();
    private final List<ThresholdWarningReason> thresholdWarningReasons = new ArrayList<>();

    private long queuedCount;
    private long startedCount;
    private long invalidCount;
    private long thresholdMissCount;
    private final LatencyHistogram preprocessing = new LatencyHistogram();
    private final LatencyHistogram processing = new LatencyHistogram();
    private final Map<ButtonPressStage, LatencyHistogram> stages = new EnumMap<>(ButtonPressStage.class);

    synchronized void recordQueued() {
        queuedCount++;
    }

    synchronized void recordStarted() {
        startedCount++;
    }

    synchronized void recordInvalid() {
        invalidCount++;
    }

    synchronized void submit(ButtonInteractionEvent event, ButtonPressTimeline timeline) {
        recordTimings(timeline);

        var now = Instant.now();
        if (lastWarningTime.isBefore(now.minusSeconds(RESET_WARNING_COUNT_AFTER_SECONDS))) {
            runtimeWarningCount = 0;
            thresholdWarningReasons.clear();
        }

        boolean slowPreprocess = timeline.getPreprocessingMillis() >= PREPROCESSING_WARNING_THRESHOLD_MILLISECONDS;
        boolean slowExecution = timeline.getProcessingMillis() >= PROCESSING_WARNING_THRESHOLD_MILLISECONDS;
        if (!slowPreprocess && !slowExecution) {
            return;
        }

        thresholdMissCount++;

        if (pauseWarningsUntil.isAfter(now)) {
            return;
        }

        warnSlowButton(event, timeline);

        runtimeWarningCount++;
        if (runtimeWarningCount >= RUNTIME_WARNING_COUNT_THRESHOLD) {
            pauseWarningsUntil = now.plusSeconds(PAUSE_AFTER_WARNING_SECONDS);
            BotLogger.spammyerror(formatPauseWarningMessage());
            runtimeWarningCount = 0;
            thresholdWarningReasons.clear();
        }

        lastWarningTime = now;
    }

    private void recordTimings(ButtonPressTimeline timeline) {
        preprocessing.record(timeline.getPreprocessingMillis());
        processing.record(timeline.getProcessingMillis());
        SREStats.recordButtonPreprocessingMillis(timeline.getPreprocessingMillis());
        SREStats.recordButtonProcessingMillis(timeline.getProcessingMillis());
        timeline.getStageMillis().forEach((stage, millis) -> {
            stages.computeIfAbsent(stage, _ -> new LatencyHistogram()).record(millis);
            SREStats.recordButtonStageMillis(stage.getShortName(), millis);
        });
    }

    private void warnSlowButton(ButtonInteractionEvent event, ButtonPressTimeline timeline) {
        String eventTime = DateTimeHelper.getTimestampFromMillisecondsEpoch(timeline.getDiscordCreatedAtMillis());
        String responseTime = DateTimeHelper.getTimeRepresentationToMilliseconds(timeline.getResponseMillis());
        String buttonRepresentation = ButtonHelper.getButtonRepresentation(event.getButton());
        thresholdWarningReasons.add(new ThresholdWarningReason(eventTime, buttonRepresentation, responseTime));

        StringBuilder message = new StringBuilder()
                .append(event.getUser().getEffectiveName())
                .append(" pressed button: ")
                .append(buttonRepresentation)
                .append(" in: [")
                .append(event.getChannel().getName())
                .append("](")
                .append(event.getMessage().getJumpUrl())
                .append(") ")
                .append("\n> ⚠ **Slow Button Warning:**")
                .append("\n> 🕒 Event start: ")
                .append(eventTime);
        Map<ButtonPressStage, Long> stageMillis = timeline.getStageMillis();
        for (ButtonPressStage stage : ButtonPressStage.values()) {
            Long millis = stageMillis.get(stage);
            if (millis == null) continue;
            message.append("\n> ")
                    .append(stage.getDescription())
                    .append(": `")
                    .append(formatMillisecondsWithWarning(millis))
                    .append("`");
        }
        message.append("\n> ⚡ Total preprocessing time: `")
                .append(formatMillisecondsWithWarning(timeline.getPreprocessingMillis()))
                .append("`\n> ⚡ Total processing time: `")
                .append(formatMillisecondsWithWarning(timeline.getProcessingMillis()))
                .append("`\n> 🕒 Total response time: `")
                .append(responseTime)
                .append("`");

        BotLogger.warning(message.toString());
    }

    private static String formatMillisecondsWithWarning(long runtimeMs) {
        String formattedRuntime = DateTimeHelper.getTimeRepresentationToMilliseconds(runtimeMs);
        if (runtimeMs >= PROCESSING_WARNING_THRESHOLD_MILLISECONDS) {
            return formattedRuntime + " ❗";
        }
        return formattedRuntime;
    }

    private String formatPauseWarningMessage() {
        return "**Buttons are processing slowly. Pausing warnings for 5 minutes.**" + formatThresholdWarningReasons();
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

    synchronized String formatStatistics(String timestamp) {
        long processedCount = processing.count();
        long notRunCount = Math.max(0, queuedCount - startedCount);
        double thresholdMissPercent = processedCount == 0 ? 0 : 100.0 * thresholdMissCount / processedCount;
        var decimalFormatter = new DecimalFormat("#.##");

        StringBuilder table = new StringBuilder();
        table.append(String.format(STATISTICS_ROW_FORMAT, "stage", "count", "mean", "p50", "p95"));
        appendRow(table, "preprocessing", preprocessing);
        appendStageRows(table, true);
        appendRow(table, "processing", processing);
        appendStageRows(table, false);

        return "Button Processor Statistics: " + timestamp
                + "\n> Total button presses: " + processedCount
                + "\n> Not processed: " + invalidCount + " from a non-player or non-owner, "
                + notRunCount + " not run (game busy or bot paused)"
                + "\n> Threshold misses: " + decimalFormatter.format(thresholdMissPercent) + "% ("
                + thresholdMissCount + ")"
                + "\n```\n" + table + "```";
    }

    private void appendStageRows(StringBuilder table, boolean preprocessingStages) {
        for (ButtonPressStage stage : ButtonPressStage.values()) {
            if (stage.isPreprocessing() != preprocessingStages) continue;
            appendRow(table, "  " + stage.getShortName(), stages.get(stage));
        }
    }

    private static void appendRow(StringBuilder table, String label, LatencyHistogram histogram) {
        if (histogram == null || histogram.count() == 0) {
            table.append(String.format(STATISTICS_ROW_FORMAT, label, 0, "-", "-", "-"));
            return;
        }
        table.append(String.format(
                STATISTICS_ROW_FORMAT,
                label,
                histogram.count(),
                String.format("%.1fms", histogram.meanMillis()),
                histogram.percentileMillis(0.5) + "ms",
                histogram.percentileMillis(0.95) + "ms"));
    }

    private record ThresholdWarningReason(String occurredAt, String buttonRepresentation, String totalRuntime) {}
}
