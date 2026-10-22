package ti4.image.tile;

import static ti4.image.tile.TileGeometry.TILE_PADDING;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.annotation.Nullable;
import ti4.game.Tile;
import ti4.model.ShipPositionModel.ShipPosition;

record TileLayer(Tile tile, @Nullable ShipPosition shipPositions, Graphics2D g) {

    static final int SPIRAL_OFFSET_X = 36;
    static final int SPIRAL_OFFSET_Y = 43;

    boolean isSpiral() {
        return shipPositions != null && shipPositions.isSpiral();
    }

    int spiralOffsetX() {
        return isSpiral() ? SPIRAL_OFFSET_X : 0;
    }

    int spiralOffsetY() {
        return isSpiral() ? SPIRAL_OFFSET_Y : 0;
    }

    void drawOnHex(BufferedImage image, int x, int y) {
        g.drawImage(image, TILE_PADDING + x, TILE_PADDING + y, null);
    }
}
