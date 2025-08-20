package ti4.service.statistics;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.executors.CircuitBreaker;
import ti4.executors.ExecutionHistoryManager;
import ti4.helpers.TimedRunnable;
import ti4.message.logging.BotLogger;

public class StatisticsPipeline {

    private static final int SHUTDOWN_TIMEOUT_SECONDS = 20;
    private static final int EXECUTION_TIME_SECONDS_WARNING_THRESHOLD = 10;
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newSingleThreadExecutor();

    public static void queue(SlashCommandInteractionEvent event, Runnable runnable) {
        if (CircuitBreaker.checkIsOpenAndPostWarningIfTrue(event.getMessageChannel())) {
            return;
        }
        event.getHook()
                .sendMessage("Your statistics are being processed, please hold...")
                .setEphemeral(true)
                .queue();
        var timedRunnable = new TimedRunnable(eventToString(event), EXECUTION_TIME_SECONDS_WARNING_THRESHOLD, runnable);
        ExecutionHistoryManager.runWithExecutionHistory(EXECUTOR_SERVICE, timedRunnable);
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

    private static String eventToString(SlashCommandInteractionEvent event) {
        return "StatisticsPipeline task for `" + event.getUser().getEffectiveName() + "`: `" + event.getCommandString()
                + "`";
    }
}
