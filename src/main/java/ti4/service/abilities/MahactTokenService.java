package ti4.service.abilities;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.emoji.ColorEmojis;

@UtilityClass
public class MahactTokenService {

    public void removeFleetCC(Game game, Player player, String reason) {
        String message = player.getRepresentation();
        if (!player.getMahactCC().isEmpty()) {
            if (player.getFleetCC() == 0 && player.getMahactCC().size() == 1) {
                // exactly 1 token in fleet
                String color = player.getMahactCC().getFirst();
                Player p2 = game.getPlayerFromColorOrFaction(color);
                message += " has been forced to lose the " + p2.fogSafeEmoji();
                message += " token from their fleet pool " + reason;

                player.getMahactCC().remove(color);
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
                ButtonHelper.checkFleetInEveryTile(player, game);
            } else {
                message += " you are being forced to lose 1 fleet token " + reason + ", and have the";
                message += " option to remove another player's CC from your pool instead of your own.";

                List<Button> options = removeFleetTokenOptions(game, player, true, false);
                MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(player.getCorrectChannel(), message, options);
            }
        } else {
            message += " has removed a command token from their fleet pool " + reason;
            message += " " + player.gainFleetCC(-1);
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
            ButtonHelper.checkFleetInEveryTile(player, game);
        }
    }

    public List<Button> removeFleetTokenOptions(Game game, Player player, boolean includeSelf, boolean keepButtons) {
        List<Button> buttons = new ArrayList<>();
        String prefix = player.finChecker() + "loseMahactCC_";
        String suffix = keepButtons ? "_keep" : "";
        String label = "Lose your own token";
        String emoji = "";

        if (includeSelf && player.getFleetCC() > 0) {
            emoji = player.getFactionEmoji();
            buttons.add(Buttons.gray(prefix + player.getColor() + suffix, label, emoji));
        }
        for (String color : player.getMahactCC()) {
            Player p2 = game.getPlayerFromColorOrFaction(color);
            if (p2 != null && !game.isFowMode()) {
                String faction = game.getPlayerFromColorOrFaction(color).getFaction();
                emoji = p2.getFactionEmoji();
                label = "Lose " + faction + " token";
            } else {
                label = "Lose " + color + " token";
                emoji = ColorEmojis.getColorEmoji(color).emojiString();
            }

            buttons.add(Buttons.red(prefix + color + suffix, label, emoji));
        }
        return buttons;
    }

    @ButtonHandler("loseMahactCC_")
    private void loseMahactCC(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String msg = player.getRepresentationNoPing() + " removed ";
        String color = buttonID.split("_")[1];
        boolean delAll = !buttonID.endsWith("_keep");
        boolean delOne = false;

        if (player.getMahactCC().contains(color)) {
            player.removeMahactCC(color);
            Player p2 = game.getPlayerFromColorOrFaction(color);
            msg += "the " + p2.fogSafeEmoji() + " token from their fleet pool.";
            delOne = true;

        } else if (color.equals(player.getColor())) {
            msg += "one of their own tokens from their fleet pool ";
            msg += player.gainFleetCC(-1);
            if (player.getFleetCC() == 0) delOne = true;

        } else {
            msg = "Error. Unable to remove " + color + " token.";
            delAll = delOne = false;
        }

        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        ButtonHelper.checkFleetInEveryTile(player, game);
        if (delAll) ButtonHelper.deleteAllButtons(event);
        else if (delOne) ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }
}
