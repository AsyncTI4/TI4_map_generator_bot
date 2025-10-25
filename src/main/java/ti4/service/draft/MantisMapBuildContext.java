package ti4.service.draft;


import java.util.ArrayList;
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
import ti4.draft.DraftItem.Category;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.MapTemplateModel;
import ti4.service.draft.draftables.MantisTileDraftable;
import ti4.service.draft.draftables.SpeakerOrderDraftable;

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

        public MantisMapBuildContext withMulligan() {
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

        public MantisMapBuildContext regeneratePlayerTiles() {
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
                    (Integer playerNum) -> getPlayerUserIdBySpeakerOrder(draftManager, playerNum),
                    getPlayerTileState(draftManager, mantisTileDraftable),
                    () -> getPlayerTileState(draftManager, mantisTileDraftable),
                    draftManager::trySetupPlayers
            );
        }

        /**
         * Get the user ID of the player at a given map position, based on speaker
         * order.
         * @param draftManager
         * @param playerNum The player number from the map template
         */
        private static Optional<Player> getPlayerUserIdBySpeakerOrder(DraftManager draftManager, int playerNum) {

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
    }
}