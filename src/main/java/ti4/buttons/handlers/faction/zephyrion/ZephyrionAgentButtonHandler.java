package ti4.buttons.handlers.faction.zephyrion;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;

@UtilityClass
class ZephyrionAgentButtonHandler {

    @ButtonHandler("zephAgentRes_")
    public static void zephAgentRes(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        buttonID = buttonID.replace("zephAgentRes_", "");
        String colorPlayer = buttonID.split("_")[0];
        String unitTypeString = buttonID.split("_")[1].toLowerCase();
        Player p2 = game.getPlayerFromColorOrFaction(colorPlayer);
        UnitModel unit = p2.getUnitByBaseType(unitTypeString);
        game.removeStoredValue("bounties" + p2.getFaction() + unitTypeString);
        player.gainTG(3, true);
        ButtonHelperAgents.resolveArtunoCheck(player, 3);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + " claimed a bounty and so gained 3 trade goods. The bounty claimed was on a "
                        + StringUtils.capitalize(unitTypeString) + " belonging to "
                        + p2.getRepresentationNoPing() + ".");
        if (unit != null) {
            String message = p2.getRepresentation() + ", please destroy one of your ships of that type.";
            List<Button> removeButtons = new ArrayList<>(
                    ButtonHelperModifyUnits.getRemoveThisTypeOfUnitButton(p2, game, unitTypeString, true));
            if (!removeButtons.isEmpty()) {
                p2.gainTG((int) unit.getCost(), true);
                ButtonHelperAgents.resolveArtunoCheck(p2, (int) unit.getCost());
                MessageHelper.sendMessageToChannel(
                        p2.getCorrectChannel(),
                        p2.getRepresentationNoPing() + " received trade goods equal to the ship's cost.");
                message += ".";
                MessageHelper.sendMessageToChannelWithButtons(p2.getCorrectChannel(), message, removeButtons);
            }
        }
        ButtonHelper.deleteMessage(event);
    }
}
