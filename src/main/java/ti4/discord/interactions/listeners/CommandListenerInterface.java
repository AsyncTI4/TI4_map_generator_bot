package ti4.discord.interactions.listeners;

import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.JdaService;
import ti4.helpers.DateTimeHelper;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.spring.service.deploy.ActiveLeaseService;

interface CommandListenerInterface {

    long DELAY_THRESHOLD_MILLISECONDS = 2000;

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
        long endTime = System.currentTimeMillis();
        long eventTimeMs = DateTimeHelper.getLongDateTimeFromDiscordSnowflake(event.getInteraction());
        long responseTimeMs = endTime - eventTimeMs;
        long executionTimeMs = endTime - processStartTimeMs;
        long preprocessTimeMs = processStartTimeMs - eventTimeMs;

        boolean slowResponse = responseTimeMs > DELAY_THRESHOLD_MILLISECONDS;
        boolean slowExecution = executionTimeMs > DELAY_THRESHOLD_MILLISECONDS;

        if (!slowResponse && !slowExecution) {
            return;
        }

        String eventTime = DateTimeHelper.getTimestampFromMillisecondsEpoch(eventTimeMs);
        String responseTime = DateTimeHelper.getTimeRepresentationToMilliseconds(responseTimeMs);
        String executionTime = DateTimeHelper.getTimeRepresentationToMilliseconds(executionTimeMs);
        String preprocessTime = DateTimeHelper.getTimeRepresentationToMilliseconds(preprocessTimeMs);

        String message = "\n> ⚠ **Slow Command Warning:** Took over " + DELAY_THRESHOLD_MILLISECONDS + "ms"
                + "\n> 🕒 Event start: `" + eventTime + "`"
                + "\n> 📦 Preprocessed for: `" + preprocessTime + "`"
                + "\n> 🛠 Executed in: `" + executionTime + "`"
                + "\n> ⚡ Responded after: `" + responseTime + "`";

        BotLogger.warning(new LogOrigin(event), message);
    }
}
