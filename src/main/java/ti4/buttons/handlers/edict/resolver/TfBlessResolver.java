package ti4.buttons.handlers.edict.resolver;

import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.buttons.UnfiledButtonHandlers;
import ti4.buttons.handlers.edict.EdictResolveButtonHandler;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.emoji.CardEmojis;

public class TfBlessResolver implements EdictResolver {

    @Getter
    final String edict = "tf-bless";

    private static final List<Button> blessButtons = Arrays.asList(
            Buttons.green("blessBoonTg", "Gain 3 Trade Goods"),
            Buttons.gray("blessBoonAC", "Draw 2 Action Cards", CardEmojis.TF_Action_Card),
            Buttons.blue("blessBoonCC", "Gain 1 Command Token"));

    public void handle(ButtonInteractionEvent event, Game game, Player player) {
        clearResolved(game);

        if (game.isFowMode()) {
            String tyrantMsg = "-# As the resolving player, you get to gain all three boons.";
            String otherMsg = "-# You only get to resolve one of the three boons.";
            for (Player p2 : game.getRealPlayers()) {
                String msg2 = playerPing(p2, p2.equals(player) ? tyrantMsg : otherMsg);
                MessageHelper.sendMessageToChannelWithButtons(p2.getCorrectChannel(), msg2, blessButtons);
            }
        } else {
            String addl = "-# " + player.getRepresentation() + " gets to resolve all 3 boons, ";
            addl += "other players must choose only 1.";
            MessageHelper.sendMessageToChannelWithButtons(
                    game.getMainGameChannel(), gamePing(game, addl), blessButtons);
        }
    }

    private static void clearResolved(Game game) {
        for (Player p2 : game.getRealPlayers()) {
            p2.removeStoredValue("blessBoonTg");
            p2.removeStoredValue("blessBoonAC");
            p2.removeStoredValue("blessBoonCC");
        }
    }

    private static boolean alreadyResolved(Player player) {
        boolean isTyrant = EdictResolveButtonHandler.isEdictResolver(player);
        boolean resolvedTG = player.hasStoredValue("blessBoonTg");
        boolean resolvedAC = player.hasStoredValue("blessBoonAC");
        boolean resolvedCC = player.hasStoredValue("blessBoonCC");
        if (!isTyrant && (resolvedAC || resolvedCC || resolvedTG)) {
            return true;
        } else if (isTyrant && resolvedAC && resolvedCC && resolvedTG) {
            return true;
        }
        return false;
    }

    @ButtonHandler("blessBoonTg")
    private static void blessBoonTg(ButtonInteractionEvent event, Game game, String buttonID, Player player) {
        if (alreadyResolved(player)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "You have already resolved this edict.");
            return;
        }
        player.addStoredValue(buttonID, "y");

        MessageHelper.sendMessageToChannel(event.getChannel(), player.getRepresentation() + " gained 3 trade goods.");
        player.setTg(player.getTg() + 3);
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, 3);

        afterResolve(event, game, player);
    }

    @ButtonHandler("blessBoonAC")
    private static void blessBoonAC(ButtonInteractionEvent event, Game game, String buttonID, Player player) {
        if (alreadyResolved(player)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "You have already resolved this edict.");
            return;
        }
        player.addStoredValue(buttonID, "y");
        ActionCardHelper.drawActionCards(player, 3);
        afterResolve(event, game, player);
    }

    @ButtonHandler("blessBoonCC")
    private static void blessBoonCC(ButtonInteractionEvent event, Game game, String buttonID, Player player) {
        if (alreadyResolved(player)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "You have already resolved this edict.");
            return;
        }
        player.addStoredValue(buttonID, "y");
        UnfiledButtonHandlers.gainCC(event, player, game);
        afterResolve(event, game, player);
    }

    private static void afterResolve(ButtonInteractionEvent event, Game game, Player player) {
        if (game.isFowMode()) {
            ButtonHelper.deleteMessage(event);
        }
        if (game.getRealPlayers().stream().allMatch(TfBlessResolver::alreadyResolved)) {
            clearResolved(game);
            ButtonHelper.deleteMessage(event);
        }
    }
}
