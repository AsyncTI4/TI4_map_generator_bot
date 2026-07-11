package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.FoWHelper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;

@UtilityClass
class SpatialCollapseAcd2ButtonHandler {

    private static List<Button> getSpatialCollapseTilesStep1(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTiles()) {
            if (tile.getPosition().contains("t")
                    || tile.getPosition().contains("b")
                    || tile.isHomeSystem(game)
                    || tile.isMecatol(game)) {
                continue;
            }
            if (FoWHelper.playerHasShipsInSystem(player, tile)) {
                buttons.add(Buttons.gray(
                        "spatialCollapseStep2_" + tile.getPosition(), tile.getRepresentationForButtons(game, player)));
            }
        }
        return buttons;
    }

    @ButtonHandler("spatialCollapseStep2_")
    public static void resolveSpatialCollapseStep2(
            Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String pos1 = buttonID.split("_")[1];
        List<Button> buttons = new ArrayList<>();
        Tile tile1 = game.getTileByPosition(pos1);
        for (String tilePos2 : FoWHelper.getAdjacentTiles(game, pos1, player, false, false)) {
            Tile tile = game.getTileByPosition(tilePos2);
            if (tile.getPosition().contains("t")
                    || tile.getPosition().contains("b")
                    || tile == tile1
                    || tile.isHomeSystem(game)
                    || tile.isMecatol(game)) {
                continue;
            }

            buttons.add(Buttons.gray(
                    "spatialCollapseStep3_" + pos1 + "_" + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player)));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", please choose which system you wish to swap places with "
                        + tile1.getRepresentationForButtons(game, player) + ".",
                buttons);
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("spatialCollapseStep3_")
    public static void resolveSpatialCollapseStep3(
            Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String position = buttonID.split("_")[1];
        String position2 = buttonID.split("_")[2];
        Tile tile = game.getTileByPosition(position);
        Tile tile2 = game.getTileByPosition(position2);
        tile.setPosition(position2);
        tile2.setPosition(position);
        game.setTile(tile);
        game.setTile(tile2);
        game.rebuildTilePositionAutoCompleteList();
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " Chose to swap "
                        + tile2.getRepresentationForButtons(game, player) + " with "
                        + tile.getRepresentationForButtons(game, player));
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }
}
