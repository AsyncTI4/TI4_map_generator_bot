package ti4.image.tile;

import static ti4.image.tile.TileGeometry.TILE_PADDING;

import java.awt.Graphics2D;
import java.util.List;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.model.MapPositionMapper;
import ti4.service.map.CustomHyperlaneService;

final class OneWayEntryHints {

    private OneWayEntryHints() {}

    static void draw(TileScene scene, TileLayer layer) {
        List<String> neighbours =
                MapPositionMapper.getAdjacentTilePositions(layer.tile().getPosition());
        if (neighbours == null || neighbours.size() != 6) {
            return;
        }
        for (int side = 0; side < 6; side++) {
            if (oneWayLaneLeadsHere(scene, neighbours.get(side), (side + 3) % 6)) {
                Graphics2D g = (Graphics2D) layer.g().create();
                g.translate(TILE_PADDING, TILE_PADDING);
                OneWayHyperlaneOverlay.drawEntryHint(g, side);
                g.dispose();
            }
        }
    }

    private static boolean oneWayLaneLeadsHere(TileScene scene, String neighbourPosition, int neighbourSide) {
        Game game = scene.game();
        Tile neighbour = game.getTileByPosition(neighbourPosition);
        if (neighbour == null
                || !CustomHyperlaneService.isCustomHyperlaneTile(neighbour)
                || !scene.seesPosition(neighbourPosition)
                || game.getAdjacentTileOverride(neighbourPosition, neighbourSide) != null) {
            return false;
        }
        String lanes = TileArtwork.drawnLanes(scene, neighbour);
        return lanes != null && CustomHyperlaneService.oneWayExitSides(lanes).contains(neighbourSide);
    }
}
