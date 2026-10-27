package ti4.image.tile;

import static ti4.image.tile.TileGeometry.TILE_PADDING;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.image.BufferedImage;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.image.draw.HorizontalAlign;
import ti4.image.draw.OutlinedText;
import ti4.image.draw.Strokes;
import ti4.image.draw.VerticalAlign;
import ti4.image.io.Fonts;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;

final class DistanceStep implements TileStepPainter {

    private static final int UNREACHABLE = 100;
    private static final int MAX_LABELLED_DISTANCE = 10;
    private static final BasicStroke OUTLINE = Strokes.STROKE_4;

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

        BufferedImage tileImage = ImageHelper.read(ImageResources.tilePath(tile));
        if (tileImage == null) {
            return;
        }
        int x = TILE_PADDING + layer.spiralOffsetX();
        int y = TILE_PADDING + layer.spiralOffsetY();
        if (distance > 0) {
            BufferedImage distanceColor = ImageHelper.read(ImageResources.tile("Distance" + distance + ".png"));
            layer.g().drawImage(distanceColor, x, y, null);
        }
        if (distance <= MAX_LABELLED_DISTANCE) {
            layer.g().setFont(Fonts.ti(110));
            OutlinedText.draw(
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
