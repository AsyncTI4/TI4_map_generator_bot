package ti4.image.tile;

import static ti4.image.tile.TileGeometry.TILE_HEIGHT;
import static ti4.image.tile.TileGeometry.TILE_PADDING;
import static ti4.image.tile.TileGeometry.TILE_WIDTH;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.util.List;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.image.draw.HorizontalAlign;
import ti4.image.draw.OutlinedText;
import ti4.image.draw.TextWrap;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;

final class FogOverlay {

    private static final Point ROUND_LABEL_POSITION = new Point(90, 295);

    private FogOverlay() {}

    static void draw(TileScene scene, TileLayer layer) {
        Tile tile = layer.tile();
        if (!scene.hidesFogged(tile)) {
            return;
        }
        Player fowPlayer = scene.fowPlayer();
        Graphics2D g = layer.g();
        g.drawImage(ImageHelper.read(ImageResources.fogTilePath(tile, fowPlayer)), TILE_PADDING, TILE_PADDING, null);
        String label = tile.getFogLabel(fowPlayer);
        if (label == null) {
            return;
        }
        if (label.startsWith("Rnd")) {
            drawRoundLabel(g, label);
        } else {
            drawCentredLabel(g, label);
        }
    }

    private static void drawRoundLabel(Graphics2D g, String label) {
        int labelX = TILE_PADDING + ROUND_LABEL_POSITION.x;
        int labelY = TILE_PADDING + ROUND_LABEL_POSITION.y;
        OutlinedText.draw(g, label, labelX, labelY, Color.WHITE, null, null, null, null);
    }

    private static void drawCentredLabel(Graphics2D g, String label) {
        int labelX = TILE_PADDING + (TILE_WIDTH / 2);
        int labelY = TILE_PADDING + (TILE_HEIGHT / 2);
        int lineHeight = g.getFontMetrics().getHeight();
        List<String> lines = TextWrap.layoutLines(label, g.getFontMetrics(), TILE_WIDTH - TILE_PADDING);
        int deltaY = 0;
        for (String line : lines) {
            OutlinedText.draw(g, line, labelX, labelY + deltaY, Color.WHITE, HorizontalAlign.Center, null, null, null);
            deltaY += lineHeight;
        }
    }
}
