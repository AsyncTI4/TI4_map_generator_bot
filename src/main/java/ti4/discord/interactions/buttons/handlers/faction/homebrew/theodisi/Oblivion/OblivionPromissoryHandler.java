package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Oblivion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.PromissoryNoteHelper;
import ti4.helpers.thundersedge.DSHelperBreakthroughs;
import ti4.image.TileHelper;
import ti4.message.MessageHelper;

@UtilityClass
public class OblivionPromissoryHandler {
    private static final String SHARD_OF_NOTHINGNESS = "thpnoblivion";
    private static final String DRAWN_RED_TILES = "oblivionPnDrawnRedTiles_";
    private static final String SELECTED_RED_TILE = "oblivionPnSelectedRedTile_";
    private static final String CHOOSE_RED_TILE = "chooseOblivionPnRedTile_";
    private static final String PLACE_RED_TILE = "placeOblivionPnRedTile_";

    public static void offerShardOfNothingnessButtons(Game game, Player player) {
        if (game == null
                || player == null
                || !game.getStoredValue(DRAWN_RED_TILES + player.getFaction()).isBlank()
                || !game.getStoredValue(SELECTED_RED_TILE + player.getFaction()).isBlank()) {
            return;
        }

        List<String> drawnTileIds = OblivionTileHelper.drawUnusedTiles(game, 3, null);
        if (drawnTileIds.size() < 3) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + ", there are not enough unused red-backed tiles to resolve _Shard of Nothingness_.");
            return;
        }

        game.setStoredValue(DRAWN_RED_TILES + player.getFaction(), String.join(",", drawnTileIds));
        game.removeStoredValue(SELECTED_RED_TILE + player.getFaction());
        sendShardTileChoices(game, player);
    }

    private static void sendShardTileChoices(Game game, Player player) {
        List<String> drawnTileIds = getStoredTiles(game, DRAWN_RED_TILES, player);
        List<String> legalTileIds = drawnTileIds.stream()
                .filter(tileId -> OblivionTileHelper.hasLegalPlacement(game, tileId))
                .toList();
        if (legalTileIds.isEmpty()) {
            clearShardState(game, player);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + ", there are no legal edge positions at which to resolve _Shard of Nothingness_. The promissory note was not purged.");
            return;
        }

        List<Button> buttons = new ArrayList<>();
        List<MessageEmbed> tileEmbeds = new ArrayList<>();
        for (String tileId : legalTileIds) {
            tileEmbeds.add(TileHelper.getTileById(tileId).getRepresentationEmbed(false));
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + CHOOSE_RED_TILE + tileId,
                    TileHelper.getTileById(tileId).getName()));
        }
        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", please choose 1 red-backed tile to place. The other 2 will be purged.",
                tileEmbeds,
                buttons);
    }

    @ButtonHandler(CHOOSE_RED_TILE)
    public static void chooseShardOfNothingnessTile(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            return;
        }

        String chosenTileId = buttonID.substring(CHOOSE_RED_TILE.length());
        List<String> drawnTileIds = getStoredTiles(game, DRAWN_RED_TILES, player);
        if (drawnTileIds.size() != 3
                || !drawnTileIds.contains(chosenTileId)
                || !game.getStoredValue(SELECTED_RED_TILE + player.getFaction()).isBlank()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> placementButtons = getPlacementButtons(game, player, chosenTileId);
        if (placementButtons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation()
                            + ", that system tile no longer has a legal edge position. Please choose another tile.");
            return;
        }

        game.setStoredValue(SELECTED_RED_TILE + player.getFaction(), chosenTileId);

        ButtonHelper.deleteMessage(event);
        sendShardPlacementButtons(game, player, chosenTileId, placementButtons);
    }

    @ButtonHandler(PLACE_RED_TILE)
    public static void placeShardOfNothingnessTile(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String placementData = buttonID.substring(PLACE_RED_TILE.length());
        int tileIdEnd = placementData.lastIndexOf('_');
        if (game == null || player == null || tileIdEnd <= 0) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String tileId = placementData.substring(0, tileIdEnd);
        if (!tileId.equals(game.getStoredValue(SELECTED_RED_TILE + player.getFaction()))) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> placementButtons = getPlacementButtons(game, player, tileId);
        if (placementButtons.isEmpty()) {
            game.removeStoredValue(SELECTED_RED_TILE + player.getFaction());
            ButtonHelper.deleteMessage(event);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + ", that system tile no longer has a legal edge position. Please choose another tile.");
            sendShardTileChoices(game, player);
            return;
        }

        String placementMessage =
                player.getRepresentation() + ", please choose an edge position for the selected system tile.";
        String placementButtonPrefix = player.factionButtonChecker() + PLACE_RED_TILE + tileId + "_";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                placementButtons,
                placementMessage,
                placementButtonPrefix,
                buttonID)) {
            return;
        }

        String position = placementData.substring(tileIdEnd + 1);
        Tile tile = OblivionTileHelper.placeTile(game, tileId, position);
        if (tile == null) {
            ButtonHelper.deleteMessage(event);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + ", that placement is no longer legal. Please choose another edge position.");
            List<Button> updatedPlacementButtons = getPlacementButtons(game, player, tileId);
            if (updatedPlacementButtons.isEmpty()) {
                game.removeStoredValue(SELECTED_RED_TILE + player.getFaction());
                sendShardTileChoices(game, player);
            } else {
                sendShardPlacementButtons(game, player, tileId, updatedPlacementButtons);
            }
            return;
        }

        List<String> drawnTileIds = getStoredTiles(game, DRAWN_RED_TILES, player);
        OblivionTileHelper.purgeTiles(
                game, drawnTileIds.stream().filter(id -> !id.equals(tileId)).toList());
        clearShardState(game, player);

        Player owner = game.getPNOwner(SHARD_OF_NOTHINGNESS);
        game.setPurgedPN(SHARD_OF_NOTHINGNESS);
        player.removePromissoryNote(SHARD_OF_NOTHINGNESS);
        if (owner != null) {
            owner.removePromissoryNote(SHARD_OF_NOTHINGNESS);
            owner.removeOwnedPromissoryNoteByID(SHARD_OF_NOTHINGNESS);
        }
        DSHelperBreakthroughs.doLanefirBtCheck(game, player);
        OblivionUnitHandler.doOblivionMechCheck(game, player);
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, player, false);
        if (owner != null && owner != player) {
            PromissoryNoteHelper.sendPromissoryNoteInfo(game, owner, false);
        }

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " placed " + tile.getRepresentationForButtons(game, player)
                        + ", purged the other 2 red-backed tiles, and purged _Shard of Nothingness_.");
    }

    private static void sendShardPlacementButtons(
            Game game, Player player, String tileId, List<Button> placementButtons) {
        String placementMessage =
                player.getRepresentation() + ", please choose an edge position for the selected system tile.";
        String placementButtonPrefix = player.factionButtonChecker() + PLACE_RED_TILE + tileId + "_";
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                placementMessage,
                NewStuffHelper.buttonPagination(placementButtons, placementButtonPrefix, 0));
    }

    private static List<Button> getPlacementButtons(Game game, Player player, String tileId) {
        return OblivionTileHelper.getPlacementButtons(
                game, player, tileId, player.factionButtonChecker() + PLACE_RED_TILE);
    }

    private static List<String> getStoredTiles(Game game, String key, Player player) {
        return Arrays.stream(game.getStoredValue(key + player.getFaction()).split(","))
                .filter(tileId -> !tileId.isBlank())
                .toList();
    }

    private static void clearShardState(Game game, Player player) {
        game.removeStoredValue(DRAWN_RED_TILES + player.getFaction());
        game.removeStoredValue(SELECTED_RED_TILE + player.getFaction());
    }
}
