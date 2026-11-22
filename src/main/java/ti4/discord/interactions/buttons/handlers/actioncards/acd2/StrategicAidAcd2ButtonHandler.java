package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.message.MessageHelper;

@UtilityClass
class StrategicAidAcd2ButtonHandler {

    @ButtonHandler("resolveStrategicAid")
    public static void resolveStrategicAid(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        Set<Player> neighbours = player.getAbilityNeighbours();
        for (Player target : game.getRealPlayers()) {
            if (target == player || !neighbours.contains(target)) continue;
            buttons.add(FoWHelper.fogSafeTargetButton(
                    player.factionButtonChecker() + "strategicAidTarget_" + target.getFaction(),
                    "green",
                    target,
                    player));
        }
        ButtonHelper.deleteMessage(event);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", you have no neighbors to choose for _Strategic Aid_.");
            return;
        }
        buttons.add(Buttons.DONE_RED_DELETE_BUTTONS);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", choose 1 of your neighbors. You and that player each gain 1 command token.",
                buttons);
    }

    @ButtonHandler("strategicAidTarget_")
    public static void resolveStrategicAidTarget(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.substring("strategicAidTarget_".length()));
        if (target == null || target == player || !player.getAbilityNeighbours().contains(target)) {
            return;
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                game.getActionsChannel(),
                player.getRepresentationNoPing() + " chose " + FoWHelper.actorOrAnon(game, target, "a neighbor")
                        + " for _Strategic Aid_. Each of them gains 1 command token.");
        offerOneCommandToken(player);
        offerOneCommandToken(target);
    }

    private static void offerOneCommandToken(Player player) {
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", gain 1 command token from _Strategic Aid_. Your current"
                        + " command tokens are " + player.getCCRepresentation() + ".",
                ButtonHelper.getGainCCButtons(player));
    }
}
