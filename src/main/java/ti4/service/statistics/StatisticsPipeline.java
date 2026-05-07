package ti4.service.statistics;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.executors.CircuitBreaker;
import ti4.executors.ExecutionHistoryManager;
import ti4.executors.ExecutorUtility;
import ti4.helpers.TimedRunnable;
import ti4.logging.BotLogger;

@UtilityClass
public class StatisticsPipeline {

    private static final int SHUTDOWN_TIMEOUT_SECONDS = 10;
    private static final int EXECUTION_TIME_SECONDS_WARNING_THRESHOLD = 60;
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("ti4-statistics-gatherer-", 0).factory());

    public static void queue(SlashCommandInteractionEvent event, Runnable runnable) {
        if (CircuitBreaker.checkIsOpenAndPostWarningIfTrue(event.getMessageChannel())) {
            return;
        }
        event.getHook()
                .sendMessage("Your statistics are being processed, please hold...")
                .setEphemeral(true)
                .queue(Consumers.nop(), BotLogger::catchRestError);
        var timedRunnable = new TimedRunnable(eventToString(event), EXECUTION_TIME_SECONDS_WARNING_THRESHOLD, runnable);
        ExecutionHistoryManager.runWithExecutionHistory(EXECUTOR_SERVICE, timedRunnable);
    }

    public static ExecutorUtility.ShutdownResult shutdown() {
        return ExecutorUtility.shutdownAndAwaitTermination(
                "statistics pipeline", EXECUTOR_SERVICE, SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static String eventToString(SlashCommandInteractionEvent event) {
        return "StatisticsPipeline task for `" + event.getUser().getEffectiveName() + "`: `" + event.getCommandString()
                + "`";
    }
}
