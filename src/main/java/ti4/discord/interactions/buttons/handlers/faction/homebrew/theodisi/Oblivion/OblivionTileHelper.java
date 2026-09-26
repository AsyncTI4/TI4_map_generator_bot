package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Oblivion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.image.TileHelper;
import ti4.model.TileModel;
import ti4.model.TileModel.TileBack;
import ti4.service.map.AddTileService;
import ti4.service.milty.MiltyDraftTile;

@UtilityClass
public class OblivionTileHelper {

    public static List<String> drawUnusedTiles(Game game, Integer redCount, Integer blueCount) {
        if (game == null) {
            return List.of();
        }

        int redTilesToDraw = redCount == null ? 0 : Math.max(0, redCount);
        int blueTilesToDraw = blueCount == null ? 0 : Math.max(0, blueCount);
        if (redTilesToDraw + blueTilesToDraw == 0) {
            return List.of();
        }

        List<MiltyDraftTile> unusedTiles = new ArrayList<>(Helper.getUnusedTiles(game).stream()
                .filter(tile -> {
                    TileModel tileModel = tile.getTile().getTileModel();
                    return tileModel != null
                            && (tileModel.getTileBack() == TileBack.RED || tileModel.getTileBack() == TileBack.BLUE)
                            && !tileModel.isHyperlane();
                })
                .toList());
        Collections.shuffle(unusedTiles);

        List<String> drawnTiles = new ArrayList<>();
        unusedTiles.stream()
                .filter(tile -> tile.getTile().getTileModel().getTileBack() == TileBack.RED)
                .limit(redTilesToDraw)
                .map(tile -> tile.getTile().getTileID())
                .forEach(drawnTiles::add);
        unusedTiles.stream()
                .filter(tile -> tile.getTile().getTileModel().getTileBack() == TileBack.BLUE)
                .limit(blueTilesToDraw)
                .map(tile -> tile.getTile().getTileID())
                .forEach(drawnTiles::add);
        return drawnTiles;
    }

    public static void purgeTiles(Game game, Collection<String> tileIds) {
        if (game == null || tileIds == null || tileIds.isEmpty()) {
            return;
        }

        Set<String> purgedTileIds = new LinkedHashSet<>();
        String storedPurgedTiles = game.getStoredValue(Constants.PURGED_MAP_TILES);
        if (!storedPurgedTiles.isBlank()) {
            Collections.addAll(purgedTileIds, storedPurgedTiles.split(","));
        }
        tileIds.stream().filter(tileId -> tileId != null && !tileId.isBlank()).forEach(purgedTileIds::add);
        game.setStoredValue(Constants.PURGED_MAP_TILES, String.join(",", purgedTileIds));
    }

    public static List<Button> getPlacementButtons(Game game, Player player, String tileId, String buttonPrefix) {
        if (game == null
                || player == null
                || TileHelper.getTileById(tileId) == null
                || buttonPrefix == null
                || buttonPrefix.isBlank()) {
            return List.of();
        }

        List<Button> buttons = new ArrayList<>();
        Set<String> offeredPositions = new LinkedHashSet<>();
        for (Tile tile : game.getTileMap().values()) {
            for (String position : PositionMapper.getAdjacentTilePositions(tile.getPosition())) {
                if (!offeredPositions.add(position) || !isLegalDestination(game, tileId, position)) {
                    continue;
                }

                List<Tile> adjacentSystems = getAdjacentSystems(game, position);
                Tile firstSystem = adjacentSystems.getFirst();
                Tile secondSystem = adjacentSystems.get(1);
                buttons.add(Buttons.green(
                        buttonPrefix + tileId + "_" + position,
                        "Place Between " + firstSystem.getPosition() + " and " + secondSystem.getPosition()));
            }
        }
        return buttons;
    }

    public static boolean hasLegalPlacement(Game game, String tileId) {
        if (game == null || TileHelper.getTileById(tileId) == null) {
            return false;
        }
        for (Tile tile : game.getTileMap().values()) {
            for (String position : PositionMapper.getAdjacentTilePositions(tile.getPosition())) {
                if (isLegalDestination(game, tileId, position)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static Tile placeTile(Game game, String tileId, String position) {
        if (game == null
                || game.getTile(tileId) != null
                || TileHelper.getTileById(tileId) == null
                || !isLegalDestination(game, tileId, position)) {
            return null;
        }

        Tile tile = new Tile(tileId, position);
        AddTileService.addTile(game, tile);
        if (tile.getPlanetUnitHolders().isEmpty()) {
            tile.addToken(Mapper.getTokenID(Constants.FRONTIER), Constants.SPACE);
        }
        return tile;
    }

    private static boolean isLegalDestination(Game game, String tileId, String position) {
        Tile destination = game.getTileByPosition(position);
        if ((destination != null && !"silver_flame".equals(destination.getTileID()))
                || getAdjacentSystems(game, position).size() < 2) {
            return false;
        }
        return new Tile(tileId, position).isEdgeOfBoard(game);
    }

    private static List<Tile> getAdjacentSystems(Game game, String position) {
        return PositionMapper.getAdjacentTilePositions(position).stream()
                .map(game::getTileByPosition)
                .filter(tile -> tile != null
                        && !"silver_flame".equals(tile.getTileID())
                        && (tile.getTileModel() == null || !tile.getTileModel().isHyperlane()))
                .sorted(Comparator.comparing(Tile::getPosition))
                .toList();
    }
}
