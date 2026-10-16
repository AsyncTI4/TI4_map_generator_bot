package ti4.discord.interactions.buttons.handlers.actioncards.thirtyk;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.message.MessageHelper;

@UtilityClass
class ThirtyKAcTargets {

    static List<Button> otherPlayerButtons(Game game, Player player, String prefix, Predicate<Player> publicRule) {
        List<Button> buttons = new ArrayList<>();
        for (Player other : game.getRealPlayers()) {
            if (other == player) continue;
            if (!game.isFowMode() && publicRule != null && !publicRule.test(other)) continue;
            buttons.add(FoWHelper.fogSafeTargetButton(
                    player.factionButtonChecker() + prefix + other.getColor(), "gray", other));
        }
        return buttons;
    }

    static Player target(Game game, String buttonID, String prefix) {
        String color = buttonID.substring(prefix.length());
        int separator = color.indexOf('_');
        if (separator >= 0) color = color.substring(0, separator);
        return game.getPlayerFromColorOrFaction(color);
    }

    static String afterTarget(String buttonID, String prefix) {
        String rest = buttonID.substring(prefix.length());
        int separator = rest.indexOf('_');
        return separator < 0 ? "" : rest.substring(separator + 1);
    }

    static String name(Game game, Player target, Player viewer) {
        return FoWHelper.identityOrColorIfCanSeeStats(game, target, viewer, target.getRepresentationNoPing());
    }

    static void noTarget(ButtonInteractionEvent event, Player player, String cardName) {
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", _" + cardName + "_ has no legal target.");
    }

    static void send(Player player, String message, List<Button> buttons) {
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }
}
