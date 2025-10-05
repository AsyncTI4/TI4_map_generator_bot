package ti4.service.draft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Predicate;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.draft.DraftItem;
import ti4.draft.DraftItem.Category;
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
import ti4.service.draft.draftables.SeatDraftable;
import ti4.service.draft.draftables.SpeakerOrderDraftable;
import ti4.service.milty.MiltyDraftTile;

@UtilityClass
public class MantisMapBuildService {

    public static final String ACTION_PREFIX = "mbuild_";
    /**
     * Figures out what works is needed to start building the map, and sends buttons for that work.
     * If players have too many tiles, will send private discard buttons first.
     * Otherwise, will publicly post map building buttons.
     * @param draftManager
     */
    public void initializeMapBuilding(DraftManager draftManager) {
        Game game = draftManager.getGame();
        MantisTileDraftable mantisTileDraftable = (MantisTileDraftable) draftManager.getDraftable(MantisTileDraftable.TYPE);
        if(mantisTileDraftable == null) {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "Error: Could not find mantis tile draftable to start map building.");
            return;
        }

        String mapTemplate = game.getMapTemplateID();
        MapTemplateModel mapTemplateModel = Mapper.getMapTemplate(mapTemplate);
        if(mapTemplateModel == null) {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "Error: Map template " + mapTemplate + " is not valid.");
            return;
        }

        // TODO: Support over-picking
        int bpp = mapTemplateModel.bluePerPlayer();
        int rpp = mapTemplateModel.redPerPlayer();

        List<PlayerRemainingTiles> playerRemainingTiles = getPlayerRemainingTiles(draftManager, mantisTileDraftable);
        if(anyNeedsToDiscard(playerRemainingTiles, bpp, rpp)) {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "Some players have too many tiles. Over-drafting isn't supported right now.");
            // MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "Starting the map building by having players discard excess tiles.");
            // sendDiscardButtons(game, mantisTileDraftable, playerRemainingTiles, bpp, rpp);
        } else {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "Starting map building!");
            updateMapBuild(null, game, mantisTileDraftable, mapTemplateModel, playerRemainingTiles);
        }
    }

    public String handleAction(GenericInteractionCreateEvent event, DraftManager draftManager, String action) {
        Game game = draftManager.getGame();
        MantisTileDraftable mantisTileDraftable = (MantisTileDraftable) draftManager.getDraftable(MantisTileDraftable.TYPE);
        if(mantisTileDraftable == null) {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "Error: Could not find mantis tile draftable to start map building.");
            return "Error: Could not find mantis tile draftable to start map building.";
        }

        String mapTemplate = game.getMapTemplateID();
        MapTemplateModel mapTemplateModel = Mapper.getMapTemplate(mapTemplate);
        if(mapTemplateModel == null) {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "Error: Map template " + mapTemplate + " is not valid.");
            return "Error: Map template " + mapTemplate + " is not valid.";
        }

        if(action.startsWith(ACTION_PREFIX)) {
            action = action.substring(ACTION_PREFIX.length());
        }
        String[] actionParts = action.split("_", 2);
        String actionType = actionParts[0];
        String outcome = switch(actionType) {
            case "place" -> handlePlaceTile(event, game, mapTemplateModel, actionParts[1]);
            case "mulligan" -> handleMulliganTile();
            case "repost" -> handleRepost();
            default -> "Error: Unknown map build action type: " + actionType;
        };

        return outcome;
    }

    public String handlePlaceTile(GenericInteractionCreateEvent event, Game game, MapTemplateModel mapTemplateModel, String actionParams) {
        String[] params = actionParams.split("_", 2);
        if(params.length != 2) {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "Error: Invalid parameters for place tile action: " + actionParams);
            return "Error: Invalid parameters for place tile action: " + actionParams;
        }

        String position = params[0];
        String tileId = params[1];

        DraftManager draftManager = game.getDraftManager();
        String playerUserId = getPlayerUserIdForPosition(draftManager, mapTemplateModel, position);
        if(event instanceof ButtonInteractionEvent buttonEvent && !buttonEvent.getUser().getId().equals(playerUserId)) {
            return "It's not your turn to place a tile";
        }

        // TODO: Other validation before locking it in?
    }

    public String handleMulliganTile() {

    }

    public String handleRepost() {

    }

    private boolean placeTile(Game game, String position, String tileId) {
        Tile existingTile = game.getTileByPosition(position);
        if(existingTile != null && !TileHelper.isDraftTile(existingTile.getTileModel())) {
            // There's already a non-draft tile here
            BotLogger.warning(new LogOrigin(game), "Cannot place tile " + tileId + " at " + position + " because there's already a non-draft tile there.");
            return false;
        }

        game.setTile(new Tile(tileId, position));
        return true;
    }

    private void updateMapBuild(GenericInteractionCreateEvent event, Game game, MantisTileDraftable mantisDraftable, MapTemplateModel mapTemplateModel, List<PlayerRemainingTiles> allPlayerRemainingTiles) {

        DraftManager draftManager = game.getDraftManager();
        if(draftManager == null) {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "Error: Could not find draft manager to continue map building.");
            return;
        }
        
        // Get the tile positions, grouped by player, and then grouped again by placement order.
        Map<Integer, Map<Integer, List<String>>> placementGroups = getGroupedPositions(game, mapTemplateModel);

        // Get the group keys ordered, to find the next group to place.
        List<Integer> groupKeys = new ArrayList<>(placementGroups.keySet());
        groupKeys.sort(Integer::compareTo);

        // Find the first group that has a position without a tile
        List<String> nextGroup = null;
        for(Integer groupKey : groupKeys) {
            Map<Integer, List<String>> playerNumToTiles = placementGroups.get(groupKey);
            List<Entry<Integer, List<String>>> orderedPlayerPositions = playerNumToTiles.entrySet().stream().sorted(Entry.comparingByKey()).toList();
            for(Entry<Integer, List<String>> entry : orderedPlayerPositions) {
                Integer playerNum = entry.getKey();
                List<String> positions = entry.getValue();
                List<String> currentUnplacedPositions = new ArrayList<>();
                for(String pos : positions) {
                    Tile tileAtPos = game.getTileByPosition(pos);
                    if(tileAtPos == null || TileHelper.isDraftTile(tileAtPos.getTileModel())) {
                        // This position needs a tile placed
                        currentUnplacedPositions.add(pos);
                        break;
                    }
                }

                if(currentUnplacedPositions.isEmpty()) {
                    // No unplaced positions found
                    continue;
                }

                if(nextGroup == null) {
                    // If this is the first group with unplaced positions, start with it.
                    nextGroup = currentUnplacedPositions;
                } else if(currentUnplacedPositions.size() > nextGroup.size()) {
                    // If this group has more unplaced positions than the current nextGroup, use it instead.
                    nextGroup = currentUnplacedPositions;
                }
            }

            // If this group had any players with unplaced positions, we're done searching.
            if(nextGroup != null) break;
        }
        if(nextGroup == null || nextGroup.isEmpty()) {
            // All done!
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Map building complete!");
            // NOTE: This currently doesn't block anything, so there's nothing to unblock. But if it did, this is
            // where the unblocking would happen.
            return;
        }

        // Use the first position in the group to determine which player is placing next.
        String playerUserId = getPlayerUserIdForPosition(draftManager, mapTemplateModel, nextGroup.get(0));
        if(playerUserId == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Error: Could not determine player for next tile placement position at " + nextGroup.get(0) + ".");
            return;
        }

        Player player = game.getPlayer(playerUserId);
        if(player == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Error: Could not find player with ID " + playerUserId + " to place next tile.");
            return;
        }

        PlayerRemainingTiles playerRemainingTiles = allPlayerRemainingTiles.stream().filter(prt -> prt.playerUserId().equals(playerUserId)).findFirst().orElse(null);
        if(playerRemainingTiles == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Error: Could not find remaining tiles for player " + player.getRepresentation() + " to place next tile.");
            return;
        }

        DraftItem nextTile = drawTile(event, player, playerRemainingTiles, null);
        if(nextTile == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Error: Could not draw next tile for player " + player.getRepresentation() + ".");
            return;
        }
        sendDraftButtons(event, game, mantisDraftable, player, nextTile, nextGroup);
    }

    private void sendDraftButtons(GenericInteractionCreateEvent event, Game game, MantisTileDraftable mantisDraftable, Player player, DraftItem nextTile, List<String> openPositions) {
        if(nextTile == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Error: Could not find any available tiles for player " + player.getRepresentation() + " to place next tile.");
            return;
        }
        
        // Get the number of mulligans used by this player so far
        List<DraftChoice> playerPicks = game.getDraftManager().getPlayerPicks(player.getUserID(), MantisTileDraftable.TYPE);
        int mulligansUsed = (int) mantisDraftable.getMulliganTileIDs().stream()
                .filter(mulliganId -> playerPicks.stream().anyMatch(pick -> mantisDraftable.getItemId(pick.getChoiceKey()).equals(mulliganId)))
                .count();

        // TODO: Handle option to block adjacent anomalies when alternatives exist
        List<Button> buttons = new ArrayList<>();
        for(String pos : openPositions) {
            String buttonId = mantisDraftable.makeButtonId("build_place_" + pos + "_" + nextTile.ItemId);
            buttons.add(Buttons.blue(buttonId, pos));
        }
        // TODO: Handle option to adjust max mulligans
        if(mulligansUsed < 1) {
            String buttonId = mantisDraftable.makeButtonId("build_mulligan_" + nextTile.ItemId);
            buttons.add(Buttons.gray(buttonId, "Mulligan"));
        }
        buttons.add(Buttons.gray(mantisDraftable.makeButtonId("build_repost"), "Repost build info"));

        // Build message text
        StringBuilder sb = new StringBuilder(player.getRepresentation() + " can pick a position to place " + nextTile.getLongDescription());
        if(mulligansUsed < 1 ) {
            sb.append(" or mulligan to draw a different tile.");
        } else {
            sb.append(". (No mulligans remaining.)");
        }

        // If no event, just re-post
        if(event == null || !(event instanceof ButtonInteractionEvent)) {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), sb.toString());
        } else {
            ButtonInteractionEvent buttonEvent = (ButtonInteractionEvent) event;
            buttonEvent.getHook().editOriginal(sb.toString()).setComponents(ActionRow.partitionOf(buttons)).queue();
        }
    }

    private DraftItem drawTile(GenericInteractionCreateEvent event, Player player, PlayerRemainingTiles playerRemainingTiles, String mulliganTileId) {
        List<String> availableTileIDs = new ArrayList<>();
        availableTileIDs.addAll(playerRemainingTiles.blueTileIds());
        availableTileIDs.addAll(playerRemainingTiles.redTileIds());
        if(mulliganTileId != null) {
            availableTileIDs.remove(mulliganTileId);
        }
        if(availableTileIDs.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Error: Could not find any available tiles for player " + player.getRepresentation() + " to place next tile.");
            return null;
        }
        Collections.shuffle(availableTileIDs);
        String selectedTileId = availableTileIDs.get(0);
        Category category = playerRemainingTiles.blueTileIds().contains(selectedTileId) ? Category.BLUETILE : Category.REDTILE;
        return DraftItem.generate(category, selectedTileId);
    }

    /**
     * Get the user ID of the player at a given map position, based on speaker order.
     * @param draftManager
     * @param position 0-based position in the list of the map's player numbers
     * @return The user ID of the player at that position, or null if not found
     */
    private String getPlayerUserIdBySpeakerOrderPosition(DraftManager draftManager, int position) {
        // Find the player with the given speaker order position
        for(Entry<String, PlayerDraftState> entry : draftManager.getPlayerStates().entrySet()) {
            String playerId = entry.getKey();
            PlayerDraftState pState = entry.getValue();
            List<DraftChoice> picks = pState.getPicks(SpeakerOrderDraftable.TYPE);
            if(picks == null || picks.isEmpty()) {
                continue;
            }
            // This is 1-based
            Integer playerPosition = SpeakerOrderDraftable.getSpeakerOrderFromChoiceKey(picks.get(0).getChoiceKey());
            if(playerPosition != null && playerPosition == (1 + position)) {
                return playerId;
            }
        }
        return null;
    }

    private String getPlayerUserIdForPosition(DraftManager draftManager, MapTemplateModel mapTemplateModel, String position) {
        // Get the template tile at that position
        MapTemplateTile templateTile = mapTemplateModel.getTemplateTiles().stream()
                .filter(t -> t.getPos().equals(position))
                .findFirst()
                .orElse(null);
        if(templateTile == null || templateTile.getPlayerNumber() == null) {
            return null;
        }

        Integer mapTemplatePlayerNum = templateTile.getPlayerNumber();

        // Player numbers are just data, I don't trust them to be 1, 2, 3, n. Get the actual list of player numbers.
        List<Integer> playerNums = mapTemplateModel.getTemplateTiles().stream()
                .filter(t -> t.getPlayerNumber() != null)
                .map(MapTemplateTile::getPlayerNumber)
                .distinct()
                .sorted()
                .toList();
        // Find this player number's position in the list, and that index will correspond to the player's speaker order pick.
        int playerIndex = playerNums.indexOf(mapTemplatePlayerNum);
        if(playerIndex < 0) {
            return null;
        }

        // Get the user ID of the current group's player, based on speaker order
        String playerUserId = getPlayerUserIdBySpeakerOrderPosition(draftManager, playerIndex);
        return playerUserId;
    }

    /**
     * Gets all of the map tile positions in which a tile must be placed, grouped with the other positions
     * that could receive a tile at the same time. For example, the tile positions adjacent to Mecatol Rex
     * are all placed individually, so they're not grouped. But each player can choose whether to place their
     * ring 2 tiles in either position, so those positions ARE grouped.
     * Here's an example from a 3-player standard map:
     * [[101], [103], [105], [201, 203], [205, 207], [209, 211], [318, 302], [306, 308], [312, 314]]
     * @param game
     * @param mapTemplateModel
     * @return A map of group number (arbitrary, but can be used for ordering), to a map that translates
     * player numbers in the map template to a list of tile positions for that player in that group.
     */
    private Map<Integer, Map<Integer, List<String>>> getGroupedPositions(Game game, MapTemplateModel mapTemplateModel) {
        
        // I'm not sure the best way to generically group tiles that players can place in any order.
        // (for example, the two tiles on either side of your HS in a standard map; how are those logically
        // identified as a group, and how does that logic exclude the tile in front of the HS in ring 2?)

        // I think the best way right now is to take the template's emulated tile list, and use the ring number
        // on those to get the tile indexes to group together. An alternative could be to use the DistanceTool
        // to group tiles by their distance from the center tile, but that seems excessive and flakey.

        List<String> emulatedPositions = mapTemplateModel.emulatedTiles();
        // Remove the Home Position
        Predicate<MapTemplateTile> isHome = t -> t.getHome() != null && t.getHome();
        Set<String> homePositions = new HashSet<>(mapTemplateModel.getTemplateTiles().stream().filter(isHome).map(MapTemplateTile::getPos).toList());
        emulatedPositions.removeIf(homePositions::contains);

        // It weirds me out that playerNums start at 1, so get a list of ACTUAL player numbers used in the template
        // Just in case someone uses 0 or skips a number or something I dunno
        List<Integer> playerNums = mapTemplateModel.getTemplateTiles().stream()
                .filter(t -> t.getPlayerNumber() != null)
                .map(MapTemplateTile::getPlayerNumber)
                .distinct()
                .sorted()
                .toList();

        // For each emulated position, add each player's equivalent position to the correct group and add that group to the list.
        Map<Integer, Map<Integer, List<String>>> groupToPlayerNumToTiles = new HashMap<>();
        for(String pos : emulatedPositions) {
            // Get the actual template tile for the emulated position
            MapTemplateTile baseTile = mapTemplateModel.getTemplateTiles().stream().filter(t -> t.getPos().equals(pos)).findFirst().orElse(null);
            if(baseTile == null) {
                BotLogger.warning(new LogOrigin(game), "Could not find template tile for position " + pos + " in map template " + mapTemplateModel.getAlias() + ".");
                continue;
            }

            // Get the tile index this emulated position represents
            Integer tileIndex = baseTile.getMiltyTileIndex();
            // Get the grouping for this tile index
            Integer group = getTileGroup(baseTile);

            // For each player, find their equivalent position for this emulated tile
            // and add that position to the correct group for that player.
            for(int playerNum : playerNums) {
                String playerTilePos = mapTemplateModel.getTemplateTiles().stream()
                        .filter(t -> t.getPlayerNumber() != null && t.getPlayerNumber() == playerNum)
                        .filter(t -> t.getMiltyTileIndex() != null && t.getMiltyTileIndex().equals(tileIndex))
                        .map(MapTemplateTile::getPos)
                        .findFirst()
                        .orElse(null);
                if(playerTilePos == null) {
                    BotLogger.warning(new LogOrigin(game), "Could not find player tile for player " + playerNum + " and tile " + tileIndex + " in map template " + mapTemplateModel.getAlias() + ".");
                    continue;
                }

                groupToPlayerNumToTiles.putIfAbsent(group, new HashMap<>());
                Map<Integer, List<String>> playerNumToTiles = groupToPlayerNumToTiles.get(group);
                playerNumToTiles.putIfAbsent(playerNum, new ArrayList<>());
                playerNumToTiles.get(playerNum).add(playerTilePos);
            }
        }

        // For the groups that were found, get their group number in order
        // List<Integer> groupNumbers = new ArrayList<>(groupToPlayerNumToTiles.keySet());
        // groupNumbers.sort(Integer::compareTo);

        // Iterate through each group in order, and for each:
        // iterate through each player in order, and for each:
        // add that player's tiles for that group to the final ordered list of groups.
        // List<List<String>> groups = new ArrayList<>();
        // for(Integer groupNum : groupNumbers) {
        //     Map<Integer, List<String>> playerNumToTiles = groupToPlayerNumToTiles.get(groupNum);
        //     for(Integer playerNum : playerNums) {
        //         groups.add(playerNumToTiles.get(playerNum));
        //     }
        // }

        return groupToPlayerNumToTiles;
    }

    /**
     * This is somewhat arbitrary. I'm trying to create a general logic that will group
     * tile positions like we do in a standard map. Basically, just use the ring number
     * on the equivalent emulated tile to determine the group.
     */
    private Integer getTileGroup(MapTemplateTile emulatedTile) {
        String pos = emulatedTile.getPos();
        // Position 101 = group 1; Position 1304 = group 13
        return Integer.valueOf(pos.substring(0, pos.length() - 2));
    }

    // TODO: Implement over-drafting support, then will need this to discard down. These are untested.
    // private void sendDiscardButtons(Game game, MantisTileDraftable mantisDraftable, List<PlayerRemainingTiles> playerRemainingTiles, int bpp, int rpp) {
    //     for(PlayerRemainingTiles prt : playerRemainingTiles) {
    //         Player player = game.getPlayer(prt.playerUserId());
    //         if(player == null) {
    //             BotLogger.warning(new LogOrigin(game), "Could not find player with ID " + prt.playerUserId() + " to send mantis discard buttons.");
    //             continue;
    //         }
    //         MessageChannel playerChannel = player.getPrivateChannel();
    //         if(playerChannel == null) {
    //             BotLogger.warning(new LogOrigin(game), "Could not open private channel to player " + player.getRepresentation() + " to send mantis discard buttons.");
    //             continue;
    //         }

    //         int blueToDiscard = prt.blueTileIds().size() - bpp;
    //         if(blueToDiscard > 0) {
    //             sendDiscardButtons(game, mantisDraftable, player, playerChannel, Category.BLUETILE, prt.blueTileIds(), blueToDiscard);
    //         }

    //         int redToDiscard = prt.redTileIds().size() - rpp;
    //         if(redToDiscard > 0) {
    //             sendDiscardButtons(game, mantisDraftable, player, playerChannel, Category.REDTILE, prt.redTileIds(), redToDiscard);
    //         }
    //     }
    // }

    // private void sendDiscardButtons(Game game, MantisTileDraftable mantisDraftable, Player player, MessageChannel playerChannel, Category category, List<String> tileIds, int toDiscard) {
    //     if(toDiscard <= 0) return;
    //     if(tileIds == null || tileIds.isEmpty()) return;
    //     if(toDiscard > tileIds.size()) toDiscard = tileIds.size();
    //     List<Button> discardButtons = new ArrayList<>();
    //     for(String tileId : tileIds) {
    //         String buttonId = mantisDraftable.makeButtonId("build_discard_" + tileId);
    //         DraftItem item = DraftItem.generate(category, tileId);
    //         if(category == Category.BLUETILE) {
    //             discardButtons.add(Buttons.blue(buttonId, item.getShortDescription(), item.getItemEmoji()));
    //         } else if(category == Category.REDTILE) {
    //             discardButtons.add(Buttons.red(buttonId, item.getShortDescription(), item.getItemEmoji()));
    //         }
    //     }
    // }

    private boolean anyNeedsToDiscard(List<PlayerRemainingTiles> playerRemainingTiles, int bpp, int rpp) {
        for(PlayerRemainingTiles prt : playerRemainingTiles) {
            if(prt.blueTileIds().size() > bpp || prt.redTileIds().size() > rpp) {
                return true;
            }
        }
        return false;
    }

    private List<PlayerRemainingTiles> getPlayerRemainingTiles(DraftManager draftManager, MantisTileDraftable mantisDraftable) {
        List<PlayerRemainingTiles> result = new ArrayList<>();
        for(Entry<String, PlayerDraftState> entry : draftManager.getPlayerStates().entrySet()) {
            String playerId = entry.getKey();
            List<DraftChoice> mantisPicks = entry.getValue().getPicks(MantisTileDraftable.TYPE);
            result.add(PlayerRemainingTiles.create(playerId, mantisDraftable, mantisPicks));
        }
        return result;
    }

    private record PlayerRemainingTiles(String playerUserId, List<String> blueTileIds, List<String> redTileIds) {
        public static PlayerRemainingTiles create(String playerUserId, MantisTileDraftable mantisDraftable, List<DraftChoice> playerPicks) {
            List<String> blueTileIds = new ArrayList<>();
            List<String> redTileIds = new ArrayList<>();
            for(DraftChoice choice : playerPicks) {
                Category category = mantisDraftable.getItemCategory(choice.getChoiceKey());
                String tileId = mantisDraftable.getItemId(choice.getChoiceKey());
                if(category == Category.BLUETILE) {
                    blueTileIds.add(tileId);
                } else if(category == Category.REDTILE) {
                    redTileIds.add(tileId);
                }
            }
            return new PlayerRemainingTiles(playerUserId, blueTileIds, redTileIds);
        }
    }
}
