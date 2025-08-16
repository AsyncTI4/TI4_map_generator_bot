package ti4.buttons.handlers.leader.hero;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperHeroes;
import ti4.helpers.FoWHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;

@UtilityClass
class BenedictionButtonHandler {

    @ButtonHandler("mahactBenedictionFrom_")
    public static void mahactBenedictionFrom(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        ButtonHelperHeroes.mahactBenediction(buttonID, event, game, player);
        String pos1 = buttonID.split("_")[1];
        String pos2 = buttonID.split("_")[2];
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getFactionEmojiOrColor() + " moved all units in space from "
                        + game.getTileByPosition(pos1).getRepresentationForButtons(game, player) + " to "
                        + game.getTileByPosition(pos2).getRepresentationForButtons(game, player)
                        + " using Airo Shir Aur, the Mahact hero. If they moved themselves and wish to move ground forces, they may do so either with slash command or modify units button.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("benedictionStep1_")
    public static void benedictionStep1(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos1 = buttonID.split("_")[1];
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged() + " please choose the system you wish to send the ships in "
                        + game.getTileByPosition(pos1).getRepresentationForButtons(game, player) + " to.",
                getBenediction2ndTileOptions(player, game, pos1));
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getBenediction2ndTileOptions(Player player, Game game, String pos1) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        Player origPlayer = player;
        Tile tile1 = game.getTileByPosition(pos1);
        List<Player> players2 = ButtonHelper.getOtherPlayersWithShipsInTheSystem(player, game, tile1);
        if (!players2.isEmpty()) {
            player = players2.getFirst();
        }
        for (String pos2 : FoWHelper.getAdjacentTiles(game, pos1, player, false)) {
            if (pos1.equalsIgnoreCase(pos2)) {
                continue;
            }
            Tile tile2 = game.getTileByPosition(pos2);
            if (FoWHelper.otherPlayersHaveShipsInSystem(player, tile2, game)) {
                buttons.add(Buttons.gray(
                        finChecker + "mahactBenedictionFrom_" + pos1 + "_" + pos2,
                        tile2.getRepresentationForButtons(game, origPlayer)));
            }
        }
        return buttons;
    }
}
