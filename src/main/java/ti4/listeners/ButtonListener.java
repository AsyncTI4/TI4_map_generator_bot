package ti4.listeners;

import java.util.List;
import javax.annotation.Nonnull;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ti4.AsyncTI4DiscordBot;
import ti4.helpers.ButtonHelper;
import ti4.processors.ButtonProcessor;

public class ButtonListener extends ListenerAdapter {

    private static ButtonListener instance;

    public static ButtonListener getInstance() {
        if (instance == null) instance = new ButtonListener();
        return instance;
    }

    @Override
    public void onButtonInteraction(@Nonnull ButtonInteractionEvent event) {
        if (!AsyncTI4DiscordBot.isReadyToReceiveCommands()) {
            event.reply("You pressed: " + ButtonHelper.getButtonRepresentation(event.getButton())
                            + "\nPlease try again in a few minutes. The bot is rebooting.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        // Only defer if button does not spawn a Modal
        if (!isModalSpawner(event)) {
            if (shouldShowBotIsThinking(event)) {
                event.deferReply(true).queue();
            } else {
                event.deferEdit().queue();
            }
        }

        ButtonProcessor.queue(event);
    }

    private static final List<String> buttonsToThinkAbout = List.of("showGameAgain");

    /**
     * @return whether a button should show the bot is thinking - need to add the following at end of execution:
     * `    if (event instanceof ButtonInteractionEvent buttonEvent) {
     * buttonEvent.getHook().deleteOriginal().queue();
     * }`
     */
    private static boolean shouldShowBotIsThinking(ButtonInteractionEvent event) {
        return buttonsToThinkAbout.contains(event.getButton().getId());
    }

    /**
     * @return whether the button spawns a Modal - modals must be a raw undeferred reply
     */
    private static boolean isModalSpawner(ButtonInteractionEvent event) {
        return event.getButton().getId().endsWith("~MDL");
    }
}
