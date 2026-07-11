package ti4.service.game;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.service.emoji.ColorEmojis;
import ti4.service.emoji.MiscEmojis;

@UtilityClass
public class SpeakerService {

    public static void assignSpeaker(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String faction = buttonID.replace(Constants.SC3_ASSIGN_SPEAKER_BUTTON_ID_PREFIX, "");
        faction = faction.replace("assignSpeaker_", "");
        Player newSpeaker = game.getPlayerFromColorOrFaction(faction);
        if (newSpeaker.isSpeaker()) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "That player is already speaker.");
            return;
        }
        game.setStoredValue("hasntSetSpeaker", "");
        for (Player player_ : game.getPlayers().values()) {
            if (player_.isFactionExact(faction)) {
                game.setSpeakerUserID(player_.getUserID());
                String message = MiscEmojis.SpeakerToken + " Speaker has been assigned to "
                        + player_.getRepresentation(false, true) + ".";
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
                if (game.isFowMode() && player != player_) {
                    MessageHelper.sendMessageToChannel(player_.getPrivateChannel(), message);
                }
                if (!game.isFowMode() && !game.isTwilightsFallMode()) {
                    ButtonHelper.sendMessageToRightStratThread(player, game, message, "politics");
                }
            }
        }
        ButtonHelper.deleteMessage(event);

        if (game.isTwilightsFallMode()) {
            String assignSpeakerMessage =
                    player.toString() + ", please choose a faction below to receive the Tyrant token.";
            List<Button> assignSpeakerActionRow = getTyrannusAssignTyrantButtons(game, player);
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(), assignSpeakerMessage, assignSpeakerActionRow);
        }
    }

    private static List<Button> getTyrannusAssignTyrantButtons(Game game, Player politicsHolder) {
        List<Button> assignSpeakerButtons = new ArrayList<>();
        for (Player player : game.getRealPlayers()) {
            if ((!player.isSpeaker() || !politicsHolder.hasStrategyCard(3)) && !player.isTyrant()) {
                String faction = player.getFaction();
                if (Mapper.isValidFaction(faction)) {
                    Button button;
                    if (!game.isFowMode()) {
                        button = Buttons.gray(
                                politicsHolder.factionButtonChecker() + "assignTyrant_" + faction,
                                " ",
                                player.getFactionEmoji());
                    } else {
                        button = Buttons.gray(
                                politicsHolder.factionButtonChecker() + "assignTyrant_" + faction,
                                player.getColor(),
                                ColorEmojis.getColorEmoji(player.getColor()));
                    }
                    assignSpeakerButtons.add(button);
                }
            }
        }
        return assignSpeakerButtons;
    }
}
