package ti4.service.draft;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.helpers.ButtonHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;

@UtilityClass
public class DraftButtonService {
    // jwds: Jabberwocky's Draft System
    public final String DRAFT_BUTTON_SERVICE_PREFIX = "jwds_";

    // Button handlers may return this string to indicate that there was no error,
    // and that the button should be deleted.
    public final String DELETE_BUTTON = "@$!#deletebutton";
    // Button handlers may return this string to indicate that there was no error,
    // and that the message should be deleted.
    public final String DELETE_MESSAGE = "@$!#deletemessage";
    // Button handlers may return a string starting with this prefix to indicate
    // that a user made a mistake (e.g. clicked a button when it wasn't their turn).
    // The user will get a discrete ephemeral message with the rest of the string.
    public final String USER_MISTAKE_PREFIX = "@$!#pebcak:";

    public boolean isError(String outcome) {
        return outcome != null
                && !outcome.isEmpty()
                && !outcome.equals(DELETE_BUTTON)
                && !outcome.equals(DELETE_MESSAGE);
    }

    @ButtonHandler(DRAFT_BUTTON_SERVICE_PREFIX)
    public void handleDraftButtonClick(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String innerButtonID = buttonID.substring(DRAFT_BUTTON_SERVICE_PREFIX.length());
        DraftManager draftManager = game.getDraftManager();
        String outcome = draftManager.routeCommand(event, player, innerButtonID, DraftManager.CommandSource.BUTTON);
        handleButtonResult(event, outcome);
    }

    public void handleButtonResult(GenericInteractionCreateEvent event, String outcome) {
        if (outcome == null) {
            return;
        }
        if (outcome.equals(DELETE_BUTTON)) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        } else if (outcome.equals(DELETE_MESSAGE)) {
            ButtonHelper.deleteMessage(event);
        } else if (outcome.startsWith(USER_MISTAKE_PREFIX)) {
            String userMessage = outcome.substring(USER_MISTAKE_PREFIX.length());
            if (userMessage.isEmpty()) {
                userMessage = "You can't use this button.";
            }
            if (event instanceof ButtonInteractionEvent bevent) {
                bevent.getHook()
                        .sendMessage(userMessage)
                        .setEphemeral(true)
                        .queue(Consumers.nop(), BotLogger::catchRestError);
            } else {
                userMessage = event.getUser().getAsMention() + ": " + userMessage;
                event.getMessageChannel().sendMessage(userMessage).queue(Consumers.nop(), BotLogger::catchRestError);
            }
        } else {
            // Another message, likely an error.
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), outcome);
        }
    }
}
