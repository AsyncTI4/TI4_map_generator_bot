package ti4.service.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;

public final class ChannelExportWatcher implements Runnable {

    public static final String RESUME_COMMAND = "/admin channel_export resume";
    public static final String CANCEL_COMMAND = "/admin channel_export cancel";

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().name("channel-export-watcher").factory());
    private static final Map<Path, ChannelExportWatcher> ACTIVE_WATCHERS = new ConcurrentHashMap<>();
    private static final Duration POLL_INTERVAL = Duration.ofMinutes(1);
    private static final Duration START_TIMEOUT = Duration.ofMinutes(20);
    private static final Duration FINISH_TIMEOUT = Duration.ofHours(24);

    private final ChannelExportLocation location;
    private final MessageChannel responseChannel;
    private final long maxUploadBytes;
    private final Instant startedAt = Instant.now();
    private volatile ScheduledFuture<?> future;

    private ChannelExportWatcher(ChannelExportLocation location, MessageChannel responseChannel, long maxUploadBytes) {
        this.location = location;
        this.responseChannel = responseChannel;
        this.maxUploadBytes = maxUploadBytes;
    }

    public static void watch(ChannelExportLocation location, MessageChannel responseChannel, long maxUploadBytes) {
        ACTIVE_WATCHERS.computeIfAbsent(location.log(), key -> {
            ChannelExportWatcher watcher = new ChannelExportWatcher(location, responseChannel, maxUploadBytes);
            long intervalSeconds = POLL_INTERVAL.toSeconds();
            watcher.future =
                    SCHEDULER.scheduleWithFixedDelay(watcher, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
            return watcher;
        });
    }

    @Override
    public void run() {
        try {
            switch (location.status()) {
                case DONE -> stopAfter(this::reportDone);
                case FAILED -> stopAfter(this::reportFailed);
                case CANCELLED -> stopAfter(this::reportCancelled);
                case PAUSED -> stopAfter(this::reportPaused);
                case RUNNING -> stopIfOlderThan(FINISH_TIMEOUT, "is still running after 24 hours. Check the host.");
                case NOT_STARTED ->
                    stopIfOlderThan(
                            START_TIMEOUT,
                            "never started. Check the `ArchiveGameChannel " + location.channelId()
                                    + "` run in GitHub Actions.");
            }
        } catch (Exception e) {
            BotLogger.error("Channel export watcher failed for " + exportName(), e);
            stop();
        }
    }

    private void stopIfOlderThan(Duration timeout, String reason) {
        if (Duration.between(startedAt, Instant.now()).compareTo(timeout) < 0) return;
        stopAfter(() -> MessageHelper.sendMessageToChannel(responseChannel, exportName() + " " + reason));
    }

    private void stopAfter(ThrowingReport report) {
        stop();
        try {
            report.send();
        } catch (IOException e) {
            BotLogger.error("Could not read channel export files for " + exportName(), e);
        }
    }

    private void stop() {
        future.cancel(false);
        ACTIVE_WATCHERS.remove(location.log(), this);
    }

    private void reportDone() throws IOException {
        long size = Files.size(location.archive());
        String message = exportName() + " finished: `" + location.displayPath(location.archive()) + "` ("
                + ByteSizes.format(size) + ").";
        if (size > maxUploadBytes) {
            MessageHelper.sendMessageToChannel(
                    responseChannel, message + " It is too large to attach, so grab it from the server.");
            return;
        }
        MessageHelper.sendMessageToChannel(responseChannel, message);
        MessageHelper.sendFileToChannel(responseChannel, location.archive().toFile());
    }

    private void reportCancelled() throws IOException {
        MessageHelper.sendMessageToChannel(
                responseChannel,
                exportName() + " was cancelled: " + location.stopReason(location.cancelledMarker())
                        + ". The finished parts are kept; " + resumeHint());
    }

    private void reportPaused() throws IOException {
        MessageHelper.sendMessageToChannel(
                responseChannel,
                exportName() + " paused: " + location.stopReason(location.pausedMarker())
                        + ". To continue, run `" + RESUME_COMMAND + " export_id:" + location.exportId()
                        + " max_size_mb:` with a higher cap, or 0 for no cap.");
    }

    private void reportFailed() throws IOException {
        StringBuilder message = new StringBuilder(exportName())
                .append(" failed: ")
                .append(location.stopReason(location.failedMarker()))
                .append(" (log: `")
                .append(location.displayPath(location.log()))
                .append("`).");
        if (location.canResume()) {
            message.append(" Finished parts are kept; ").append(resumeHint());
        }
        String logTail = location.logTail();
        if (!logTail.isEmpty()) {
            message.append("\n```\n").append(logTail.replace("```", "'''")).append("\n```");
        }
        MessageHelper.sendMessageToChannel(responseChannel, message.toString());
    }

    private String resumeHint() {
        return "pick up where it stopped with `" + RESUME_COMMAND + " export_id:" + location.exportId() + "`.";
    }

    private String exportName() {
        return "Export `" + location.exportId() + "` of <#" + location.channelId() + ">";
    }

    @FunctionalInterface
    private interface ThrowingReport {
        void send() throws IOException;
    }
}
