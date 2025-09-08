package ti4.buttons.handlers.leader.agent;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

@UtilityClass
class CabalAgentButtonHandler {

    @ButtonHandler("cabalAgentCapture_")
    public static void resolveCabalAgentCapture(
            String buttonID, Player player, Game game, ButtonInteractionEvent event) {
        String unit = buttonID.split("_")[1];
        String faction = buttonID.split("_")[2];
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        if (p2 == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "Unable to resolve player, please resolve manually.");
            return;
        }
        int commodities = p2.getCommodities();
        MessageHelper.sendMessageToChannel(
                p2.getCorrectChannel(),
                p2.getRepresentationUnfogged() + " a " + unit
                        + " of yours has been captured by "
                        + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                        + "The Stillness of Stars, the Vuil'raith"
                        + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent. "
                        + "Rejoice, for your " + commodities + " commodities been washed.");
        p2.setTg(p2.getTg() + commodities);
        p2.setCommodities(0);
        ButtonHelperFactionSpecific.cabalEatsUnit(p2, game, player, 1, unit, event, true);
        ButtonHelper.deleteMessage(event);
    }
}
