package ti4.image;

import static ti4.image.TileGeometry.TILE_PADDING;

import ti4.game.Game;
import ti4.game.Tile;

final class HighlightGuards {

    private HighlightGuards() {}

    static boolean isFogOrHyperlane(Game game, Tile tile) {
        return game.isFowMode() || tile.getTileModel().isHyperlane();
    }

    static boolean isTileImageMissing(Tile tile) {
        return ImageHelper.read(tile.getTilePath()) == null;
    }

    static void fogOut(TileLayer layer) {
        layer.g().drawImage(ImageHelper.read(layer.tile().getFowTilePath(null)), TILE_PADDING, TILE_PADDING, null);
    }
}
