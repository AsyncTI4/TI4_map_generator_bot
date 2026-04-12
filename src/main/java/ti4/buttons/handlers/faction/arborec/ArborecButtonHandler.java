package ti4.buttons.handlers.faction.arborec;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAgents;
import ti4.listeners.annotations.ButtonHandler;
import ti4.message.MessageHelper;

@UtilityClass
class ArborecButtonHandler {

    @ButtonHandler("arboAgentOn_")
    public static void arboAgentOn(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.split("_")[1];
        String unit = buttonID.split("_")[2];
        List<Button> buttons = ButtonHelperAgents.getArboAgentReplacementOptions(
                player, game, event, game.getTileByPosition(pos), unit);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getChannel(),
                player.getRepresentationUnfogged() + ", please choose which unit you wish to place down.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("arboAgentIn_")
    public static void arboAgentIn(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.substring(buttonID.indexOf('_') + 1);
        List<Button> buttons = ButtonHelperAgents.getUnitsToArboAgent(player, game.getTileByPosition(pos));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getChannel(),
                player.getRepresentationUnfogged() + ", please choose which unit you'd like to replace.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }
}
