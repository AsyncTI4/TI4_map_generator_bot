package ti4.buttons.handlers.leader.agent;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

@UtilityClass
class NekroAgentButtonHandler {

    @ButtonHandler("nekroAgentRes_")
    public static void nekroAgentRes(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String faction = buttonID.split("_")[1];
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        String msg2 = player.getFactionEmojiOrColor() + " selected "
                + p2.getFactionEmojiOrColor() + " as user of "
                + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                + "Nekro Malleon, the Nekro" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "")
                + " agent.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg2);
        String message = p2.getRepresentationUnfogged() + " increased your trade goods by 2 " + p2.gainTG(2)
                + ". Use buttons in your `#cards-info` thread to discard 1 action card, or remove 1 command token.";
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), message);
        ButtonHelperAbilities.pillageCheck(p2, game);
        ButtonHelperAgents.resolveArtunoCheck(p2, 2);
        MessageHelper.sendMessageToChannelWithButtons(
                p2.getCardsInfoThread(),
                p2.getRepresentationUnfogged() + " use buttons to discard",
                ActionCardHelper.getDiscardActionCardButtons(p2, false));
        String finsFactionCheckerPrefix = "FFCC_" + p2.getFaction() + "_";
        Button loseTactic = Buttons.red(finsFactionCheckerPrefix + "decrease_tactic_cc", "Lose 1 Tactic Token");
        Button loseFleet = Buttons.red(finsFactionCheckerPrefix + "decrease_fleet_cc", "Lose 1 Fleet Token");
        Button loseStrat = Buttons.red(finsFactionCheckerPrefix + "decrease_strategy_cc", "Lose 1 Strategy Token");
        Button DoneGainingCC = Buttons.red(finsFactionCheckerPrefix + "deleteButtons", "Done Losing Tokens");
        List<Button> buttons = List.of(loseTactic, loseFleet, loseStrat, DoneGainingCC);
        String message2 = p2.getRepresentationUnfogged() + "! Your current command tokens are "
                + p2.getCCRepresentation() + ". Use buttons to lose tokens.";
        MessageHelper.sendMessageToChannelWithButtons(p2.getCorrectChannel(), message2, buttons);
        game.setStoredValue("originalCCsFor" + p2.getFaction(), p2.getCCRepresentation());
        ButtonHelper.deleteMessage(event);
    }
}
