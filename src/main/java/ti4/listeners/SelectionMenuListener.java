package ti4.listeners;

import javax.annotation.Nonnull;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.commons.lang3.function.Consumers;
import ti4.message.logging.BotLogger;
import ti4.selections.SelectionMenuProcessor;
import ti4.spring.jda.JdaService;

public class SelectionMenuListener extends ListenerAdapter {

    @Override
    public void onStringSelectInteraction(@Nonnull StringSelectInteractionEvent event) {
        if (!JdaService.isReadyToReceiveCommands()) {
            event.reply("Please try again in a moment. The bot is not ready to receive selections.")
                    .setEphemeral(true)
                    .queue(Consumers.nop(), BotLogger::catchRestError);
            return;
        }

        event.deferEdit().queue(Consumers.nop(), BotLogger::catchRestError);

        SelectionMenuProcessor.queue(event);
    }
}
