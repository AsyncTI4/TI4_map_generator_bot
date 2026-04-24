package ti4.discord.interactions.listeners;

import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.JdaService;
import ti4.helpers.DateTimeHelper;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.spring.service.deploy.ActiveLeaseService;

interface ListenerInterface {

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

    default <T extends GenericCommandInteractionEvent> void warnForLongRunningCommands(T event, long startTime) {
        long eventTime = DateTimeHelper.getLongDateTimeFromDiscordSnowflake(event.getInteraction());
        long eventDelay = startTime - eventTime;

        long endTime = System.currentTimeMillis();
        long processingRuntime = endTime - startTime;

        if (eventDelay > DELAY_THRESHOLD_MILLISECONDS || processingRuntime > DELAY_THRESHOLD_MILLISECONDS) {
            String responseTime = DateTimeHelper.getTimeRepresentationToMilliseconds(eventDelay);
            String executionTime = DateTimeHelper.getTimeRepresentationToMilliseconds(processingRuntime);
            String message = eventToString(event) + "`\n> Warning: "
                    + "This command took over "
                    + DELAY_THRESHOLD_MILLISECONDS + "ms to respond or execute\n> "
                    + DateTimeHelper.getTimestampFromMillisecondsEpoch(eventTime)
                    + " command was issued by user\n> " + DateTimeHelper.getTimestampFromMillisecondsEpoch(startTime)
                    + " `" + responseTime + "` to respond\n> "
                    + DateTimeHelper.getTimestampFromMillisecondsEpoch(endTime)
                    + " `" + executionTime + "` to execute" + (processingRuntime > eventDelay ? "😲" : "");
            BotLogger.warning(new LogOrigin(event), message);
        }
    }
}
