package ti4.service.draft;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.micrometer.common.lang.NonNull;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.draft.DraftItem;
import ti4.draft.DraftItem.Category;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.MapTemplateModel;
import ti4.service.draft.draftables.MantisTileDraftable;
import ti4.service.draft.draftables.SpeakerOrderDraftable;
import ti4.service.franken.FrankenDraftBagService;

public record MantisMapBuildContext(
            @Nonnull Game game,
            @Nonnull MapTemplateModel mapTemplateModel,
            @Nonnull Integer mulliganLimit,
            @Nonnull Function<String, String> makeButtonId,
            @Nullable String drawnTileId,
            @Nullable String mulliganedTileId,
            @NonNull Consumer<String> persistDrawnTile,
            @NonNull Consumer<String> persistMulligan,
            @NonNull Consumer<String> persistDiscard,
            // Player corresponding to the "playerNum" value in map templates
            @NonNull Function<Integer, Optional<Player>> getPlayerForPosition,
            @NonNull List<PlayerTiles> availablePlayerTiles,
            @NonNull Supplier<List<PlayerTiles>> getAvailableTiles,
            @Nonnull Consumer<GenericInteractionCreateEvent> buildCompleteCallback
    ) {

    public MantisMapBuildContext afterMulligan() {
        return new MantisMapBuildContext(
                game,
                mapTemplateModel,
                mulliganLimit,
                makeButtonId,
                null,
                drawnTileId, //mulliganedTileId = drawnTileId
                persistDrawnTile,
                persistMulligan,
                persistDiscard,
                getPlayerForPosition,
                getAvailableTiles.get(),
                getAvailableTiles,
                buildCompleteCallback
        );
    }

    public MantisMapBuildContext withRegeneratedPlayerTiles() {
        return new MantisMapBuildContext(
                game,
                mapTemplateModel,
                mulliganLimit,
                makeButtonId,
                drawnTileId,
                mulliganedTileId,
                persistDrawnTile,
                persistMulligan,
                persistDiscard,
                getPlayerForPosition,
                getAvailableTiles.get(),
                getAvailableTiles,
                buildCompleteCallback
        );
    }

    public MantisMapBuildContext afterTilePlaced() {
        persistDrawnTile().accept(null);
        return new MantisMapBuildContext(
                game,
                mapTemplateModel,
                mulliganLimit,
                makeButtonId,
                null,
                mulliganedTileId,
                persistDrawnTile,
                persistMulligan,
                persistDiscard,
                getPlayerForPosition,
                getAvailableTiles.get(),
                getAvailableTiles,
                buildCompleteCallback
        );
    }

    public static MantisMapBuildContext from(@Nonnull DraftManager draftManager, @NonNull MantisTileDraftable mantisTileDraftable) {
        String mapTemplateId = draftManager.getGame().getMapTemplateID();
        if(mapTemplateId == null) {
            throw new IllegalStateException("Game does not have a map template ID set.");
        }
        MapTemplateModel mapTemplateModel = Mapper.getMapTemplate(mapTemplateId);
        if(mapTemplateModel == null) {
            throw new IllegalStateException("Could not find map template model for ID: " + mapTemplateId);
        }
        return new MantisMapBuildContext(
                draftManager.getGame(),
                mapTemplateModel,
                mantisTileDraftable.getMulligans(),
                mantisTileDraftable::makeButtonId,
                mantisTileDraftable.getDrawnTileId(),
                null,
                (String tileId) -> mantisTileDraftable.setDrawnTileId(tileId),
                (String tileId) -> mantisTileDraftable.getMulliganTileIDs().add(tileId),
                (String tileId) -> mantisTileDraftable.getDiscardedTileIDs().add(tileId),
                (Integer playerNum) -> getPlayerBySpeakerOrder(draftManager, playerNum),
                getPlayerTileState(draftManager, mantisTileDraftable),
                () -> getPlayerTileState(draftManager, mantisTileDraftable),
                draftManager::trySetupPlayers
        );
    }

    /**
     * Get the player at a given map position, based on speaker order.
     * @param draftManager The draft manager
     * @param playerNum The player number from the map template
     */
    private static Optional<Player> getPlayerBySpeakerOrder(DraftManager draftManager, int playerNum) {

        if (draftManager.getDraftable(SpeakerOrderDraftable.TYPE) == null) {
            MessageHelper.sendMessageToChannel(
                    draftManager.getGame().getMainGameChannel(),
                    "Error: Speaker order draftable is not enabled, cannot determine player order.");
            return Optional.empty();
        }

        Game game = draftManager.getGame();

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

            if (playerPosition != null && playerPosition == playerNum) {
                if(game.getPlayer(playerId) == null) {
                    return Optional.empty();
                }
                return Optional.of(game.getPlayer(playerId));
            }
        }
        return Optional.empty();
    }

    private static List<PlayerTiles> getPlayerTileState(
        DraftManager draftManager, MantisTileDraftable mantisDraftable) {
        List<PlayerTiles> result = new ArrayList<>();
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
            result.add(PlayerTiles.create(playerId, mantisDraftable, mantisPicks));
        }
        return result;
    }

    public static MantisMapBuildContext fromFranken(@Nonnull Game game) {
        String mapTemplateId = game.getMapTemplateID();
        if(mapTemplateId == null) {
            throw new IllegalStateException("Game does not have a map template ID set.");
        }
        MapTemplateModel mapTemplateModel = Mapper.getMapTemplate(mapTemplateId);
        if(mapTemplateModel == null) {
            throw new IllegalStateException("Could not find map template model for ID: " + mapTemplateId);
        }
        return new MantisMapBuildContext(
                game,
                mapTemplateModel,
                1,
                (String buttonAction) -> FrankenDraftBagService.ACTION_NAME + buttonAction,
                game.getStoredValue("frankenMapBuildTileId"),
                null,
                tileId -> game.setStoredValue("frankenMapBuildTileId", tileId),
                tileId -> {
                    String mulligans = game.getStoredValue("frankenMapBuildMulligans");
                    if (mulligans == null || mulligans.isEmpty()) {
                        mulligans = tileId;
                    } else {
                        mulligans += "," + tileId;
                    }
                    game.setStoredValue("frankenMapBuildMulligans", mulligans);
                },
                tileId -> {
                    String discards = game.getStoredValue("frankenMapBuildDiscards");
                    if (discards == null || discards.isEmpty()) {
                        discards = tileId;
                    } else {
                        discards += "," + tileId;
                    }
                    game.setStoredValue("frankenMapBuildDiscards", discards);
                },
                playerNum -> {
                    for(Player player : game.getPlayers().values()) {
                        if(player.getCurrentDraftBag() == null) {
                            continue;
                        }
                        boolean hasSpeakerOrder = player.getCurrentDraftBag().Contents.stream()
                            .filter(item -> item.ItemCategory == Category.DRAFTORDER)
                            .filter(item -> item.ItemId.equals("" + playerNum))
                            .findFirst().isPresent();
                        if(hasSpeakerOrder) {
                            return Optional.of(player);
                        }
                    }
                    return Optional.empty();
                },
                getPlayerTileStateFranken(game),
                () -> getPlayerTileStateFranken(game),
                event -> {
                    game.setStoredValue("frankenMapBuildTileId", null);
                    game.setStoredValue("frankenMapBuildMulligans", null);
                    game.setStoredValue("frankenMapBuildDiscards", null);
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Map build complete!");
                }
        );
    }

    private static List<PlayerTiles> getPlayerTileStateFranken(Game game) {
        List<PlayerTiles> result = new ArrayList<>();
        Set<String> placedTiles = game.getTileMap().values().stream()
                .map(t -> t.getTileID())
                .collect(Collectors.toSet());
        String discardedTilesStr = game.getStoredValue("frankenMapBuildDiscards");
        Set<String> discardedTiles = new HashSet<>();
        if (discardedTilesStr != null && !discardedTilesStr.isEmpty()) {
            discardedTiles.addAll(Arrays.asList(discardedTilesStr.split(",")));
        }
        String mulliganedTilesStr = game.getStoredValue("frankenMapBuildMulligans");
        List<String> mulliganedTiles = new ArrayList<>();
        if (mulliganedTilesStr != null && !mulliganedTilesStr.isEmpty()) {
            mulliganedTiles.addAll(Arrays.asList(mulliganedTilesStr.split(",")));
        }
        for (Player player : game.getPlayers().values()) {
            if (player.getCurrentDraftBag() == null) {
                continue;
            }
            List<DraftItem> tileItems = player.getCurrentDraftBag().Contents.stream()
                    .filter(item -> item.ItemCategory == Category.BLUETILE || item.ItemCategory == Category.REDTILE)
                    .collect(Collectors.toList());

            // Count how many of the mulliganed tiles are in this bag (one tile can be mulliganed more than once)
            int mulligansUsed = 0;
            for(String mulliganedTileId : mulliganedTiles) {
                boolean isInBag = tileItems.stream()
                        .filter(item -> item.ItemId.equals(mulliganedTileId))
                        .findFirst().isPresent();
                if(isInBag) {
                    mulligansUsed++;
                }
            }

            // Remove tiles that are placed on the game board
            tileItems.removeIf(tileItem -> placedTiles.contains(tileItem.ItemId));
            // Remove tiles that the player discarded
            tileItems.removeIf(tileItem -> discardedTiles.contains(tileItem.ItemId));

            result.add(PlayerTiles.create(player.getUserID(), tileItems, mulligansUsed));
        }

        return result;
    }

    public static record PlayerTiles(String playerUserId, List<String> blueTileIds, List<String> redTileIds, Integer mulligansUsed) {
        public static PlayerTiles create(
            String playerUserId, MantisTileDraftable mantisDraftable, List<DraftChoice> playerPicks) {

            Integer mulligansUsed = 0;
            List<String> blueTileIds = new ArrayList<>();
            List<String> redTileIds = new ArrayList<>();
            for (DraftChoice choice : playerPicks) {
                
                // Add choice to the correct list
                Category category = mantisDraftable.getItemCategory(choice.getChoiceKey());
                String tileId = MantisTileDraftable.getItemId(choice.getChoiceKey());
                if (category == Category.BLUETILE) {
                    blueTileIds.add(tileId);
                } else if (category == Category.REDTILE) {
                    redTileIds.add(tileId);
                }

                // For each time the tile was mulliganed, add to the count
                if (mantisDraftable.getMulliganTileIDs().contains(tileId)) {
                    mulligansUsed += (int) mantisDraftable.getMulliganTileIDs().stream()
                            .filter(mulliganId -> mulliganId.equals(tileId))
                            .count();
                }
            }
            return new PlayerTiles(playerUserId, blueTileIds, redTileIds, mulligansUsed);
        }

        public static PlayerTiles create(String playerUserId, List<DraftItem> allTiles, Integer mulligansUsed) {
            List<String> blueTileIds = allTiles.stream()
                    .filter(item -> item.ItemCategory == Category.BLUETILE)
                    .map(item -> item.ItemId)
                    .collect(Collectors.toList());
            List<String> redTileIds = allTiles.stream()
                    .filter(item -> item.ItemCategory == Category.REDTILE)
                    .map(item -> item.ItemId)
                    .collect(Collectors.toList());
            return new PlayerTiles(playerUserId, blueTileIds, redTileIds, mulligansUsed);
        }
    }
}