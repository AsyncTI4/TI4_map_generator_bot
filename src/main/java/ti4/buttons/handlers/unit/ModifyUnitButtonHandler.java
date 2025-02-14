package ti4.buttons.handlers.unit;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.helpers.ButtonHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

@UtilityClass
class ModifyUnitButtonHandler {

    @ButtonHandler("getModifyTiles")
    public static void getModifyTilesHandler(Player player, Game game) {
        List<Button> buttons = ButtonHelper.getTilesToModify(player, game);
        String message =
                player.getRepresentation() + " Use the buttons to select the tile in which you wish to modify units. ";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    @ButtonHandler("modifyUnitsAllTiles")
    public static void modifyUnitsAllTiles(Player player, Game game) {
        List<Button> buttons = ButtonHelper.getAllTilesToModify(player, game, "genericModifyAllTiles", true);
        String message =
                player.getRepresentation() + " Use the buttons to select the tile in which you wish to modify units. ";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }
}
