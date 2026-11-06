package ti4.image.tile;

import static ti4.image.tile.TileGeometry.TILE_PADDING;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import ti4.game.Tile;
import ti4.image.draw.OutlinedText;
import ti4.image.io.Fonts;

final class EmptiesStep implements TileStepPainter {

    private static final int MARK_X = 93;
    private static final int MARK_Y = 70;
    private static final int MARK_SIZE = 159;

    @Override
    public void paint(TileScene scene, TileLayer layer) {
        Tile tile = layer.tile();
        if (HighlightGuards.isHiddenOrHyperlane(scene, tile) || HighlightGuards.isTileImageMissing(tile)) {
            return;
        }
        if (!tile.getPlanetUnitHolders().isEmpty()) {
            HighlightGuards.fogOut(layer);
            return;
        }
        int x = TILE_PADDING + layer.spiralOffsetX() + MARK_X;
        int y = TILE_PADDING + layer.spiralOffsetY() + MARK_Y;
        Graphics2D g = layer.g();
        g.setColor(Color.RED);
        g.fillOval(x, y, MARK_SIZE, MARK_SIZE);
        g.setColor(Color.WHITE);
        OutlinedText.drawCentered(g, "!", new Rectangle(x, y, MARK_SIZE, MARK_SIZE), Fonts.ti(80));
    }
}
