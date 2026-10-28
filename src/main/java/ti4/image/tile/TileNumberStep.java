package ti4.image.tile;

import static ti4.image.tile.TileGeometry.TILE_PADDING;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Point;
import ti4.image.draw.HorizontalAlign;
import ti4.image.draw.OutlinedText;
import ti4.image.draw.VerticalAlign;

final class TileNumberStep implements TileStepPainter {

    private static final Point POSITION = new Point(255, 295);
    private static final BasicStroke OUTLINE = new BasicStroke(7.0f);

    @Override
    public void paint(TileScene scene, TileLayer layer) {
        TileText.applyTextSize(layer.g(), scene.game());
        OutlinedText.draw(
                layer.g(),
                layer.tile().getPosition(),
                TILE_PADDING + POSITION.x,
                TILE_PADDING + POSITION.y,
                Color.WHITE,
                HorizontalAlign.Right,
                VerticalAlign.Bottom,
                OUTLINE,
                Color.BLACK);
    }
}
