package ti4.image.tile;

import java.awt.Point;
import java.awt.image.BufferedImage;
import ti4.game.Tile;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.model.TileModel;
import ti4.service.map.CustomHyperlaneService;

public final class TileGeometry {

    public static final int TILE_WIDTH = 345;
    public static final int TILE_HEIGHT = 300;
    public static final int TILE_PADDING = 100;
    public static final int LAYER_SIZE = 600;

    private TileGeometry() {}

    public static Point hexOffsetInArtwork(Tile tile) {
        if (CustomHyperlaneService.isCustomHyperlaneTile(tile)) {
            return new Point();
        }
        TileModel model = tile.getTileModel();
        String path = model == null ? null : ImageResources.tilePath(model);
        BufferedImage artwork = path == null ? null : ImageHelper.read(path);
        if (artwork == null) {
            return new Point();
        }
        return new Point((artwork.getWidth() - TILE_WIDTH) / 2, (artwork.getHeight() - TILE_HEIGHT) / 2);
    }

    public static boolean followsArtworkOffset(TileStep step) {
        return step != TileStep.TileNumber;
    }
}
