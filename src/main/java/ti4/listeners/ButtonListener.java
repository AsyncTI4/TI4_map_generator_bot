package ti4.listeners;

import java.util.List;
import javax.annotation.Nonnull;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.commons.lang3.function.Consumers;
import ti4.helpers.ButtonHelper;
import ti4.message.logging.BotLogger;
import ti4.processors.ButtonProcessor;
import ti4.spring.jda.JdaService;

public class ButtonListener extends ListenerAdapter {

    private static final List<String> BUTTONS_TO_THINK_ABOUT = List.of("showGameAgain");

    private static ButtonListener instance;

    public static ButtonListener getInstance() {
        if (instance == null) instance = new ButtonListener();
        return instance;
    }

    @Override
    public void onButtonInteraction(@Nonnull ButtonInteractionEvent event) {
        if (!JdaService.isReadyToReceiveCommands()) {
            event.reply("You pressed: " + ButtonHelper.getButtonRepresentation(event.getButton())
                            + "\nPlease try again in a few minutes. The bot is rebooting.")
                    .setEphemeral(true)
                    .queue(Consumers.nop(), BotLogger::catchRestError);
            return;
        }

        // Only defer if button does not spawn a Modal
        if (!isModalSpawner(event)) {
            if (shouldShowBotIsThinking(event)) {
                event.deferReply(true).queue(Consumers.nop(), BotLogger::catchRestError);
            } else {
                event.deferEdit().queue(Consumers.nop(), BotLogger::catchRestError);
            }
        }

        ButtonProcessor.queue(event);
    }

    /**
     * @return whether a button should show the bot is thinking - need to add the following at end of execution:
     * `    if (event instanceof ButtonInteractionEvent buttonEvent) {
     * buttonEvent.getHook().deleteOriginal().queue(Consumers.nop(), BotLogger::catchRestError);
     * }`
     */
    private static boolean shouldShowBotIsThinking(ButtonInteractionEvent event) {
        return BUTTONS_TO_THINK_ABOUT.contains(event.getButton().getCustomId());
    }

    /**
     * @return whether the button spawns a Modal - modals must be a raw undeferred reply
     */
    private static boolean isModalSpawner(ButtonInteractionEvent event) {
        return event.getButton().getCustomId().contains("~MDL");
    }
}
