package ti4.listeners;

import javax.annotation.Nonnull;

import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ti4.AsyncTI4DiscordBot;
import ti4.selections.SelectionMenuProcessor;

public class SelectionMenuListener extends ListenerAdapter {

    @Override
    public void onStringSelectInteraction(@Nonnull StringSelectInteractionEvent event) {
        if (!AsyncTI4DiscordBot.isReadyToReceiveCommands()) {
            event.reply("Please try again in a moment. The bot is not ready to receive selections.").setEphemeral(true).queue();
            return;
        }

        event.deferEdit().queue();

        SelectionMenuProcessor.process(event);
    }
}
