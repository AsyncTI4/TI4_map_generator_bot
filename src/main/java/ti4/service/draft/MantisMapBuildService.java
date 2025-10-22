package ti4.service.draft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
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
import ti4.image.Mapper;
import ti4.image.TileHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.message.logging.LogOrigin;
import ti4.model.MapTemplateModel;
import ti4.model.MapTemplateModel.MapTemplateTile;
import ti4.service.draft.draftables.MantisTileDraftable;
import ti4.service.draft.draftables.SpeakerOrderDraftable;
import ti4.spring.jda.JdaService;

@UtilityClass
public class MantisMapBuildService {

    public static final String ACTION_PREFIX = "mbuild_";
    private static final String IMAGE_UNIQUE_STRING = "mantismapbuild";

    /**
     * Figures out what works is needed to start building the map, and sends buttons
     * for that work.
     * If players have too many tiles, will send private discard buttons first.
     * Otherwise, will publicly post map building buttons.
     *
     * @param draftManager
     */
    public void initializeMapBuilding(DraftManager draftManager) {
        Game game = draftManager.getGame();
        MantisTileDraftable mantisTileDraftable =
                (MantisTileDraftable) draftManager.getDraftable(MantisTileDraftable.TYPE);
        if (mantisTileDraftable == null) {
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(), "Error: Could not find mantis tile draftable to start map building.");
            return;
        }

        String mapTemplate = game.getMapTemplateID();
        MapTemplateModel mapTemplateModel = Mapper.getMapTemplate(mapTemplate);
        if (mapTemplateModel == null) {
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(), "Error: Map template " + mapTemplate + " is not valid.");
            return;
        }

        List<PlayerRemainingTiles> playerRemainingTiles = getPlayerRemainingTiles(draftManager, mantisTileDraftable);
        if (anyNeedsToDiscard(playerRemainingTiles, mapTemplateModel)) {
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(), "Starting the map building by having players discard excess tiles.");
            sendDiscardButtons(game, mantisTileDraftable, mapTemplateModel, playerRemainingTiles);
        } else {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "Starting map building!");
            updateMapBuild(null, game, mantisTileDraftable, mapTemplateModel, playerRemainingTiles);
        }
    }

    public String handleAction(GenericInteractionCreateEvent event, DraftManager draftManager, String action) {
        Game game = draftManager.getGame();
        MantisTileDraftable mantisTileDraftable =
                (MantisTileDraftable) draftManager.getDraftable(MantisTileDraftable.TYPE);
        if (mantisTileDraftable == null) {
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(), "Error: Could not find mantis tile draftable to start map building.");
            return "Error: Could not find mantis tile draftable to start map building.";
        }

        String mapTemplate = game.getMapTemplateID();
        MapTemplateModel mapTemplateModel = Mapper.getMapTemplate(mapTemplate);
        if (mapTemplateModel == null) {
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(), "Error: Map template " + mapTemplate + " is not valid.");
            return "Error: Map template " + mapTemplate + " is not valid.";
        }

        if (action.startsWith(ACTION_PREFIX)) {
            action = action.substring(ACTION_PREFIX.length());
        }
        String[] actionParts = action.split("_", 2);
        String actionType = actionParts[0];
        String outcome =
                switch (actionType) {
                    case "place" -> handlePlaceTile(event, game, mapTemplateModel, actionParts[1]);
                    case "mulligan" -> handleMulliganTile(event, game, mapTemplateModel, actionParts[1]);
                    case "repost" -> handleRepost(game, mapTemplateModel);
                    case "discard" -> handleDiscardTile(event, game, mapTemplateModel, actionParts[1]);
                    default -> "Error: Unknown map build action type: " + actionType;
                };

        return outcome;
    }

    public String handlePlaceTile(
            GenericInteractionCreateEvent event, Game game, MapTemplateModel mapTemplateModel, String actionParams) {
        String[] params = actionParams.split("_", 2);
        if (params.length != 2) {
            return "Error: Invalid parameters for place tile action: " + actionParams;
        }

        DraftManager draftManager = game.getDraftManager();
        MantisTileDraftable mantisTileDraftable =
                (MantisTileDraftable) draftManager.getDraftable(MantisTileDraftable.TYPE);
        if (mantisTileDraftable == null) {
            return "Error: Could not find mantis tile draftable to continue map building.";
        }

        String position = params[0];
        String tileId = params[1];

        String playerUserId = getPlayerUserIdForTilePosition(draftManager, mapTemplateModel, position);
        if (event instanceof ButtonInteractionEvent buttonEvent
                && !buttonEvent.getUser().getId().equals(playerUserId)) {
            return DraftButtonService.USER_MISTAKE_PREFIX + "It's not your turn to place a tile";
        }
        List<String> playersWithTilePick = draftManager.getPlayersWithChoiceKey(
                MantisTileDraftable.TYPE, MantisTileDraftable.makeChoiceKey(tileId));
        if (playersWithTilePick.isEmpty()) {
            return "Error: Could not find any player that has tile " + tileId + " drafted.";
        }
        if (!playersWithTilePick.contains(playerUserId)) {
            return "Error: You do not have tile " + tileId + " drafted.";
        }

        Player player = game.getPlayer(playerUserId);

        // TODO: Other validation before locking it in?

        if (!placeTile(game, mantisTileDraftable, position, tileId)) {
            return "Error: Unable to place tile " + tileId + " at " + position + ".";
        }

        // Post the pick
        String tileChoiceKey = MantisTileDraftable.makeChoiceKey(tileId);
        Category tileCategory = mantisTileDraftable.getItemCategory(tileChoiceKey);
        DraftItem placedTile = DraftItem.generate(tileCategory, tileId);
        MessageHelper.sendMessageToChannel(
                game.getMainGameChannel(),
                player.getRepresentation() + " placed " + placedTile.getShortDescription() + " at Position " + position
                        + ".");

        List<PlayerRemainingTiles> allPlayerRemainingTiles = getPlayerRemainingTiles(draftManager, mantisTileDraftable);
        updateMapBuild(event, game, mantisTileDraftable, mapTemplateModel, allPlayerRemainingTiles);
        return DraftButtonService.DELETE_MESSAGE;
    }

    public String handleMulliganTile(
            GenericInteractionCreateEvent event, Game game, MapTemplateModel mapTemplateModel, String actionParams) {
        DraftManager draftManager = game.getDraftManager();
        MantisTileDraftable mantisTileDraftable =
                (MantisTileDraftable) draftManager.getDraftable(MantisTileDraftable.TYPE);
        if (mantisTileDraftable == null) {
            return "Error: Could not find mantis tile draftable to continue map building.";
        }

        String tileId = actionParams;

        List<String> playersWithTilePick = draftManager.getPlayersWithChoiceKey(
                MantisTileDraftable.TYPE, MantisTileDraftable.makeChoiceKey(tileId));
        if (playersWithTilePick.isEmpty()) {
            return "Error: Could not find any player that has tile " + tileId + " drafted.";
        }

        String playerUserId = playersWithTilePick.get(0);
        if (event instanceof ButtonInteractionEvent buttonEvent
                && !buttonEvent.getUser().getId().equals(playerUserId)) {
            return DraftButtonService.USER_MISTAKE_PREFIX + "It's not your tile to mulligan.";
        }

        List<PlayerRemainingTiles> allPlayerRemainingTiles = getPlayerRemainingTiles(draftManager, mantisTileDraftable);

        mantisTileDraftable.getMulliganTileIDs().add(tileId);
        mantisTileDraftable.setDrawnTileId(null);
        updateMapBuild(event, game, mantisTileDraftable, mapTemplateModel, allPlayerRemainingTiles, tileId);
        return null;
    }

    public String handleRepost(Game game, MapTemplateModel mapTemplateModel) {
        DraftManager draftManager = game.getDraftManager();
        MantisTileDraftable mantisTileDraftable =
                (MantisTileDraftable) draftManager.getDraftable(MantisTileDraftable.TYPE);
        if (mantisTileDraftable == null) {
            return "Error: Could not find mantis tile draftable to continue map building.";
        }

        List<PlayerRemainingTiles> allPlayerRemainingTiles = getPlayerRemainingTiles(draftManager, mantisTileDraftable);

        // Calling this without an event should cause an update, instead of an edit
        updateMapBuild(null, game, mantisTileDraftable, mapTemplateModel, allPlayerRemainingTiles);
        return DraftButtonService.DELETE_MESSAGE;
    }

    public String handleDiscardTile(
            GenericInteractionCreateEvent event, Game game, MapTemplateModel mapTemplateModel, String actionParams) {
        DraftManager draftManager = game.getDraftManager();
        MantisTileDraftable mantisTileDraftable =
                (MantisTileDraftable) draftManager.getDraftable(MantisTileDraftable.TYPE);
        if (mantisTileDraftable == null) {
            return "Error: Could not find mantis tile draftable to continue map building.";
        }

        String tileId = actionParams;

        List<String> playersWithTilePick = draftManager.getPlayersWithChoiceKey(
                MantisTileDraftable.TYPE, MantisTileDraftable.makeChoiceKey(tileId));
        if (playersWithTilePick.isEmpty()) {
            return "Error: Could not find any player that has tile " + tileId + " drafted.";
        }

        String playerUserId = playersWithTilePick.get(0);
        if (event instanceof ButtonInteractionEvent buttonEvent
                && !buttonEvent.getUser().getId().equals(playerUserId)) {
            return DraftButtonService.USER_MISTAKE_PREFIX + "It's not your tile to discard";
        }

        List<PlayerRemainingTiles> allPlayerRemainingTiles = getPlayerRemainingTiles(draftManager, mantisTileDraftable);
        PlayerRemainingTiles playerRemainingTiles = allPlayerRemainingTiles.stream()
                .filter(pr -> pr.playerUserId().equals(playerUserId))
                .findFirst()
                .orElse(null);
        if (!(playerRemainingTiles.blueTileIds().contains(tileId)
                || playerRemainingTiles.redTileIds().contains(tileId))) {
            return "Error: You do not have tile " + tileId + " drafted.";
        }

        Category tileCategory = mantisTileDraftable.getItemCategory(MantisTileDraftable.makeChoiceKey(tileId));

        mantisTileDraftable.getDiscardedTileIDs().add(tileId);

        // Update check for needed discards
        allPlayerRemainingTiles = getPlayerRemainingTiles(draftManager, mantisTileDraftable);
        boolean anyNeedToDiscard = anyNeedsToDiscard(allPlayerRemainingTiles, mapTemplateModel);
        playerRemainingTiles = allPlayerRemainingTiles.stream()
                .filter(pr -> pr.playerUserId().equals(playerUserId))
                .findFirst()
                .orElse(null);

        boolean categoryNeedsDiscard = tileCategory == Category.BLUETILE
                ? playerRemainingTiles.blueTileIds().size() > mapTemplateModel.bluePerPlayer()
                : playerRemainingTiles.redTileIds().size() > mapTemplateModel.redPerPlayer();

        if (!anyNeedToDiscard) {
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(), "All players have discarded excess tiles. Continuing map building!");
            mantisTileDraftable.setDrawnTileId(null);
            // Start building with a null event to force new messaging in the main thread
            updateMapBuild(null, game, mantisTileDraftable, mapTemplateModel, allPlayerRemainingTiles);
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

    private boolean placeTile(Game game, MantisTileDraftable mantisDraftable, String position, String tileId) {
        Tile existingTile = game.getTileByPosition(position);
        if (existingTile != null && !TileHelper.isDraftTile(existingTile.getTileModel())) {
            // There's already a non-draft tile here
            BotLogger.warning(
                    new LogOrigin(game),
                    "Cannot place tile " + tileId + " at " + position
                            + " because there's already a non-draft tile there.");
            return false;
        }

        game.setTile(new Tile(tileId, position));
        mantisDraftable.setDrawnTileId(null);
        return true;
    }

    private void updateMapBuild(
            GenericInteractionCreateEvent event,
            Game game,
            MantisTileDraftable mantisDraftable,
            MapTemplateModel mapTemplateModel,
            List<PlayerRemainingTiles> allPlayerRemainingTiles) {
        updateMapBuild(event, game, mantisDraftable, mapTemplateModel, allPlayerRemainingTiles, null);
    }

    private void updateMapBuild(
            GenericInteractionCreateEvent event,
            Game game,
            MantisTileDraftable mantisDraftable,
            MapTemplateModel mapTemplateModel,
            List<PlayerRemainingTiles> allPlayerRemainingTiles,
            String mulliganTileId) {
        MessageChannel responseChannel = event == null ? game.getMainGameChannel() : event.getMessageChannel();

        DraftManager draftManager = game.getDraftManager();
        if (draftManager == null) {
            MessageHelper.sendMessageToChannel(
                    responseChannel, "Error: Could not find draft manager to continue map building.");
            return;
        }

        // Get the tile positions, grouped by player, and then grouped again by
        // placement order.
        Map<Integer, Map<Integer, List<String>>> placementGroups = getGroupedPositions(game, mapTemplateModel);

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
        if (nextGroup == null || nextGroup.isEmpty()) {
            // All done!
            MessageHelper.sendMessageToChannel(responseChannel, "Map building complete!");
            ButtonHelper.updateMap(game, event, "Mantis Map Build Completed");
            draftManager.trySetupPlayers(event);
            return;
        }

        // Use the first position in the group to determine which player is placing
        // next.
        String playerUserId = getPlayerUserIdForTilePosition(draftManager, mapTemplateModel, nextGroup.get(0));
        if (playerUserId == null) {
            MessageHelper.sendMessageToChannel(
                    responseChannel,
                    "Error: Could not determine player for next tile placement position at " + nextGroup.get(0) + ".");
            return;
        }

        Player player = game.getPlayer(playerUserId);
        if (player == null) {
            MessageHelper.sendMessageToChannel(
                    responseChannel, "Error: Could not find player with ID " + playerUserId + " to place next tile.");
            return;
        }

        PlayerRemainingTiles playerRemainingTiles = allPlayerRemainingTiles.stream()
                .filter(prt -> prt.playerUserId().equals(playerUserId))
                .findFirst()
                .orElse(null);
        if (playerRemainingTiles == null) {
            MessageHelper.sendMessageToChannel(
                    responseChannel,
                    "Error: Could not find remaining tiles for player " + player.getRepresentation()
                            + " to place next tile.");
            return;
        }

        DraftItem nextTile = drawTile(event, player, mantisDraftable, playerRemainingTiles, mulliganTileId);
        if (nextTile == null) {
            MessageHelper.sendMessageToChannel(
                    responseChannel, "Error: Could not draw next tile for player " + player.getRepresentation() + ".");
            return;
        }

        // Get the number of mulligans used by this player so far
        List<DraftChoice> playerPicks =
                game.getDraftManager().getPlayerPicks(player.getUserID(), MantisTileDraftable.TYPE);
        int mulligansUsed = (int) mantisDraftable.getMulliganTileIDs().stream()
                .filter(mulliganId -> playerPicks.stream()
                        .anyMatch(pick -> MantisTileDraftable.getItemId(pick.getChoiceKey())
                                .equals(mulliganId)))
                .count();

        int mulliganLimit = mantisDraftable.getMulligans() != null ? mantisDraftable.getMulligans() : 0;
        boolean canMulligan = mulligansUsed < mulliganLimit;
        if (playerRemainingTiles.blueTileIds().size()
                        + playerRemainingTiles.redTileIds().size()
                <= 1) {
            // No more tiles to draw
            canMulligan = false;
        }

        // If only one position, do it now and call this recursively
        if (nextGroup.size() == 1 && !canMulligan) {
            boolean success = placeTile(game, mantisDraftable, nextGroup.get(0), nextTile.ItemId);
            if (!success) return;

            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(),
                    player.getRepresentation() + " placed "
                            + nextTile.getShortDescription() + " at Position " + nextGroup.get(0)
                            + " (automatically; no alternatives).");

            // Regenerate possible tiles
            allPlayerRemainingTiles = getPlayerRemainingTiles(draftManager, mantisDraftable);

            // Recursively do this update until we encounter a decision or finish
            updateMapBuild(event, game, mantisDraftable, mapTemplateModel, allPlayerRemainingTiles);
            return;
        }

        String mulliganString = canMulligan
                ? " You can mulligan to draw a different tile (used " + mulligansUsed + " of " + mulliganLimit + ")."
                : " (No mulligans remaining.)";

        FileUpload mapImage = MantisBuildImageGeneratorService.tryGenerateImage(
                draftManager, IMAGE_UNIQUE_STRING, nextGroup, nextTile.ItemId);
        if (mapImage != null) {
            sendMapImage(event, responseChannel, mapImage);
        }
        sendDraftButtons(event, game, mantisDraftable, player, nextTile, nextGroup, canMulligan, mulliganString);
    }

    private void sendDraftButtons(
            GenericInteractionCreateEvent event,
            Game game,
            MantisTileDraftable mantisDraftable,
            Player player,
            DraftItem nextTile,
            List<String> openPositions,
            boolean canMulligan,
            String mulliganString) {
        MessageChannel responseChannel = event == null ? game.getMainGameChannel() : event.getMessageChannel();

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
            String buttonId = mantisDraftable.makeButtonId(ACTION_PREFIX + "place_" + pos + "_" + nextTile.ItemId);
            buttons.add(Buttons.blue(buttonId, pos));
        }
        if (canMulligan) {
            String buttonId = mantisDraftable.makeButtonId(ACTION_PREFIX + "mulligan_" + nextTile.ItemId);
            buttons.add(Buttons.gray(buttonId, "Mulligan"));
        }
        buttons.add(Buttons.gray(mantisDraftable.makeButtonId(ACTION_PREFIX + "repost"), "Repost build info"));

        // Build next message text
        StringBuilder sb = new StringBuilder(
                player.getRepresentation() + " can pick a position to place " + nextTile.getLongDescription() + ".");
        sb.append(mulliganString);
        MessageHelper.sendMessageToChannel(responseChannel, sb.toString(), buttons);
    }

    private void sendMapImage(
            GenericInteractionCreateEvent event, MessageChannel fallbackChannel, FileUpload mapImage) {
        if (event != null && event instanceof ButtonInteractionEvent buttonEvent) {
            // Attempt replace
            buttonEvent
                    .getChannel()
                    .getHistoryAround(buttonEvent.getMessage(), 20)
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
                                    msg.editMessageAttachments(mapImage).queue();
                                    replaced = true;
                                    break;
                                }
                            }
                        }

                        // Fallback to just sending the image again
                        if (!replaced) {
                            MessageHelper.sendFileUploadToChannel(buttonEvent.getMessageChannel(), mapImage);
                        }
                    });
        } else {
            MessageHelper.sendFileUploadToChannel(fallbackChannel, mapImage);
        }
    }

    private DraftItem drawTile(
            GenericInteractionCreateEvent event,
            Player player,
            MantisTileDraftable mantisTileDraftable,
            PlayerRemainingTiles playerRemainingTiles,
            String mulliganTileId) {
        MessageChannel responseChannel =
                event == null ? player.getGame().getMainGameChannel() : event.getMessageChannel();

        // Gather all possible tiles
        List<String> availableTileIDs = new ArrayList<>();
        availableTileIDs.addAll(playerRemainingTiles.blueTileIds());
        availableTileIDs.addAll(playerRemainingTiles.redTileIds());
        if (mulliganTileId != null) {
            availableTileIDs.remove(mulliganTileId);
        }
        if (availableTileIDs.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    responseChannel,
                    "Error: Could not find any available tiles for player " + player.getRepresentation()
                            + " to place next tile.");
            return null;
        }

        // If there's already a drawn tile, use that one.
        if (mantisTileDraftable.getDrawnTileId() != null
                && !mantisTileDraftable.getDrawnTileId().isEmpty()) {
            String selectedTileId = mantisTileDraftable.getDrawnTileId();
            if (playerRemainingTiles.blueTileIds().contains(selectedTileId)) {
                return DraftItem.generate(Category.BLUETILE, selectedTileId);
            } else if (playerRemainingTiles.redTileIds().contains(selectedTileId)) {
                return DraftItem.generate(Category.REDTILE, selectedTileId);
            } else {
                MessageHelper.sendMessageToChannel(
                        responseChannel,
                        "Error: Previously drawn tile " + selectedTileId + " is no longer available for player "
                                + player.getRepresentation() + " to place next tile.");
                return null;
            }
        }

        // Draw and set a new tile as the "current drawn tile"
        Collections.shuffle(availableTileIDs);
        String selectedTileId = availableTileIDs.get(0);
        mantisTileDraftable.setDrawnTileId(selectedTileId);
        Category category =
                playerRemainingTiles.blueTileIds().contains(selectedTileId) ? Category.BLUETILE : Category.REDTILE;
        return DraftItem.generate(category, selectedTileId);
    }

    /**
     * Get the user ID of the player at a given map position, based on speaker
     * order.
     *
     * @param draftManager
     * @param position     0-based position in the list of the map's player numbers
     * @return The user ID of the player at that position, or null if not found
     */
    private String getPlayerUserIdBySpeakerOrder(DraftManager draftManager, int position) {

        if (draftManager.getDraftable(SpeakerOrderDraftable.TYPE) == null) {
            MessageHelper.sendMessageToChannel(
                    draftManager.getGame().getMainGameChannel(),
                    "Error: Speaker order draftable is not enabled, cannot determine player order.");
            return null;
        }

        // Find the player with the given speaker order position
        for (Entry<String, PlayerDraftState> entry :
                draftManager.getPlayerStates().entrySet()) {
            String playerId = entry.getKey();
            PlayerDraftState pState = entry.getValue();
            List<DraftChoice> picks = pState.getPicks(SpeakerOrderDraftable.TYPE);
            if (picks == null || picks.isEmpty()) {
                continue;
            }
            // This is 1-based
            Integer playerPosition = SpeakerOrderDraftable.getSpeakerOrderFromChoiceKey(
                    picks.get(0).getChoiceKey());
            if (playerPosition != null && playerPosition == (1 + position)) {
                return playerId;
            }
        }
        return null;
    }

    private String getPlayerUserIdForTilePosition(
            DraftManager draftManager, MapTemplateModel mapTemplateModel, String position) {
        // Get the template tile at that position
        MapTemplateTile templateTile = mapTemplateModel.getTemplateTiles().stream()
                .filter(t -> t.getPos().equals(position))
                .findFirst()
                .orElse(null);
        if (templateTile == null || templateTile.getPlayerNumber() == null) {
            return null;
        }

        Integer mapTemplatePlayerNum = templateTile.getPlayerNumber();

        // Player numbers are just data, I don't trust them to be 1, 2, 3, n. Get the
        // actual list of player numbers.
        List<Integer> playerNums = mapTemplateModel.getTemplateTiles().stream()
                .filter(t -> t.getPlayerNumber() != null)
                .map(MapTemplateTile::getPlayerNumber)
                .distinct()
                .sorted()
                .toList();
        // Find this player number's position in the list, and that index will
        // correspond to the player's speaker order
        // pick.
        int playerIndex = playerNums.indexOf(mapTemplatePlayerNum);
        if (playerIndex < 0) {
            return null;
        }

        // Get the user ID of the current group's player, based on speaker order
        String playerUserId = getPlayerUserIdBySpeakerOrder(draftManager, playerIndex);
        return playerUserId;
    }

    /**
     * Gets all of the template tile positions in which a tile must be placed, grouped
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
        emulatedPositions.remove(0);

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

    private void sendDiscardButtons(
            Game game,
            MantisTileDraftable mantisDraftable,
            MapTemplateModel mapTemplate,
            List<PlayerRemainingTiles> playerRemainingTiles) {
        for (PlayerRemainingTiles prt : playerRemainingTiles) {
            Player player = game.getPlayer(prt.playerUserId());
            if (player == null) {
                BotLogger.warning(
                        new LogOrigin(game),
                        "Could not find player with ID " + prt.playerUserId() + " to send mantis discard buttons.");
                continue;
            }

            int blueToDiscard = prt.blueTileIds().size() - mapTemplate.bluePerPlayer();
            if (blueToDiscard > 0) {
                sendDiscardButtons(
                        game,
                        mantisDraftable,
                        player,
                        Category.BLUETILE,
                        prt.blueTileIds(),
                        blueToDiscard,
                        mapTemplate.bluePerPlayer());
            }

            int redToDiscard = prt.redTileIds().size() - mapTemplate.redPerPlayer();
            if (redToDiscard > 0) {
                sendDiscardButtons(
                        game,
                        mantisDraftable,
                        player,
                        Category.REDTILE,
                        prt.redTileIds(),
                        redToDiscard,
                        mapTemplate.redPerPlayer());
            }
        }
    }

    private void sendDiscardButtons(
            Game game,
            MantisTileDraftable mantisDraftable,
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
            String buttonId = mantisDraftable.makeButtonId(ACTION_PREFIX + "discard_" + tileId);
            DraftItem item = DraftItem.generate(category, tileId);
            if (category == Category.BLUETILE) {
                discardButtons.add(Buttons.blue(buttonId, item.getShortDescription(), item.getItemEmoji()));
            } else if (category == Category.REDTILE) {
                discardButtons.add(Buttons.red(buttonId, item.getShortDescription(), item.getItemEmoji()));
            }
        }

        MessageChannel playerChannel = player.getCardsInfoThread();
        if (playerChannel == null) {
            playerChannel = game.getMainGameChannel();
        }
        MessageHelper.sendMessageToChannel(
                playerChannel,
                player.getRepresentation() + " You need to discard down to " + desiredAmount + " of these tiles",
                discardButtons);
    }

    private boolean anyNeedsToDiscard(List<PlayerRemainingTiles> playerRemainingTiles, MapTemplateModel mapTemplate) {
        for (PlayerRemainingTiles prt : playerRemainingTiles) {
            if (playerNeedsToDiscard(prt, mapTemplate)) {
                return true;
            }
        }
        return false;
    }

    private boolean playerNeedsToDiscard(PlayerRemainingTiles prt, MapTemplateModel mapTemplate) {
        return prt.blueTileIds().size() > mapTemplate.bluePerPlayer()
                || prt.redTileIds().size() > mapTemplate.redPerPlayer();
    }

    private List<PlayerRemainingTiles> getPlayerRemainingTiles(
            DraftManager draftManager, MantisTileDraftable mantisDraftable) {
        List<PlayerRemainingTiles> result = new ArrayList<>();
        Set<String> placedTiles = draftManager.getGame().getTileMap().values().stream()
                .map(t -> t.getTileID())
                .collect(Collectors.toSet());
        Set<String> discardedTiles =
                mantisDraftable.getDiscardedTileIDs().stream().collect(Collectors.toSet());
        for (Entry<String, PlayerDraftState> entry :
                draftManager.getPlayerStates().entrySet()) {
            String playerId = entry.getKey();
            List<DraftChoice> mantisPicks = new ArrayList<>(entry.getValue().getPicks(MantisTileDraftable.TYPE));

            // Remove tiles that are placed on the game board
            mantisPicks.removeIf(pick -> placedTiles.contains(MantisTileDraftable.getItemId(pick.getChoiceKey())));

            // Remove tiles that are discarded
            mantisPicks.removeIf(pick -> discardedTiles.contains(MantisTileDraftable.getItemId(pick.getChoiceKey())));

            // Collect unplaced picks as separate lists of BlueTileDraftItems and
            // RedTileDraftItems
            result.add(PlayerRemainingTiles.create(playerId, mantisDraftable, mantisPicks));
        }
        return result;
    }

    private record PlayerRemainingTiles(String playerUserId, List<String> blueTileIds, List<String> redTileIds) {
        public static PlayerRemainingTiles create(
                String playerUserId, MantisTileDraftable mantisDraftable, List<DraftChoice> playerPicks) {
            List<String> blueTileIds = new ArrayList<>();
            List<String> redTileIds = new ArrayList<>();
            for (DraftChoice choice : playerPicks) {
                Category category = mantisDraftable.getItemCategory(choice.getChoiceKey());
                String tileId = MantisTileDraftable.getItemId(choice.getChoiceKey());
                if (category == Category.BLUETILE) {
                    blueTileIds.add(tileId);
                } else if (category == Category.REDTILE) {
                    redTileIds.add(tileId);
                }
            }
            return new PlayerRemainingTiles(playerUserId, blueTileIds, redTileIds);
        }
    }
}
