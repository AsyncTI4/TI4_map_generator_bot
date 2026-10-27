package ti4.image.tile;

import static ti4.image.tile.TileGeometry.TILE_PADDING;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.regex.Pattern;
import ti4.helpers.PatternHelper;
import ti4.image.draw.HorizontalAlign;
import ti4.image.draw.OutlinedText;
import ti4.image.draw.Strokes;
import ti4.image.draw.VerticalAlign;
import ti4.image.io.Fonts;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.model.TileHelper;

final class DraftTileLabel {

    private static final Pattern DIGIT_PATTERN = Pattern.compile("[0-9]");
    private static final Point NUMBER_POSITION = new Point(85, 140);
    private static final Point COLOR_POSITION = new Point(172, 228);
    private static final BasicStroke NUMBER_OUTLINE = Strokes.STROKE_8;
    private static final BasicStroke COLOR_OUTLINE = Strokes.STROKE_6;

    private DraftTileLabel() {}

    static void draw(TileLayer layer) {
        if (!TileHelper.isDraftTile(layer.tile().getTileModel())) {
            return;
        }
        String tileID = layer.tile().getTileID();
        Graphics2D g = layer.g();
        if (tileID.endsWith("blank")) {
            BufferedImage green = ImageHelper.readScaled(ImageResources.tile("00_green.png"), 73, 63);
            layer.drawOnHex(green, 49, 119);
        } else {
            g.setFont(Fonts.ti(50));
            OutlinedText.draw(
                    g,
                    draftNumber(tileID),
                    TILE_PADDING + NUMBER_POSITION.x,
                    TILE_PADDING + NUMBER_POSITION.y,
                    Color.WHITE,
                    HorizontalAlign.Center,
                    VerticalAlign.Center,
                    NUMBER_OUTLINE,
                    Color.BLACK);
        }
        g.setFont(Fonts.ti(24));
        OutlinedText.draw(
                g,
                draftColor(tileID),
                TILE_PADDING + COLOR_POSITION.x,
                TILE_PADDING + COLOR_POSITION.y,
                Color.WHITE,
                HorizontalAlign.Center,
                VerticalAlign.Bottom,
                COLOR_OUTLINE,
                Color.BLACK);
    }

    private static String draftNumber(String tileID) {
        return PatternHelper.LOWERCASE_LETTER_PATTERN.matcher(tileID).replaceAll("");
    }

    private static String draftColor(String tileID) {
        return PatternHelper.BLANK_WORD_PATTERN
                .matcher(DIGIT_PATTERN.matcher(tileID).replaceAll(""))
                .replaceAll("")
                .toUpperCase();
    }
}
