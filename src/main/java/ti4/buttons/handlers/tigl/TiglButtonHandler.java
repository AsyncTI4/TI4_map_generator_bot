package ti4.buttons.handlers.tigl;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.helpers.ButtonHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;

@UtilityClass
public class TiglButtonHandler {

    public enum Choice {
        ME,
        REPLACED_PLAYER
    }

    private static final String STATS_TRACKING_BUTTON_PREFIX = "tiglStatsTracking_";

    public static String statsTrackingButtonId(Choice choice, String replacementUserId, String replacedUserId) {
        return STATS_TRACKING_BUTTON_PREFIX
                + choice.name().toLowerCase()
                + "_"
                + replacementUserId
                + "_"
                + replacedUserId;
    }

    @ButtonHandler(STATS_TRACKING_BUTTON_PREFIX)
    public static void handleStatsTrackingChoice(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String payload = buttonID.substring(STATS_TRACKING_BUTTON_PREFIX.length());
        String[] parts = payload.split("_", 3);
        if (parts.length < 3) {
            MessageHelper.replyToMessage(event, "Invalid stats tracking selection.");
            return;
        }

        String selection = parts[0];
        String replacementUserId = parts[1];
        String replacedUserId = parts[2];

        if (!event.getUser().getId().equals(replacementUserId)) {
            event.getHook()
                    .sendMessage("Only the replacement player can use these buttons.")
                    .setEphemeral(true)
                    .queue(Consumers.nop(), BotLogger::catchRestError);
            return;
        }

        Player trackedPlayer = game.getPlayer(replacementUserId);
        if (trackedPlayer == null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Could not find the replacement player to update stats tracking.");
            return;
        }

        String statsTrackedUserId = selection.equalsIgnoreCase("me") ? replacementUserId : replacedUserId;
        trackedPlayer.setStatsTrackedUserID(statsTrackedUserId);

        if (statsTrackedUserId.equals(replacementUserId)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Stats will be tracked for you.");
        } else {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Stats will be tracked for the replaced player.");
        }
        ButtonHelper.deleteMessage(event);
    }
}
