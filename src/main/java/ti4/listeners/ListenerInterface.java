package ti4.listeners;

import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.message.logging.BotLogger;
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
}
