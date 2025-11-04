package ti4.service.breakthrough;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;

public class AlRaithService {

    // Cabal Breakthrough
    public static void serveBeginCabalBreakthroughButtons(ButtonInteractionEvent event, Game game, Player player) {
        Button startCabalBreakthrough =
                Buttons.red(player.finChecker() + "beginCabalBreakthroughMove", "Move Ingress Tokens");
        String message =
                "After you finish setting the ingress tokens on the map, click this button to move up to 2 of them into systems that contain gravity rifts.";
        MessageHelper.sendMessageToChannelWithButton(player.getCorrectChannel(), message, startCabalBreakthrough);
    }

    @ButtonHandler("beginCabalBreakthroughMove")
    public static void serveCabalMoveIngressButtons(ButtonInteractionEvent event, Game game, Player player) {
        List<Tile> tilesWithIngress = new ArrayList<>();
        for (Tile t : game.getTileMap().values()) {
            if (t.getSpaceUnitHolder().getTokenList().contains(Constants.TOKEN_INGRESS)) {
                tilesWithIngress.add(t);
            }
        }
        int totalIngress = tilesWithIngress.size();

        // Send notice
        String notice = player.getRepresentation()
                + " you can move up to 2 ingress tokens into systems that contain gravity rifts.";
        if (totalIngress < 2) {
            notice = player.getRepresentation() + " Only " + totalIngress
                    + " ingress tokens spawned. You can move any of those ingress tokens into systems that contain gravity rifts.";
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), notice);
        ButtonHelper.deleteMessage(event);

        // List<Button> buttons = new ArrayList<>();
        // for (int i = 0; i < Math.min(3, totalIngress); i++) {

        // }
    }
}
