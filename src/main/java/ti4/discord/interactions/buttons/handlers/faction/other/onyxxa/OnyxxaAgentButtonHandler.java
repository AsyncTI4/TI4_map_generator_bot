package ti4.discord.interactions.buttons.handlers.faction.other.onyxxa;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.FoWHelper;
import ti4.message.MessageHelper;

@UtilityClass
public class OnyxxaAgentButtonHandler {

    public static void postInitialButtons(Game game, Player targetPlayer) {
        String msg = targetPlayer.getRepresentationUnfogged()
                + ", please choose the system that you wish to move a ship from.";
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (FoWHelper.playerHasShipsInSystem(targetPlayer, tile)) {
                buttons.add(Buttons.green(
                        "moveShipToAdjacentSystemStep2_" + tile.getPosition() + "_agent",
                        tile.getRepresentationForButtons(game, targetPlayer)));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(targetPlayer.getCorrectChannel(), msg, buttons);
    }
}
