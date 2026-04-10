package ti4.factions.arborec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.UnitReplacementHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;

@UtilityClass
public class ArborecAgentButtonHandler {

    public static List<Button> getTilesToArboAgent(Player player, Game game) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, Tile> tileEntry : new HashMap<>(game.getTileMap()).entrySet()) {
            if (FoWHelper.playerHasShipsInSystem(player, tileEntry.getValue())) {
                Tile tile = tileEntry.getValue();
                buttons.add(Buttons.green(
                        finChecker + "arboAgentIn_" + tileEntry.getKey(),
                        tile.getRepresentationForButtons(game, player)));
            }
        }
        buttons.add(Buttons.red(finChecker + "deleteButtons", "Decline"));
        return buttons;
    }

    @ButtonHandler("arboAgentIn_")
    public static void arboAgentIn(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.substring(buttonID.indexOf('_') + 1);
        List<Button> buttons = UnitReplacementHelper.getUnitsToReplace(player, game.getTileByPosition(pos), 2);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getChannel(),
                player.getRepresentationUnfogged() + ", please choose which unit you'd like to replace.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }
}
