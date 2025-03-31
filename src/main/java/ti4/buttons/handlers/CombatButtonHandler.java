package ti4.buttons.handlers;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;

@UtilityClass
class CombatButtonHandler {

    @ButtonHandler("automateGroundCombat_")
    public static void automateGroundCombat(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String faction1 = buttonID.split("_")[1];
        String faction2 = buttonID.split("_")[2];
        Player p1 = game.getPlayerFromColorOrFaction(faction1);
        Player p2 = game.getPlayerFromColorOrFaction(faction2);
        String planet = buttonID.split("_")[3];
        String confirmed = buttonID.split("_")[4];
        if (player != p1 && player != p2) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "This button is only for combat participants");
            return;
        }
        Player opponent;
        if (player == p2) {
            opponent = p1;
        } else {
            opponent = p2;
        }
        ButtonHelper.deleteTheOneButton(event);
        if (opponent == null || opponent.isDummy() || confirmed.equalsIgnoreCase("confirmed")) {
            ButtonHelperModifyUnits.automateGroundCombat(p1, p2, planet, game, event);
        } else if (p1 != null && p2 != null) {
            Button automate = Buttons.green(opponent.getFinsFactionCheckerPrefix() + "automateGroundCombat_"
                + p1.getFaction() + "_" + p2.getFaction() + "_" + planet + "_confirmed", "Automate Combat");
            MessageHelper.sendMessageToChannelWithButton(event.getMessageChannel(), opponent.getRepresentation() + " Your opponent has voted to automate the entire combat. Press to confirm:", automate);
        }
    }

    @ButtonHandler("declinePDS_")
    public static void declinePDS(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = game.getTile(buttonID.split("_")[1]);
        String msg = player.getRepresentationNoPing() + " officially declines to fire SPACE CANNON" + (tile != null ? " at " + tile.getRepresentation() : "");
        if (game.isFowMode()) {
            String targetFaction = buttonID.split("_")[2];
            Player target = game.getPlayerFromColorOrFaction(targetFaction);
            if (target != null) {
                MessageHelper.sendMessageToChannel(target.getCorrectChannel(), target.getRepresentationUnfogged() + " " + msg);
            }
        }
        MessageHelper.sendMessageToChannel(game.isFowMode() ? player.getCorrectChannel() : event.getMessageChannel(), msg); 
    }

}
