package ti4.service.franken;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.math.NumberUtils;
import ti4.draft.DraftItem;
import ti4.draft.DraftItem.Category;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.model.MapTemplateModel;
import ti4.service.draft.MantisMapBuildContext;
import ti4.service.draft.MantisMapBuildContext.PlayerTiles;

@UtilityClass
public class FrankenMapBuildContextHelper {
    private static final String MULLIGAN_LIMIT_KEY = "frankenMapBuildMulliganLimit";
    private static final String DRAWN_TILE_KEY = "frankenMapBuildTileId";
    private static final String MULLIGANS_KEY = "frankenMapBuildMulligans";
    private static final String DISCARDS_KEY = "frankenMapBuildDiscards";

    public static MantisMapBuildContext createContext(@Nonnull Game game) {
        String mapTemplateId = game.getMapTemplateID();
        if (mapTemplateId == null) {
            throw new IllegalStateException("Game does not have a map template ID set.");
        }
        MapTemplateModel mapTemplateModel = Mapper.getMapTemplate(mapTemplateId);
        if (mapTemplateModel == null) {
            throw new IllegalStateException("Could not find map template model for ID: " + mapTemplateId);
        }
        return new MantisMapBuildContext(
                game,
                mapTemplateModel,
                NumberUtils.toInt(game.getStoredValue(MULLIGAN_LIMIT_KEY), 1),
                (String buttonAction) -> FrankenDraftBagService.ACTION_NAME + buttonAction,
                game.getStoredValue(DRAWN_TILE_KEY),
                null,
                tileId -> game.setStoredValue(DRAWN_TILE_KEY, tileId),
                tileId -> {
                    String mulligans = game.getStoredValue(MULLIGANS_KEY);
                    if (mulligans == null || mulligans.isEmpty()) {
                        mulligans = tileId;
                    } else {
                        mulligans += "," + tileId;
                    }
                    game.setStoredValue(MULLIGANS_KEY, mulligans);
                },
                tileId -> {
                    String discards = game.getStoredValue(DISCARDS_KEY);
                    if (discards == null || discards.isEmpty()) {
                        discards = tileId;
                    } else {
                        discards += "," + tileId;
                    }
                    game.setStoredValue(DISCARDS_KEY, discards);
                },
                playerNum -> {
                    for (Player player : game.getRealPlayers()) {
                        if (player.getDraftHand() == null) {
                            continue;
                        }
                        boolean hasSpeakerOrder = player.getDraftHand().Contents.stream()
                                .filter(item -> item.ItemCategory == Category.DRAFTORDER)
                                .filter(item -> item.ItemId.equals("" + playerNum))
                                .findFirst()
                                .isPresent();
                        if (hasSpeakerOrder) {
                            return Optional.of(player);
                        }
                    }
                    return Optional.empty();
                },
                getPlayerTileStateFranken(game),
                () -> getPlayerTileStateFranken(game),
                event -> {
                    game.removeStoredValue(DRAWN_TILE_KEY);
                    game.removeStoredValue(MULLIGANS_KEY);
                    game.removeStoredValue(DISCARDS_KEY);
                    game.removeStoredValue(MULLIGAN_LIMIT_KEY);
                });
    }

    private static List<PlayerTiles> getPlayerTileStateFranken(Game game) {
        List<PlayerTiles> result = new ArrayList<>();
        Set<String> placedTiles =
                game.getTileMap().values().stream().map(t -> t.getTileID()).collect(Collectors.toSet());
        String discardedTilesStr = game.getStoredValue(DISCARDS_KEY);
        Set<String> discardedTiles = new HashSet<>();
        if (discardedTilesStr != null && !discardedTilesStr.isEmpty()) {
            discardedTiles.addAll(Arrays.asList(discardedTilesStr.split(",")));
        }
        String mulliganedTilesStr = game.getStoredValue(MULLIGANS_KEY);
        List<String> mulliganedTiles = new ArrayList<>();
        if (mulliganedTilesStr != null && !mulliganedTilesStr.isEmpty()) {
            mulliganedTiles.addAll(Arrays.asList(mulliganedTilesStr.split(",")));
        }
        for (Player player : game.getRealPlayers()) {
            if (player.getDraftHand() == null) {
                continue;
            }
            List<DraftItem> tileItems = player.getDraftHand().Contents.stream()
                    .filter(item -> item.ItemCategory == Category.BLUETILE || item.ItemCategory == Category.REDTILE)
                    .collect(Collectors.toList());

            // Count how many of the mulliganed tiles are in this bag (one tile can be
            // mulliganed more than once)
            int mulligansUsed = 0;
            for (String mulliganedTileId : mulliganedTiles) {
                boolean isInBag = tileItems.stream()
                        .filter(item -> item.ItemId.equals(mulliganedTileId))
                        .findFirst()
                        .isPresent();
                if (isInBag) {
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
}
