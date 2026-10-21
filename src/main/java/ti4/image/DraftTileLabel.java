package ti4.image;

import static ti4.image.TileGeometry.TILE_PADDING;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.regex.Pattern;
import ti4.ResourceHelper;
import ti4.helpers.PatternHelper;
import ti4.helpers.Storage;

final class DraftTileLabel {

    private static final Pattern DIGIT_PATTERN = Pattern.compile("[0-9]");
    private static final Point NUMBER_POSITION = new Point(85, 140);
    private static final Point COLOR_POSITION = new Point(172, 228);
    private static final BasicStroke NUMBER_OUTLINE = new BasicStroke(8.0f);
    private static final BasicStroke COLOR_OUTLINE = new BasicStroke(6.0f);

    private DraftTileLabel() {}

    static void draw(TileLayer layer) {
        if (!TileHelper.isDraftTile(layer.tile().getTileModel())) {
            return;
        }
        String tileID = layer.tile().getTileID();
        Graphics2D g = layer.g();
        if (tileID.endsWith("blank")) {
            BufferedImage green = ImageHelper.readScaled(ResourceHelper.getTileFile("00_green.png"), 73, 63);
            layer.drawOnHex(green, 49, 119);
        } else {
            g.setFont(Storage.getFont50());
            DrawingUtil.superDrawString(
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
        g.setFont(Storage.getFont24());
        DrawingUtil.superDrawString(
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
