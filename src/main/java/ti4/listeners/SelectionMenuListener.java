package ti4.listeners;

import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ti4.AsyncTI4DiscordBot;
import ti4.message.BotLogger;
import ti4.selections.SelectionMenuProcessor;

public class SelectionMenuListener extends ListenerAdapter {

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (!AsyncTI4DiscordBot.isReadyToReceiveCommands()) {
            event.reply("Please try again in a moment. The bot is not ready to receive selections.").setEphemeral(true).queue();
            return;
        }

        event.deferEdit().queue();
        AsyncTI4DiscordBot.runAsync("Selection menu task", () -> handleSelectionEvent(event));
    }

    private void handleSelectionEvent(StringSelectInteractionEvent event) {
        try {
            SelectionMenuProcessor.process(event);
        } catch (Exception e) {
            String message = "Selection Menu issue in event: " + event.getComponentId() + "\n> Channel: " + event.getChannel().getAsMention() + "\n> Command: " + event.getValues();
            BotLogger.log(message, e);
        }
    }
}
