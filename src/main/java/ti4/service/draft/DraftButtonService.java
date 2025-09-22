package ti4.service.draft;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ButtonHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

@UtilityClass
public class DraftButtonService {
    // jwds: Jabberwocky's Draft System
    public static final String DRAFT_BUTTON_SERVICE_PREFIX = "jwds_";

    // Button handlers may return this string to indicate that there was no error,
    // and that the button should be deleted.
    public static final String DELETE_BUTTON = "@$!#deletebutton";
    // Button handlers may return this string to indicate that there was no error,
    // and that the message should be deleted.
    public static final String DELETE_MESSAGE = "@$!#deletemessage";

    public static boolean isError(String outcome) {
        return outcome != null
                && !outcome.isEmpty()
                && !outcome.equals(DELETE_BUTTON)
                && !outcome.equals(DELETE_MESSAGE);
    }

    @ButtonHandler(DRAFT_BUTTON_SERVICE_PREFIX)
    public static void handleDraftButtonClick(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String innerButtonID = buttonID.substring(DRAFT_BUTTON_SERVICE_PREFIX.length());
        DraftManager draftManager = game.getDraftManager();
        String outcome = draftManager.routeCommand(event, player, innerButtonID, DraftManager.CommandSource.BUTTON);
        if (outcome != null) {
            if (outcome.equals(DELETE_BUTTON)) {
                ButtonHelper.deleteTheOneButton(event);
            } else if (outcome.equals(DELETE_MESSAGE)) {
                ButtonHelper.deleteMessage(event);
            } else {
                // Another message, likely an error.
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), outcome);
            }
        }
    }

    public static void handleButtonResult(GenericInteractionCreateEvent event, String outcome) {
        if (outcome != null) {
            if (outcome.equals(DELETE_BUTTON)) {
                ButtonHelper.deleteTheOneButton(event);
            } else if (outcome.equals(DELETE_MESSAGE)) {
                ButtonHelper.deleteMessage(event);
            } else {
                // Another message, likely an error.
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), outcome);
            }
        }
    }
}
