package ti4.autocomplete;

import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ti4.MapGenerator;
import ti4.message.BotLogger;

public class AutoCompleteListener extends ListenerAdapter {

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        if (!MapGenerator.readyToReceiveCommands) {
            event.replyChoice("Please try again in a moment. The bot is rebooting.", 0).queue();
            return;
        }

        try {
            AutoCompleteProvider.autoCompleteListener(event);
        } catch (Exception e) {
            String message = "Auto complete issue in event: " + event.getName() + "\n> Channel: " + event.getChannel().getAsMention() + "\n> Command: " + event.getCommandString();
            BotLogger.log(message, e);
        }
    }
}
