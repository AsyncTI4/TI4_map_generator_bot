package ti4.service.turn;

import java.util.Collections;
import java.util.List;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperCommanders;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

@UtilityClass
public class PassService {

    public static void passPlayerForRound(GenericInteractionCreateEvent event, Game game, Player player) {
        player.setPassed(true);
        if (game.playerHasLeaderUnlockedOrAlliance(player, "olradincommander")) {
            ButtonHelperCommanders.olradinCommanderStep1(player, game);
        }

        String text = player.getRepresentation(true, false) + " PASSED";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), text);
        if (player.hasTech("absol_aida")) {
            String msg = player.getRepresentation() + " since you have AI Development Algorithm, you may research 1 unit upgrade now for 6 influence.";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            if (!player.hasAbility("propagation")) {
                MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + " you may use the button to get your tech.",
                    Collections.singletonList(Buttons.GET_A_UNIT_TECH_WITH_INF));
            } else {
                List<Button> buttons = ButtonHelper.getGainCCButtons(player);
                String message2 = player.getRepresentation() + "! Your current CCs are " + player.getCCRepresentation() + ". Use buttons to gain CCs";
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message2, buttons);
                game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
            }
        }
        if (player.hasAbility("deliberate_action") && (player.getTacticalCC() == 0 || player.getStrategicCC() == 0 || player.getFleetCC() == 0)) {
            String msg = player.getRepresentation() + " since you have deliberate action ability and passed while one of your pools was at 0, you may gain 1 CC to that pool.";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            List<Button> buttons = ButtonHelper.getGainCCButtons(player);
            String message2 = player.getRepresentationUnfogged() + "! Your current CCs are " + player.getCCRepresentation() + ". Use buttons to gain CCs";
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message2, buttons);
        }
        if (player.hasTech("dskolug")) {
            int oldComm = player.getCommodities();
            for (Player p2 : game.getRealPlayers()) {
                if (p2 == player) {
                    continue;
                }
                if (p2.isPassed()) {
                    player.setCommodities(
                        Math.min(player.getCommoditiesTotal(), player.getCommodities() + 1));
                }
            }
            if (player.getCommodities() > oldComm) {
                String msg = player.getRepresentation() + " since you have Applied Biothermics, you gained 1 comm for each passed player (Comms went from " + oldComm + " -> " + player.getCommodities() + ")";
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            }
        }
        EndTurnService.pingNextPlayer(event, game, player, true);
        ButtonHelper.updateMap(game, event, "End of Turn (PASS) " + player.getTurnCount() + ", Round " + game.getRound() + " for " + player.getFactionEmoji());
    }
}
