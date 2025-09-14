package ti4.service.draft;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ButtonHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;

@UtilityClass
public class DraftButtonService {
    // jwds: Jabberwocky's Draft System
    public static final String DRAFT_BUTTON_PREFIX = "jwds_";

    // Button handlers may return this string to indicate that there was no error,
    // and that the button should be deleted.
    public static final String DELETE_BUTTON = "@$!#deletebutton";
    // Button handlers may return this string to indicate that there was no error,
    // and that the message should be deleted.
    public static final String DELETE_MESSAGE = "@$!#deletemessage";

    @ButtonHandler(DRAFT_BUTTON_PREFIX)
    public static void handleDraftButtonClick(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        // DEBUG
        BotLogger.info("Handling draft button click: " + buttonID);

        String innerButtonID = buttonID.substring(DRAFT_BUTTON_PREFIX.length());
        DraftManager draftManager = game.getDraftManager();
        String outcome = draftManager.routeButtonPress(event, player, innerButtonID);
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
