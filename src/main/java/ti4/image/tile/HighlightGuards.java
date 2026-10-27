package ti4.image.tile;

import static ti4.image.tile.TileGeometry.TILE_PADDING;

import ti4.game.Game;
import ti4.game.Tile;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;

final class HighlightGuards {

    private HighlightGuards() {}

    static boolean isFogOrHyperlane(Game game, Tile tile) {
        return game.isFowMode() || tile.getTileModel().isHyperlane();
    }

    static boolean isTileImageMissing(Tile tile) {
        return ImageHelper.read(ImageResources.tilePath(tile)) == null;
    }

    static void fogOut(TileLayer layer) {
        layer.g()
                .drawImage(
                        ImageHelper.read(ImageResources.fogTilePath(layer.tile(), null)),
                        TILE_PADDING,
                        TILE_PADDING,
                        null);
    }
}
