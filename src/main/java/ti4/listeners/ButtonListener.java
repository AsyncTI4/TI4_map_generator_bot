package ti4.listeners;

import javax.annotation.Nonnull;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ti4.AsyncTI4DiscordBot;
import ti4.helpers.ButtonHelper;
import ti4.processors.ButtonProcessor;

public class ButtonListener extends ListenerAdapter {

    private static ButtonListener instance;

    public static ButtonListener getInstance() {
        if (instance == null)
            instance = new ButtonListener();
        return instance;
    }

    @Override
    public void onButtonInteraction(@Nonnull ButtonInteractionEvent event) {
        if (!AsyncTI4DiscordBot.isReadyToReceiveCommands()) {
            event.reply("You pressed: " + ButtonHelper.getButtonRepresentation(event.getButton()) + "\nPlease try again in a few minutes. The bot is rebooting.").setEphemeral(true).queue();
            return;
        }

        // Only defer if button does not spawn a Modal
        if (!event.getButton().getId().endsWith("~MDL")) {
            event.deferEdit().queue();
        }
        event.getChannel().sendTyping().queue();

        ButtonProcessor.queue(event);
    }
}
