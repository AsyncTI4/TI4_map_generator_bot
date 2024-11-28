package ti4.listeners;

import javax.annotation.Nonnull;

import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ti4.AsyncTI4DiscordBot;
import ti4.autocomplete.AutoCompleteProvider;
import ti4.message.BotLogger;

public class AutoCompleteListener extends ListenerAdapter {

    @Override
    public void onCommandAutoCompleteInteraction(@Nonnull CommandAutoCompleteInteractionEvent event) {
        if (!AsyncTI4DiscordBot.isReadyToReceiveCommands()) {
            event.replyChoice("Please try again in a moment. The bot is not ready to serve AutoComplete.", 0).queue();
            return;
        }
        try {
            AutoCompleteProvider.resolveAutoCompleteEvent(event, true);
        } catch (Exception e) {
            String message = "Auto complete issue in event: " + event.getName() + "\n> Channel: " + event.getChannel().getAsMention() + "\n> Command: " + event.getCommandString();
            BotLogger.log(message, e);
        }
    }
}
