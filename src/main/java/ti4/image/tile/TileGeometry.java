package ti4.image.tile;

import java.awt.Point;
import ti4.game.Tile;
import ti4.model.ShipPositionModel.ShipPosition;
import ti4.model.TileModel;

public final class TileGeometry {

    public static final int TILE_WIDTH = 345;
    public static final int TILE_HEIGHT = 300;
    public static final int TILE_PADDING = 100;
    public static final int LAYER_SIZE = 600;

    private TileGeometry() {}

    public static Point hexOffsetInArtwork(Tile tile) {
        TileModel model = tile.getTileModel();
        ShipPosition positions = model == null ? null : model.getShipPositionsType();
        if (positions != null && positions.isSpiral()) {
            return new Point(TileLayer.SPIRAL_OFFSET_X, TileLayer.SPIRAL_OFFSET_Y);
        }
        return new Point(0, 0);
    }

    public static boolean followsArtworkOffset(TileStep step) {
        return step != TileStep.TileNumber;
    }
}
