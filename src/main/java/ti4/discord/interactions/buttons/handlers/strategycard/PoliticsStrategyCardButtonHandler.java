package ti4.discord.interactions.buttons.handlers.strategycard;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.emoji.MiscEmojis;
import ti4.service.game.SpeakerService;

@UtilityClass
class PoliticsStrategyCardButtonHandler {

    @ButtonHandler("assignSpeaker_")
    @ButtonHandler(Constants.SC3_ASSIGN_SPEAKER_BUTTON_ID_PREFIX)
    public static void sc3AssignSpeaker(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        SpeakerService.assignSpeaker(event, player, buttonID, game);
    }

    @ButtonHandler("assignTyrant_")
    public static void assignTyrant(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String faction = buttonID.replace("assignTyrant_", "");
        for (Player player_ : game.getPlayers().values()) {
            if (player_.getFaction().equals(faction)) {
                game.setTyrantUserID(player_.getUserID());
                String message = MiscEmojis.BenedictionToken + " Tyrant has been assigned to "
                        + player_.getRepresentation(false, true) + ".";
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
                if (game.isFowMode() && player != player_) {
                    MessageHelper.sendMessageToChannel(player_.getPrivateChannel(), message);
                }
                if (!game.isFowMode()) {
                    ButtonHelper.sendMessageToRightStratThread(player, game, message, "politics");
                }
            }
        }
        ButtonHelper.deleteMessage(event);
    }
}
