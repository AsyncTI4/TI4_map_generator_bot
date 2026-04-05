package ti4.listeners;

import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.helpers.DateTimeHelper;
import ti4.message.logging.BotLogger;
import ti4.message.logging.LogOrigin;
import ti4.service.game.GameNameService;
import ti4.spring.jda.JdaService;

public interface ListenerInterface {

    long DELAY_THRESHOLD_MILLISECONDS = 2000;

    default boolean receiveCommands(GenericCommandInteractionEvent event) {
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

    <T extends GenericCommandInteractionEvent> String eventToString(T event, String gameName);

    default <T extends GenericCommandInteractionEvent> void warnForLongRunningCommands(T event, long startTime) {
        long eventTime = DateTimeHelper.getLongDateTimeFromDiscordSnowflake(event.getInteraction());
        long eventDelay = startTime - eventTime;

        long endTime = System.currentTimeMillis();
        long processingRuntime = endTime - startTime;

        String gameName = GameNameService.getGameName(event);
        if (eventDelay > DELAY_THRESHOLD_MILLISECONDS || processingRuntime > DELAY_THRESHOLD_MILLISECONDS) {
            String responseTime = DateTimeHelper.getTimeRepresentationToMilliseconds(eventDelay);
            String executionTime = DateTimeHelper.getTimeRepresentationToMilliseconds(processingRuntime);
            String message = eventToString(event, gameName) + "`\n> Warning: "
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
