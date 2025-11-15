package ti4.service.draft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.buttons.Buttons;
import ti4.draft.DraftItem;
import ti4.draft.DraftItem.Category;
import ti4.helpers.ButtonHelper;
import ti4.image.TileHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.message.logging.LogOrigin;
import ti4.model.MapTemplateModel;
import ti4.model.MapTemplateModel.MapTemplateTile;
import ti4.service.draft.MantisMapBuildContext.PlayerTiles;
import ti4.spring.jda.JdaService;

@UtilityClass
public class MantisMapBuildService {

    public static final String ACTION_PREFIX = "mbuild_";
    private static final String IMAGE_UNIQUE_STRING = "mantismapbuild";

    public void initializeMapBuilding(@Nonnull MantisMapBuildContext mapBuildContext) {
        MessageChannel gameChannel = mapBuildContext.game().getMainGameChannel();
        if (anyNeedsToDiscard(mapBuildContext.availablePlayerTiles(), mapBuildContext.mapTemplateModel())) {
            MessageHelper.sendMessageToChannel(
                    gameChannel, "Starting the map building by having players discard excess tiles.");
            sendDiscardButtons(mapBuildContext);
        } else {
            MessageHelper.sendMessageToChannel(gameChannel, "Starting map building!");
            updateMapBuild(null, mapBuildContext);
        }
    }

    public String handleAction(
            @Nonnull GenericInteractionCreateEvent event,
            @Nonnull MantisMapBuildContext mapBuildContext,
            @Nonnull String action) {
        if (action.startsWith(ACTION_PREFIX)) {
            action = action.substring(ACTION_PREFIX.length());
        }
        String[] actionParts = action.split("_", 2);
        String actionType = actionParts[0];
        String outcome =
                switch (actionType) {
                    case "place" -> handlePlaceTile(event, mapBuildContext, actionParts[1]);
                    case "mulligan" -> handleMulliganTile(event, mapBuildContext, actionParts[1]);
                    case "repost" -> handleRepost(mapBuildContext);
                    case "discard" -> handleDiscardTile(event, mapBuildContext, actionParts[1]);
                    default -> "Error: Unknown map build action type: " + actionType;
                };

        return outcome;
    }

    private String handlePlaceTile(
            @Nonnull GenericInteractionCreateEvent event,
            @Nonnull MantisMapBuildContext mapBuildContext,
            @Nonnull String actionParams) {
        String[] params = actionParams.split("_", 2);
        if (params.length != 2) {
            return "Error: Invalid parameters for place tile action: " + actionParams;
        }

        String position = params[0];
        String tileId = params[1];

        String validationError = validateAction(event, mapBuildContext, tileId, position);
        if (validationError != null) {
            return validationError;
        }

        // Get these before placing the tile, since the tile won't be available afterwards
        PlayerTiles playerTiles = getPlayerTilesWithTileId(mapBuildContext.availablePlayerTiles(), tileId);
        Player player = mapBuildContext.game().getPlayer(playerTiles.playerUserId());
        Category tileCategory = playerTiles.blueTileIds().contains(tileId) ? Category.BLUETILE : Category.REDTILE;
        DraftItem placedTile = DraftItem.generate(tileCategory, tileId);

        if (!placeTile(mapBuildContext, position, tileId)) {
            return "Error: Unable to place tile " + tileId + " at " + position + ".";
        }

        // Persist 'null' for drawn tile and clear it from the context
        mapBuildContext = mapBuildContext.afterTilePlaced();

        // Post the pick
        MessageHelper.sendMessageToChannel(
                mapBuildContext.game().getMainGameChannel(),
                player.getRepresentation() + " placed " + placedTile.getShortDescription() + " at Position " + position
                        + ".");

        updateMapBuild(event, mapBuildContext);
        return DraftButtonService.DELETE_MESSAGE;
    }

    private String handleMulliganTile(
            GenericInteractionCreateEvent event, @Nonnull MantisMapBuildContext mapBuildContext, String actionParams) {

        String tileId = actionParams;

        String validationError = validateAction(event, mapBuildContext, tileId, null);
        if (validationError != null) {
            return validationError;
        }

        // Persist the mulligan to our parent state
        mapBuildContext.persistMulligan().accept(tileId);
        // Get a new context object with the mulligan set, and drawn tile cleared
        MantisMapBuildContext updatedContext = mapBuildContext.afterMulligan();
        PlayerTiles playerTiles = getPlayerTilesWithTileId(mapBuildContext.availablePlayerTiles(), tileId);
        Player player = mapBuildContext.game().getPlayer(playerTiles.playerUserId());
        Category tileCategory = playerTiles.blueTileIds().contains(tileId) ? Category.BLUETILE : Category.REDTILE;
        DraftItem oldTileItem = DraftItem.generate(tileCategory, tileId);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " mulliganed tile " + oldTileItem.getLongDescription() + ".");
        updateMapBuild(event, updatedContext);
        return DraftButtonService.DELETE_MESSAGE;
    }

    private String handleRepost(MantisMapBuildContext mapBuildContext) {
        // Calling this without an event should cause an update
        updateMapBuild(null, mapBuildContext);
        return DraftButtonService.DELETE_MESSAGE;
    }

    private String handleDiscardTile(
            GenericInteractionCreateEvent event, @Nonnull MantisMapBuildContext mapBuildContext, String actionParams) {

        String tileId = actionParams;

        String validationError = validateAction(event, mapBuildContext, tileId, null);
        if (validationError != null) {
            return validationError;
        }

        PlayerTiles playerTiles = mapBuildContext.availablePlayerTiles().stream()
                .filter(pt ->
                        pt.blueTileIds().contains(tileId) || pt.redTileIds().contains(tileId))
                .findFirst()
                .orElse(null);

        if (!(playerTiles.blueTileIds().contains(tileId)
                || playerTiles.redTileIds().contains(tileId))) {
            return "Error: You do not have tile " + tileId + " drafted.";
        }

        Category tileCategory = playerTiles.blueTileIds().contains(tileId) ? Category.BLUETILE : Category.REDTILE;

        mapBuildContext.persistDiscard().accept(tileId);

        // Update check for needed discards
        mapBuildContext = mapBuildContext.withRegeneratedPlayerTiles();
        boolean anyNeedToDiscard =
                anyNeedsToDiscard(mapBuildContext.availablePlayerTiles(), mapBuildContext.mapTemplateModel());
        PlayerTiles playerRemainingTiles = mapBuildContext.availablePlayerTiles().stream()
                .filter(pr -> pr.playerUserId().equals(playerTiles.playerUserId()))
                .findFirst()
                .orElse(null);

        boolean categoryNeedsDiscard = tileCategory == Category.BLUETILE
                ? playerRemainingTiles.blueTileIds().size()
                        > mapBuildContext.mapTemplateModel().bluePerPlayer()
                : playerRemainingTiles.redTileIds().size()
                        > mapBuildContext.mapTemplateModel().redPerPlayer();

        if (!anyNeedToDiscard) {
            MessageHelper.sendMessageToChannel(
                    mapBuildContext.game().getMainGameChannel(),
                    "All players have discarded excess tiles. Continuing map building!");
            // Start building with a null event to force new messaging in the main thread
            updateMapBuild(null, mapBuildContext);
            return DraftButtonService.DELETE_MESSAGE;
        } else if (!categoryNeedsDiscard) {
            // This player is done discarding this one type
            String tileType = tileCategory == Category.BLUETILE ? "blue" : "red";
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "You've finished discarding " + tileType + " tiles.");
            return DraftButtonService.DELETE_MESSAGE;
        } else {
            // Only remove this one button
            return DraftButtonService.DELETE_BUTTON;
        }
    }

    private String validateAction(
            @Nonnull GenericInteractionCreateEvent event,
            @Nonnull MantisMapBuildContext mapBuildContext,
            @Nonnull String tileId,
            @Nullable String tilePosition) {

        if (tilePosition != null) {
            // Validate that the map position belongs to the interacting player
            Integer templatePlayerNum =
                    getPlayerNumberForTilePosition(mapBuildContext.mapTemplateModel(), tilePosition);
            if (templatePlayerNum == null) {
                return "Error: Could not determine player number for tile position " + tilePosition + ".";
            }
            Player tilePlayer = mapBuildContext
                    .getPlayerForPosition()
                    .apply(templatePlayerNum)
                    .orElse(null);
            if (tilePlayer == null) {
                return "Error: Could not find player for tile position " + tilePosition + ".";
            }
            String tilePlayerUserId = tilePlayer.getUserID();
            if (event instanceof ButtonInteractionEvent buttonEvent
                    && !buttonEvent.getUser().getId().equals(tilePlayerUserId)) {
                return DraftButtonService.USER_MISTAKE_PREFIX + "It's not your turn to place a tile.";
            }
        }

        // Validate that the interacting player owns this tile
        PlayerTiles playerTiles = getPlayerTilesWithTileId(mapBuildContext.availablePlayerTiles(), tileId);
        if (playerTiles == null) {
            return "Error: Could not find any player that has tile " + tileId + " drafted.";
        }
        String playerWithTileId = playerTiles.playerUserId();
        if (event instanceof ButtonInteractionEvent buttonEvent
                && !buttonEvent.getUser().getId().equals(playerWithTileId)) {
            return DraftButtonService.USER_MISTAKE_PREFIX + "It's not your tile to place.";
        }

        // Check if the buttons are stale; ensure they pertain to the latest positions needing tiles
        var groupedPositions = getGroupedPositions(mapBuildContext.game(), mapBuildContext.mapTemplateModel());
        List<String> positionsToPlace = getNextPositionGroup(mapBuildContext.game(), groupedPositions);
        Integer templatePlayerNum =
                getPlayerNumberForTilePosition(mapBuildContext.mapTemplateModel(), positionsToPlace.getFirst());
        if (templatePlayerNum == null) {
            return "Error: Could not determine player number for tile position " + positionsToPlace.getFirst() + ".";
        }
        Player nextPlayerToPlace =
                mapBuildContext.getPlayerForPosition().apply(templatePlayerNum).orElse(null);
        if (nextPlayerToPlace == null) {
            return "Error: Could not find player for tile position " + positionsToPlace.getFirst() + ".";
        }
        if (!nextPlayerToPlace.getUserID().equals(playerWithTileId)) {
            return DraftButtonService.USER_MISTAKE_PREFIX
                    + "It's not your turn. Are your buttons stale? Consider refreshing the draft info.";
        }

        return null;
    }

    private boolean placeTile(MantisMapBuildContext mapBuildContext, String position, String tileId) {
        Tile existingTile = mapBuildContext.game().getTileByPosition(position);
        if (existingTile != null && !TileHelper.isDraftTile(existingTile.getTileModel())) {
            // There's already a non-draft tile here
            BotLogger.warning(
                    new LogOrigin(mapBuildContext.game()),
                    "Cannot place tile " + tileId + " at " + position
                            + " because there's already a non-draft tile there.");
            return false;
        }

        mapBuildContext.game().setTile(new Tile(tileId, position));
        return true;
    }

    private void updateMapBuild(GenericInteractionCreateEvent event, MantisMapBuildContext mapBuildContext) {
        MessageChannel responseChannel =
                event == null ? mapBuildContext.game().getMainGameChannel() : event.getMessageChannel();

        // Get the tile positions, grouped by player, and then grouped again by
        // placement order.
        Map<Integer, Map<Integer, List<String>>> placementGroups =
                getGroupedPositions(mapBuildContext.game(), mapBuildContext.mapTemplateModel());
        List<String> nextGroup = getNextPositionGroup(mapBuildContext.game(), placementGroups);

        // Check if the build is complete
        if (nextGroup == null || nextGroup.isEmpty()) {
            // All done!
            MessageHelper.sendMessageToChannel(responseChannel, "Map building complete!");
            // Clean up the map build images
            if (event != null) {
                event.getMessageChannel().getHistory().retrievePast(50).queue(messageHistory -> {
                    for (Message msg : messageHistory) {
                        List<Attachment> attachments = msg.getAttachments();
                        for (Attachment att : attachments) {
                            if (att.getFileName().contains(IMAGE_UNIQUE_STRING)) {
                                msg.delete().queue();
                                break;
                            }
                        }
                    }
                });
            }
            // Update the main game map
            ButtonHelper.updateMap(mapBuildContext.game(), event, "Mantis Map Build Completed");
            // Do any post-build work
            mapBuildContext.buildCompleteCallback().accept(event);
            return;
        }

        // Use the first position in the group to determine which player is placing
        // next.
        Integer templatePlayerNum =
                getPlayerNumberForTilePosition(mapBuildContext.mapTemplateModel(), nextGroup.getFirst());
        if (templatePlayerNum == null) {
            MessageHelper.sendMessageToChannel(
                    responseChannel,
                    "Error: Could not determine player number for next tile placement position at "
                            + nextGroup.getFirst() + ".");
            return;
        }
        Player player =
                mapBuildContext.getPlayerForPosition().apply(templatePlayerNum).orElse(null);
        if (player == null) {
            MessageHelper.sendMessageToChannel(
                    responseChannel,
                    "Error: Could not find player for next tile placement position at " + nextGroup.getFirst() + ".");
            return;
        }

        PlayerTiles playerRemainingTiles = mapBuildContext.availablePlayerTiles().stream()
                .filter(prt -> prt.playerUserId().equals(player.getUserID()))
                .findFirst()
                .orElse(null);
        if (playerRemainingTiles == null) {
            MessageHelper.sendMessageToChannel(
                    responseChannel,
                    "Error: Could not find remaining tiles for player " + player.getRepresentation()
                            + " to place next tile.");
            return;
        }

        DraftItem nextTile = drawTile(event, mapBuildContext, player, playerRemainingTiles);
        if (nextTile == null) {
            MessageHelper.sendMessageToChannel(
                    responseChannel, "Error: Could not draw next tile for player " + player.getRepresentation() + ".");
            return;
        }

        // Get the number of mulligans used by this player so far
        int mulligansUsed = playerRemainingTiles.mulligansUsed();

        boolean canMulligan = mulligansUsed < mapBuildContext.mulliganLimit();
        if (playerRemainingTiles.blueTileIds().size()
                        + playerRemainingTiles.redTileIds().size()
                <= 1) {
            // No more tiles to draw
            canMulligan = false;
        }

        // If only one position, do it now and call this recursively
        if (nextGroup.size() == 1 && !canMulligan) {
            boolean success = placeTile(mapBuildContext, nextGroup.getFirst(), nextTile.ItemId);
            if (!success) return;

            MessageHelper.sendMessageToChannel(
                    mapBuildContext.game().getMainGameChannel(),
                    player.getRepresentation() + " placed "
                            + nextTile.getShortDescription() + " at Position " + nextGroup.getFirst()
                            + " (automatically; no alternatives).");

            // Regenerate possible tiles
            mapBuildContext = mapBuildContext.withRegeneratedPlayerTiles();

            // Recursively do this update until we encounter a decision or finish
            updateMapBuild(event, mapBuildContext);
            return;
        }

        String mulliganString = canMulligan
                ? " You can mulligan to draw a different tile (used " + mulligansUsed + " of "
                        + mapBuildContext.mulliganLimit() + ")."
                : mapBuildContext.mulliganLimit() > 0 ? " (No mulligans remaining.)" : "";

        FileUpload mapImage = MantisBuildImageGeneratorService.tryGenerateImage(
                mapBuildContext, IMAGE_UNIQUE_STRING, nextGroup, nextTile.ItemId);
        if (mapImage != null) {
            sendMapImage(event, responseChannel, mapImage);
        }
        sendDraftButtons(event, mapBuildContext, player, nextTile, nextGroup, canMulligan, mulliganString);
    }

    private void sendDraftButtons(
            GenericInteractionCreateEvent event,
            MantisMapBuildContext mapBuildContext,
            Player player,
            DraftItem nextTile,
            List<String> openPositions,
            boolean canMulligan,
            String mulliganString) {
        MessageChannel responseChannel =
                event == null ? mapBuildContext.game().getMainGameChannel() : event.getMessageChannel();

        if (nextTile == null) {
            MessageHelper.sendMessageToChannel(
                    responseChannel,
                    "Error: Could not find any available tiles for player " + player.getRepresentation()
                            + " to place next tile.");
            return;
        }

        // TODO: Handle option to block adjacent anomalies when alternatives exist
        List<Button> buttons = new ArrayList<>();
        for (String pos : openPositions) {
            String buttonId =
                    mapBuildContext.makeButtonId().apply(ACTION_PREFIX + "place_" + pos + "_" + nextTile.ItemId);
            buttons.add(Buttons.blue(buttonId, pos));
        }
        if (canMulligan) {
            String buttonId = mapBuildContext.makeButtonId().apply(ACTION_PREFIX + "mulligan_" + nextTile.ItemId);
            buttons.add(Buttons.gray(buttonId, "Mulligan"));
        }
        buttons.add(Buttons.gray(mapBuildContext.makeButtonId().apply(ACTION_PREFIX + "repost"), "Repost build info"));

        // Build next message text
        MessageHelper.sendMessageToChannel(
                responseChannel,
                player.getRepresentation() + " can pick a position to place " + nextTile.getLongDescription() + "."
                        + mulliganString,
                buttons);
    }

    private void sendMapImage(
            GenericInteractionCreateEvent event, MessageChannel fallbackChannel, FileUpload mapImage) {
        if (event instanceof ButtonInteractionEvent buttonEvent) {
            // Attempt replace
            buttonEvent
                    .getChannel()
                    .getHistoryAround(buttonEvent.getMessage(), 50)
                    .queue(history -> {
                        boolean replaced = false;
                        for (Message msg : history.getRetrievedHistory()) {
                            if (!msg.getAuthor().getId().equals(JdaService.getBotId())) {
                                continue;
                            }
                            if (msg.getAttachments().isEmpty()) {
                                continue;
                            }
                            for (Attachment attachment : msg.getAttachments()) {
                                if (attachment.getFileName().startsWith(IMAGE_UNIQUE_STRING) && attachment.isImage()) {
                                    if (replaced) {
                                        msg.delete().queue();
                                        continue;
                                    }
                                    msg.editMessageAttachments(mapImage).queue();
                                    replaced = true;
                                }
                            }
                        }

                        // Fallback to just sending the image again
                        if (!replaced) {
                            MessageHelper.sendFileUploadToChannel(buttonEvent.getMessageChannel(), mapImage);
                        }
                    });
        } else {
            // Clean up previous versions of the image
            fallbackChannel.getHistory().retrievePast(50).queue(history -> {
                for (Message msg : history) {
                    if (!msg.getAuthor().getId().equals(JdaService.getBotId())) {
                        continue;
                    }
                    if (msg.getAttachments().isEmpty()) {
                        continue;
                    }
                    for (Attachment attachment : msg.getAttachments()) {
                        if (attachment.getFileName().startsWith(IMAGE_UNIQUE_STRING) && attachment.isImage()) {
                            msg.delete().queue();
                        }
                    }
                }

                // Send new image
                MessageHelper.sendFileUploadToChannel(fallbackChannel, mapImage);
            });
        }
    }

    private DraftItem drawTile(
            @Nullable GenericInteractionCreateEvent event,
            @Nonnull MantisMapBuildContext mapBuildContext,
            @Nonnull Player player,
            @Nonnull PlayerTiles playerRemainingTiles) {
        MessageChannel responseChannel =
                event == null ? player.getGame().getMainGameChannel() : event.getMessageChannel();

        // If there's already a drawn tile, use that one.
        String drawnTileId = mapBuildContext.drawnTileId();
        if (drawnTileId != null && !drawnTileId.isEmpty()) {
            if (playerRemainingTiles.blueTileIds().contains(drawnTileId)) {
                return DraftItem.generate(Category.BLUETILE, drawnTileId);
            } else if (playerRemainingTiles.redTileIds().contains(drawnTileId)) {
                return DraftItem.generate(Category.REDTILE, drawnTileId);
            } else {
                MessageHelper.sendMessageToChannel(
                        responseChannel,
                        "Error: Previously drawn tile " + drawnTileId
                                + " is no longer available for player " + player.getRepresentation()
                                + " to place next tile.");
                return null;
            }
        }

        // Gather all possible tiles
        List<String> availableTileIDs = new ArrayList<>();
        availableTileIDs.addAll(playerRemainingTiles.blueTileIds());
        availableTileIDs.addAll(playerRemainingTiles.redTileIds());
        String mulliganedTileId = mapBuildContext.mulliganedTileId();
        if (mulliganedTileId != null && !mulliganedTileId.isEmpty()) {
            availableTileIDs.remove(mulliganedTileId);
        }
        if (availableTileIDs.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    responseChannel,
                    "Error: Could not find any available tiles for player " + player.getRepresentation()
                            + " to place next tile.");
            return null;
        }

        // Draw and set a new tile as the "current drawn tile"
        Collections.shuffle(availableTileIDs);
        String selectedTileId = availableTileIDs.getFirst();
        mapBuildContext.persistDrawnTile().accept(selectedTileId);
        Category category =
                playerRemainingTiles.blueTileIds().contains(selectedTileId) ? Category.BLUETILE : Category.REDTILE;
        return DraftItem.generate(category, selectedTileId);
    }

    private Integer getPlayerNumberForTilePosition(
            @Nonnull MapTemplateModel mapTemplateModel, @Nonnull String position) {
        // Get the template tile at that position
        MapTemplateTile templateTile = mapTemplateModel.getTemplateTiles().stream()
                .filter(t -> t.getPos().equals(position))
                .findFirst()
                .orElse(null);
        if (templateTile == null) {
            return null;
        }

        return templateTile.getPlayerNumber();
    }

    private PlayerTiles getPlayerTilesWithTileId(List<PlayerTiles> allPlayerTiles, String tileId) {
        for (PlayerTiles pt : allPlayerTiles) {
            if (pt.blueTileIds().contains(tileId) || pt.redTileIds().contains(tileId)) {
                return pt;
            }
        }
        return null;
    }

    private List<String> getNextPositionGroup(Game game, Map<Integer, Map<Integer, List<String>>> placementGroups) {
        // Get the group keys ordered, to find the next group to place.
        List<Integer> groupKeys = new ArrayList<>(placementGroups.keySet());
        groupKeys.sort(Integer::compareTo);

        // Find the first group that has a position without a tile
        List<String> nextGroup = null;
        for (Integer groupKey : groupKeys) {
            Map<Integer, List<String>> playerNumToTiles = placementGroups.get(groupKey);
            List<Entry<Integer, List<String>>> orderedPlayerPositions = playerNumToTiles.entrySet().stream()
                    .sorted(Entry.comparingByKey())
                    .toList();
            for (Entry<Integer, List<String>> entry : orderedPlayerPositions) {
                List<String> positions = entry.getValue();
                List<String> currentUnplacedPositions = new ArrayList<>();
                for (String pos : positions) {
                    Tile tileAtPos = game.getTileByPosition(pos);
                    if (tileAtPos == null || TileHelper.isDraftTile(tileAtPos.getTileModel())) {
                        // This position needs a tile placed
                        currentUnplacedPositions.add(pos);
                    }
                }

                if (currentUnplacedPositions.isEmpty()) {
                    // No unplaced positions found
                    continue;
                }

                if (nextGroup == null) {
                    // If this is the first group with unplaced positions, start with it.
                    nextGroup = currentUnplacedPositions;
                } else if (currentUnplacedPositions.size() > nextGroup.size()) {
                    // If this group has more unplaced positions than the current nextGroup, use it
                    // instead.
                    nextGroup = currentUnplacedPositions;
                }
            }

            // If this group had any players with unplaced positions, we're done searching.
            if (nextGroup != null) break;
        }

        return nextGroup;
    }

    /**
     * Gets all of the template tile positions in which a tile must be placed,
     * grouped
     * with the other positions
     * that could receive a tile at the same time. For example, the tile positions
     * adjacent to Mecatol Rex
     * are all placed individually, so they're not grouped. But each player can
     * choose whether to place their
     * ring 2 tiles in either position, so those positions ARE grouped.
     * Here's an example from a 3-player standard map:
     * [[101], [103], [105], [201, 203], [205, 207], [209, 211], [318, 302], [306,
     * 308], [312, 314]]
     *
     * @param game
     * @param mapTemplateModel
     * @return A map of group number (arbitrary, but can be used for ordering), to a
     *         map that translates
     *         player numbers in the map template to a list of tile positions for
     *         that player in that group.
     */
    private Map<Integer, Map<Integer, List<String>>> getGroupedPositions(Game game, MapTemplateModel mapTemplateModel) {
        List<String> emulatedPositions = new ArrayList<>(mapTemplateModel.emulatedTiles());

        // Remove the home position, which by convention seems to be the first one in
        // the list
        emulatedPositions.removeFirst();

        Map<Integer, Map<Integer, List<String>>> groupToPlayerNumToTiles = new HashMap<>();
        for (MapTemplateTile tile : mapTemplateModel.getTemplateTiles()) {
            Integer playerNumber = tile.getPlayerNumber();
            Integer tileIndex = tile.getMiltyTileIndex();
            Integer group = null;
            if (tileIndex != null) {
                // posToTileIndex.put(tile.getPos(), tileIndex);

                // Milty Tile Index seems to be 0-based, ordering tiles from relative-left to
                // clockwise then out.
                // This seems to correspond to emulated tile positions AFTER removing the
                // emulated home system.
                // TODO: Unit test that fails when the mapper loads a template that violates
                // this assumption.
                String equivEmulatedPos = emulatedPositions.get(tileIndex);
                group = getTileGroup(equivEmulatedPos);
            }

            if (playerNumber != null && group != null) {
                groupToPlayerNumToTiles.putIfAbsent(group, new HashMap<>());
                Map<Integer, List<String>> playerNumToTiles = groupToPlayerNumToTiles.get(group);
                playerNumToTiles.putIfAbsent(playerNumber, new ArrayList<>());
                playerNumToTiles.get(playerNumber).add(tile.getPos());
            }
        }

        return groupToPlayerNumToTiles;
    }

    /**
     * This is somewhat arbitrary. I'm trying to create a general logic that will
     * group
     * tile positions like we do in a standard map. Basically, just use the ring
     * number
     * on the equivalent emulated tile to determine the group.
     */
    private Integer getTileGroup(String emulatedTilePos) {
        // Position 101 = group 1; Position 1304 = group 13
        if (emulatedTilePos.length() < 3) {
            return null;
        }
        return Integer.valueOf(emulatedTilePos.substring(0, emulatedTilePos.length() - 2));
    }

    private void sendDiscardButtons(MantisMapBuildContext mapBuildContext) {
        for (PlayerTiles prt : mapBuildContext.availablePlayerTiles()) {
            Player player = mapBuildContext.game().getPlayer(prt.playerUserId());
            if (player == null) {
                BotLogger.warning(
                        new LogOrigin(mapBuildContext.game()),
                        "Could not find player with ID " + prt.playerUserId() + " to send mantis discard buttons.");
                continue;
            }

            int blueToDiscard = prt.blueTileIds().size()
                    - mapBuildContext.mapTemplateModel().bluePerPlayer();
            if (blueToDiscard > 0) {
                sendDiscardButtons(
                        mapBuildContext,
                        player,
                        Category.BLUETILE,
                        prt.blueTileIds(),
                        blueToDiscard,
                        mapBuildContext.mapTemplateModel().bluePerPlayer());
            }

            int redToDiscard =
                    prt.redTileIds().size() - mapBuildContext.mapTemplateModel().redPerPlayer();
            if (redToDiscard > 0) {
                sendDiscardButtons(
                        mapBuildContext,
                        player,
                        Category.REDTILE,
                        prt.redTileIds(),
                        redToDiscard,
                        mapBuildContext.mapTemplateModel().redPerPlayer());
            }
        }
    }

    private void sendDiscardButtons(
            MantisMapBuildContext mapBuildContext,
            Player player,
            Category category,
            List<String> tileIds,
            int toDiscard,
            int desiredAmount) {
        if (toDiscard <= 0) return;
        if (tileIds == null || tileIds.isEmpty()) return;
        if (toDiscard > tileIds.size()) toDiscard = tileIds.size();
        List<Button> discardButtons = new ArrayList<>();
        for (String tileId : tileIds) {
            String buttonId = mapBuildContext.makeButtonId().apply(ACTION_PREFIX + "discard_" + tileId);
            DraftItem item = DraftItem.generate(category, tileId);
            if (category == Category.BLUETILE) {
                discardButtons.add(Buttons.blue(buttonId, item.getShortDescription(), item.getItemEmoji()));
            } else if (category == Category.REDTILE) {
                discardButtons.add(Buttons.red(buttonId, item.getShortDescription(), item.getItemEmoji()));
            }
        }

        MessageChannel playerChannel = player.getCardsInfoThread();
        if (playerChannel == null) {
            playerChannel = mapBuildContext.game().getMainGameChannel();
        }
        MessageHelper.sendMessageToChannel(
                playerChannel,
                player.getRepresentation() + " You need to discard down to " + desiredAmount + " of these tiles",
                discardButtons);
    }

    private boolean anyNeedsToDiscard(List<PlayerTiles> playerRemainingTiles, MapTemplateModel mapTemplate) {
        for (PlayerTiles prt : playerRemainingTiles) {
            if (playerNeedsToDiscard(prt, mapTemplate)) {
                return true;
            }
        }
        return false;
    }

    private boolean playerNeedsToDiscard(PlayerTiles prt, MapTemplateModel mapTemplate) {
        return prt.blueTileIds().size() > mapTemplate.bluePerPlayer()
                || prt.redTileIds().size() > mapTemplate.redPerPlayer();
    }
}
