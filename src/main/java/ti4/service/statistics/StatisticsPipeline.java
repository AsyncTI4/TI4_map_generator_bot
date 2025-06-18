package ti4.service.statistics;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import ti4.helpers.TimedRunnable;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

public class StatisticsPipeline {

    private static final int TASK_TIMEOUT_SECONDS = 30;
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 20;
    private static final int EXECUTION_TIME_SECONDS_WARNING_THRESHOLD = 10;
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newSingleThreadExecutor();

    public static void queue(StatisticsPipeline.StatisticsEvent event) {
        event.event.getHook().sendMessage("Your statistics are being processed, please hold...").setEphemeral(true).queue();

        var timedRunnable = new TimedRunnable("Statistics event task for " + event.name,
            EXECUTION_TIME_SECONDS_WARNING_THRESHOLD,
            event.runnable);

        CompletableFuture.runAsync(timedRunnable, EXECUTOR_SERVICE)
            .orTimeout(TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .whenComplete((result, exception) -> {
                String userMention = event.event.getUser().getAsMention();
                if (exception instanceof TimeoutException) {
                    BotLogger.error("Timeout occurred while processing statistics: " + event.name, exception);
                    MessageHelper.sendMessageToChannel(event.event.getMessageChannel(),
                        "A timeout occurred while processing statistics for " + userMention + " running task: " + event.name);
                } else if (exception != null) {
                    BotLogger.error("Error occurred while processing statistics for " + userMention + " running task: " + event.name, exception);
                }
            });
    }

    public static boolean shutdown() {
        EXECUTOR_SERVICE.shutdownNow();
        try {
            return EXECUTOR_SERVICE.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            BotLogger.error("MapRenderPipeline shutdown interrupted.", e);
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public record StatisticsEvent(String name, IReplyCallback event, Runnable runnable) {}
}
