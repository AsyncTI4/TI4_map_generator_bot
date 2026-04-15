package ti4.discord.interactions.buttons.handlers.statistics;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;

@UtilityClass
public class StatsTrackingButtonHandler {

    private static final String STATS_TRACKING_BUTTON_PREFIX = "statsTracking_";

    @ButtonHandler(STATS_TRACKING_BUTTON_PREFIX)
    public static void handleStatsTrackingChoice(ButtonInteractionEvent event, Game game, String buttonID) {
        String payload = buttonID.substring(STATS_TRACKING_BUTTON_PREFIX.length());
        String[] parts = payload.split("_", 2);

        String selection = parts[0];
        String replacementUserId = parts[1];

        if (!event.getUser().getId().equals(replacementUserId)) {
            event.getHook()
                    .sendMessage("Only the replacement player can use these buttons.")
                    .setEphemeral(true)
                    .queue(Consumers.nop(), BotLogger::catchRestError);
            return;
        }

        Player replacementPlayer = game.getPlayer(replacementUserId);

        boolean changeStatsTracking = "me".equalsIgnoreCase(selection);
        if (changeStatsTracking) {
            replacementPlayer.setStatsTrackedUserID(replacementUserId);
            String trackedName = replacementPlayer.getUser().getName();
            replacementPlayer.setStatsTrackedUserName(trackedName);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Stats will be tracked for you.");
        } else {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Stats will be tracked for the replaced player.");
        }
        ButtonHelper.deleteMessage(event);
    }

    public static String statsTrackingButtonId(String choice, String replacementUserId) {
        return STATS_TRACKING_BUTTON_PREFIX + choice + "_" + replacementUserId;
    }
}
