package ti4.image;

import static ti4.image.TileGeometry.TILE_PADDING;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.image.BufferedImage;
import ti4.ResourceHelper;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.helpers.Storage;

final class DistanceStep implements TileStepPainter {

    private static final int UNREACHABLE = 100;
    private static final int MAX_LABELLED_DISTANCE = 10;
    private static final BasicStroke OUTLINE = new BasicStroke(4.0f);

    @Override
    public void paint(TileScene scene, TileLayer layer) {
        Game game = scene.game();
        if (game.isFowMode()) {
            return;
        }
        Tile tile = layer.tile();
        Integer distance = game.getTileDistances().get(tile.getPosition());
        if (distance == null) {
            distance = UNREACHABLE;
        }

        BufferedImage tileImage = ImageHelper.read(tile.getTilePath());
        if (tileImage == null) {
            return;
        }
        int x = TILE_PADDING + layer.spiralOffsetX();
        int y = TILE_PADDING + layer.spiralOffsetY();
        if (distance > 0) {
            BufferedImage distanceColor = ImageHelper.read(ResourceHelper.getTileFile("Distance" + distance + ".png"));
            layer.g().drawImage(distanceColor, x, y, null);
        }
        if (distance <= MAX_LABELLED_DISTANCE) {
            layer.g().setFont(Storage.getFont110());
            DrawingUtil.superDrawString(
                    layer.g(),
                    distance.toString(),
                    x + tileImage.getWidth() / 2,
                    y + tileImage.getHeight() / 2,
                    Color.WHITE,
                    HorizontalAlign.Center,
                    VerticalAlign.Center,
                    OUTLINE,
                    Color.BLACK);
        }
    }
}
