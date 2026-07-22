package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Oblivion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.PromissoryNoteHelper;
import ti4.helpers.thundersedge.DSHelperBreakthroughs;
import ti4.image.PositionMapper;
import ti4.image.TileHelper;
import ti4.message.MessageHelper;
import ti4.service.milty.MiltyDraftTile;

@UtilityClass
public class OblivionPromissoryHandler {
    private static final String SHARD_OF_NOTHINGNESS = "thpnoblivion";
    private static final String DRAWN_RED_TILES = "oblivionPnDrawnRedTiles_";
    private static final String CHOOSE_RED_TILE = "chooseOblivionPnRedTile_";

    public static void offerShardOfNothingnessButtons(Game game, Player player) {
        if (game == null
                || player == null
                || !game.getStoredValue(DRAWN_RED_TILES + player.getFaction()).isBlank()) {
            return;
        }

        List<MiltyDraftTile> unusedRedTiles = new ArrayList<>(Helper.getUnusedTiles(game).stream()
                .filter(tile -> !tile.getTierList().isBlue())
                .toList());
        if (unusedRedTiles.size() < 3) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + ", there are not enough unused red-backed tiles to resolve _Shard of Nothingness_.");
            return;
        }

        Collections.shuffle(unusedRedTiles);
        List<String> drawnTileIds = unusedRedTiles.stream()
                .limit(3)
                .map(tile -> tile.getTile().getTileID())
                .toList();
        if (getPlacementButtons(game, player, drawnTileIds.getFirst()).isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + ", there are no legal locations to place a tile adjacent to 2 systems.");
            return;
        }
        game.setStoredValue(DRAWN_RED_TILES + player.getFaction(), String.join(",", drawnTileIds));

        List<Button> buttons = new ArrayList<>();
        List<MessageEmbed> tileEmbeds = new ArrayList<>();
        for (String tileId : drawnTileIds) {
            tileEmbeds.add(TileHelper.getTileById(tileId).getRepresentationEmbed(false));
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + CHOOSE_RED_TILE + tileId,
                    TileHelper.getTileById(tileId).getName()));
        }
        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", choose 1 red-backed tile to place. The other 2 will be purged.",
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
        String drawnTiles = game.getStoredValue(DRAWN_RED_TILES + player.getFaction());
        List<String> drawnTileIds = Arrays.stream(drawnTiles.split(","))
                .filter(tileId -> !tileId.isBlank())
                .toList();
        if (drawnTileIds.size() != 3 || !drawnTileIds.contains(chosenTileId)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> placementButtons = getPlacementButtons(game, player, chosenTileId);
        if (placementButtons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Set<String> purgedTileIds = new HashSet<>();
        String storedPurgedTiles = game.getStoredValue(Constants.PURGED_MAP_TILES);
        if (!storedPurgedTiles.isBlank()) {
            Collections.addAll(purgedTileIds, storedPurgedTiles.split(","));
        }
        drawnTileIds.stream().filter(tileId -> !tileId.equals(chosenTileId)).forEach(purgedTileIds::add);
        game.setStoredValue(Constants.PURGED_MAP_TILES, String.join(",", purgedTileIds));
        game.removeStoredValue(DRAWN_RED_TILES + player.getFaction());

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
                player.getRepresentation() + " purged the 2 unchosen red-backed tiles and _Shard of Nothingness_.");
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", choose 2 systems to place the selected tile adjacent to.",
                placementButtons);
    }

    private static List<Button> getPlacementButtons(Game game, Player player, String tileId) {
        List<Button> buttons = new ArrayList<>();
        Set<String> offeredPositions = new HashSet<>();
        for (Tile tile : game.getTileMap().values()) {
            for (String position : PositionMapper.getAdjacentTilePositions(tile.getPosition())) {
                Tile destination = game.getTileByPosition(position);
                if ((destination != null && !"silver_flame".equals(destination.getTileID()))
                        || !offeredPositions.add(position)) {
                    continue;
                }

                List<Tile> adjacentTiles = PositionMapper.getAdjacentTilePositions(position).stream()
                        .map(game::getTileByPosition)
                        .filter(adjacentTile -> adjacentTile != null
                                && !"silver_flame".equals(adjacentTile.getTileID())
                                && (adjacentTile.getTileModel() == null
                                        || !adjacentTile.getTileModel().isHyperlane()))
                        .toList();
                if (adjacentTiles.size() < 2) {
                    continue;
                }

                Tile firstSystem = adjacentTiles.getFirst();
                Tile secondSystem = adjacentTiles.get(1);
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + "starChartsStep3_" + tileId + "_" + firstSystem.getPosition()
                                + "_" + secondSystem.getPosition(),
                        "Place adjacent to " + firstSystem.getRepresentationForButtons(game, player) + " and "
                                + secondSystem.getRepresentationForButtons(game, player)));
            }
        }
        return buttons;
    }
}
