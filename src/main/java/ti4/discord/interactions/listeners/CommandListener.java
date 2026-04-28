package ti4.discord.interactions.listeners;

import java.time.Duration;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.AsyncTI4DiscordBot;
import ti4.discord.JdaService;
import ti4.helpers.DateTimeHelper;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.spring.service.deploy.ActiveLeaseService;

interface CommandListener {

    default boolean canReceiveCommands(GenericCommandInteractionEvent event) {
        if (!ActiveLeaseService.shouldHandleCurrentProcessInteraction()) {
            return false;
        }
        if (!JdaService.isReadyToReceiveCommands()
                && !"developer setting".equals(event.getInteraction().getFullCommandName())) {
            event.getInteraction()
                    .reply("Please try again in a moment.\nThe bot is rebooting and is not ready to receive commands.")
                    .setEphemeral(true)
                    .queue(Consumers.nop(), BotLogger::catchRestError);
            return false;
        }
        return true;
    }

    <T extends GenericCommandInteractionEvent> String eventToString(T event);

    default <T extends GenericCommandInteractionEvent> void warnForLongRunningCommands(
            T event, long processStartTimeMs) {
        if (!AsyncTI4DiscordBot.durationHasPassedSinceStartup(Duration.ofMinutes(2))) return;

        long endTime = System.currentTimeMillis();
        long eventTimeMs = DateTimeHelper.getLongDateTimeFromDiscordSnowflake(event.getInteraction());
        long responseTimeMs = endTime - eventTimeMs;
        long executionTimeMs = endTime - processStartTimeMs;
        long preprocessTimeMs = processStartTimeMs - eventTimeMs;

        long delayThresholdMs = 2000;
        boolean slowResponse = responseTimeMs >= delayThresholdMs;
        boolean slowExecution = executionTimeMs >= delayThresholdMs;

        if (!slowResponse && !slowExecution) {
            return;
        }

        String eventTime = DateTimeHelper.getTimestampFromMillisecondsEpoch(eventTimeMs);
        String responseTime = DateTimeHelper.getTimeRepresentationToMilliseconds(responseTimeMs);
        String executionTime = DateTimeHelper.getTimeRepresentationToMilliseconds(executionTimeMs);
        String preprocessTime = DateTimeHelper.getTimeRepresentationToMilliseconds(preprocessTimeMs);

        String message = "\n> ⚠ **Slow Command Warning:** Took over " + delayThresholdMs + "ms"
                + "\n> 🕒 Event start: `" + eventTime + "`"
                + "\n> 📦 Preprocessed for: `" + preprocessTime + "`"
                + "\n> 🛠 Executed in: `" + executionTime + "`"
                + "\n> ⚡ Responded after: `" + responseTime + "`";

        BotLogger.warning(new LogOrigin(event), message);
    }
}
