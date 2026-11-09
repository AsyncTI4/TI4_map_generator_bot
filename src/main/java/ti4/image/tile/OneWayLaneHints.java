package ti4.image.tile;

import static ti4.image.tile.TileGeometry.TILE_PADDING;

import java.awt.Graphics2D;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.helpers.adjacency.GridRule;
import ti4.model.MapPositionMapper;
import ti4.service.fow.FOWPlusService;
import ti4.service.map.CustomHyperlaneService;
import ti4.service.option.FOWOptionService.FOWOption;

final class OneWayLaneHints {

    private OneWayLaneHints() {}

    static Set<String> tilesThatMayGlow(Game game) {
        Set<String> tiles = new HashSet<>();
        if (!areShown(game)) {
            return tiles;
        }
        game.getCustomHyperlaneData().forEach((position, lanes) -> {
            if (hasReadableOneWayLanes(lanes)) {
                tiles.addAll(MapPositionMapper.getAdjacentTilePositions(position));
            }
        });
        return tiles;
    }

    static void drawOn(TileScene scene, TileLayer layer) {
        if (!areShown(scene.game())) {
            return;
        }
        String position = layer.tile().getPosition();
        List<String> neighbours = MapPositionMapper.getAdjacentTilePositions(position);
        if (neighbours.size() != 6) {
            return;
        }
        Set<Integer> lanesPassOn = BridgeEndsLookup.sidesWhereArrivingLanesPassOn(scene, layer.tile());
        for (int side = 0; side < 6; side++) {
            if (!lanesPassOn.contains(side) && oneWayLaneLeadsInto(scene, position, side, neighbours.get(side))) {
                glowOnEdge(layer, side);
            }
        }
    }

    private static boolean areShown(Game game) {
        return game.isFowMode()
                && game.getFowOption(FOWOption.ONE_WAY_LANE_HINTS)
                && !game.getCustomHyperlaneData().isEmpty();
    }

    private static boolean oneWayLaneLeadsInto(TileScene scene, String position, int side, String neighbourPosition) {
        Game game = scene.game();
        Tile neighbour = game.getTileByPosition(neighbourPosition);
        int neighbourSide = (side + 3) % 6;
        if (neighbour == null
                || !CustomHyperlaneService.isCustomHyperlaneTile(neighbour)
                || !scene.seesPosition(neighbourPosition)
                || game.getAdjacentTileOverride(neighbourPosition, neighbourSide) != null
                || GridRule.isBorderBlocked(game, scene.fowPlayer(), neighbourPosition, neighbourSide, position, side)
                || !FOWPlusService.shouldTraverseAdjacency(game, position, side)) {
            return false;
        }
        String lanesTheViewerSees = TileArtwork.drawnLanes(scene, neighbour);
        return hasReadableOneWayLanes(lanesTheViewerSees)
                && CustomHyperlaneService.oneWayExitSides(lanesTheViewerSees).contains(neighbourSide);
    }

    private static boolean hasReadableOneWayLanes(String lanes) {
        return lanes != null
                && CustomHyperlaneService.isValidStoredMatrix(lanes)
                && CustomHyperlaneService.hasOneWayLanes(lanes);
    }

    private static void glowOnEdge(TileLayer layer, int side) {
        Graphics2D g = (Graphics2D) layer.g().create();
        g.translate(TILE_PADDING, TILE_PADDING);
        OneWayHyperlaneOverlay.drawLaneHint(g, side);
        g.dispose();
    }
}
