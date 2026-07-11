package ti4.discord.interactions.buttons.handlers.faction.base.creuss;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.message.MessageHelper;

@UtilityClass
class CreussButtonHandler {

    @ButtonHandler("creussHeroStep2_")
    public static void resolveGhostHeroStep2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String position = buttonID.split("_")[1];
        String position2 = buttonID.split("_")[2];
        Tile tile = game.getTileByPosition(position);
        Tile tile2 = game.getTileByPosition(position2);

        tile.setPosition(position2);
        tile2.setPosition(position);
        game.setTile(tile);
        game.setTile(tile2);
        for (Player p2 : game.getRealPlayers()) {
            if (p2.getHomeSystemPosition().equals(tile2.getPosition())) {
                p2.setHomeSystemPosition(tile.getPosition());
            } else if (p2.getHomeSystemPosition().equals(tile.getPosition())) {
                p2.setHomeSystemPosition(tile2.getPosition());
            }
        }
        game.rebuildTilePositionAutoCompleteList();
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " Chose to swap "
                        + tile2.getRepresentationForButtons(game, player) + " with "
                        + tile.getRepresentationForButtons(game, player));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("creussHeroStep1_")
    public static void getGhostHeroTilesStep2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String pos1 = buttonID.split("_")[1];
        List<Button> buttons = new ArrayList<>();
        Tile tile1 = game.getTileByPosition(pos1);
        for (Tile tile : game.getTiles()) {
            if (tile.getPosition().contains("t")
                    || tile.getPosition().contains("b")
                    || tile == tile1
                    || tile.getPosition().contains("frac")) {
                continue;
            }
            if (FoWHelper.doesTileHaveWHs(game, tile.getPosition()) || FoWHelper.playerHasUnitsInSystem(player, tile)) {
                buttons.add(Buttons.gray(
                        "creussHeroStep2_" + pos1 + "_" + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", please chose the system you wish to swap places with "
                        + tile1.getRepresentationForButtons(game, player) + ".",
                buttons);
        ButtonHelper.deleteMessage(event);
    }
}
