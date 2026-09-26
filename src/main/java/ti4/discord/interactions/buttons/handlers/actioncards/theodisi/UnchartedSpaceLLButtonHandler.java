package ti4.discord.interactions.buttons.handlers.actioncards.theodisi;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Oblivion.OblivionTileHelper;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.image.TileHelper;
import ti4.message.MessageHelper;
import ti4.model.TileModel;

@UtilityClass
public class UnchartedSpaceLLButtonHandler {

    @ButtonHandler("resolveUnchartedSpaceAC")
    public static void resolveUnchartedSpace(ButtonInteractionEvent event, Game game, Player player) {
        List<String> drawnTiles = OblivionTileHelper.drawUnusedTiles(game, 1, null);
        if (drawnTiles.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing()
                            + " cannot resolve _Uncharted Space_ because there are no unused red-backed tiles.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        String tileId = drawnTiles.getFirst();
        List<Button> buttons = OblivionTileHelper.getPlacementButtons(
                game, player, tileId, player.factionButtonChecker() + "placeUnchartedSpaceACTile");

        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing()
                            + " cannot resolve _Uncharted Space_ because there is no legal edge position.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        TileModel tileModel = TileHelper.getTileById(tileId);
        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", please choose an edge position for " + tileModel.getName() + ".",
                List.of(tileModel.getRepresentationEmbed(false)),
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("placeUnchartedSpaceACTile")
    public static void placeUnchartedSpace(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String placementData = buttonID.substring("placeUnchartedSpaceACTile".length());
        int tileIdEnd = placementData.lastIndexOf('_');
        if (tileIdEnd <= 0) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String tileId = placementData.substring(0, tileIdEnd);
        String position = placementData.substring(tileIdEnd + 1);
        Tile placedTile = OblivionTileHelper.placeTile(game, tileId, position);
        if (placedTile == null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing()
                            + ", that edge position is no longer legal for _Uncharted Space_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        String message = player.getRepresentationNoPing()
                + " placed "
                + placedTile.getRepresentationForButtons(game, player)
                + " with _Uncharted Space_.";
        if (placedTile.getPlanetUnitHolders().isEmpty()) {
            message += "\n-# A frontier token was added because that system contains no planets.";
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        ButtonHelper.deleteMessage(event);
    }
}
