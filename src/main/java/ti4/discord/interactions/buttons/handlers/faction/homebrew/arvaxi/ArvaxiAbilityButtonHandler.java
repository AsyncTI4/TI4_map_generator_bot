package ti4.discord.interactions.buttons.handlers.faction.homebrew.arvaxi;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;
import ti4.service.actioncard.ForceGiveActionCardService;

@UtilityClass
public class ArvaxiAbilityButtonHandler {

    @ButtonHandler("underhandedManeuverPickNeighbor")
    public static void underhandedManeuverPickNeighbor(ButtonInteractionEvent event, Player player, Game game) {
        List<Button> buttons = new ArrayList<>();
        for (Player neighbor : player.getNeighbouringPlayers(true)) {
            buttons.add(Buttons.green(
                    "FFCC_" + player.getFaction() + "_underhandedManeuverTarget_" + neighbor.getFaction(),
                    neighbor.getFactionNameOrColor(), neighbor.fogSafeEmoji()));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + ", choose a neighbor to take an action card from.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("underhandedManeuverTarget_")
    public static void underhandedManeuverTarget(
            ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String targetFaction = buttonID.replace("underhandedManeuverTarget_", "");
        Player target = game.getPlayerFromColorOrFaction(targetFaction);
        if (target == null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Could not find target, please resolve manually.");
            return;
        }
        ForceGiveActionCardService.sendGiveACButtons(
                player,
                target,
                game,
                "Underhanded Maneuver has been used against you. Please choose which action card to hand over.");
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                "Sent " + target.getColor() + " the buttons for resolving Underhanded Maneuver.");
        ButtonHelper.deleteMessage(event);
    }
}
