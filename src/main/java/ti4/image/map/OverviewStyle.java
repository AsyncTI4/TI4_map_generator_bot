package ti4.image.map;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;
import ti4.image.io.Fonts;

final class OverviewStyle {

    static final int TILE_PADDING = 100;
    static final int TILE_IMAGE_SIZE = 600;
    private static final int HEADER_X = 10;
    private static final int HEADER_BASELINE = 34;
    private static final List<Color> PALETTE = List.of(
            new Color(0, 170, 255),
            new Color(255, 140, 0),
            new Color(60, 200, 90),
            new Color(230, 60, 160),
            new Color(250, 220, 40),
            new Color(160, 90, 255),
            new Color(0, 210, 200),
            new Color(240, 70, 60));

    private OverviewStyle() {}

    static Color color(int index) {
        return PALETTE.get(index % PALETTE.size());
    }

    static Color translucent(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    static Graphics2D smoothGraphics(BufferedImage image) {
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        return graphics;
    }

    static void drawHeader(Graphics2D graphics, String header) {
        graphics.setFont(Fonts.ti(32));
        graphics.setColor(Color.WHITE);
        graphics.drawString(header, HEADER_X, HEADER_BASELINE);
    }
}
