package ti4.image.map;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import ti4.image.draw.TextWrap;
import ti4.image.io.Fonts;

final class CardBox {

    static final int WIDTH = 1178;
    static final int HEIGHT = 110;
    static final int COLUMN_STEP = WIDTH + 8;

    private static final int TEXT_LEFT = 95;
    private static final int BODY_FIRST_LINE = 70;
    private static final int BODY_LINE_STEP = 26;
    private static final int BODY_BOTTOM_PADDING = 14;
    private static final int MAX_BODY_WIDTH = 1076;

    private CardBox() {}

    static FontMetrics bodyMetrics(Graphics2D g2) {
        return g2.getFontMetrics(Fonts.ti(26));
    }

    static FontMetrics bodyMetrics() {
        Graphics2D scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics();
        try {
            return bodyMetrics(scratch);
        } finally {
            scratch.dispose();
        }
    }

    static List<String> bodyLines(FontMetrics metrics, String body, boolean breakAtLastSpace) {
        return TextWrap.wrapAll(body, metrics, MAX_BODY_WIDTH, breakAtLastSpace);
    }

    static int heightFor(List<String> bodyLines) {
        int lastBaseline = BODY_FIRST_LINE + (bodyLines.size() - 1) * BODY_LINE_STEP;
        return Math.max(HEIGHT, lastBaseline + BODY_BOTTOM_PADDING);
    }

    static void drawFrame(Graphics2D g2, int x, int y, int height, Color frameColor) {
        g2.setFont(Fonts.ti(35));
        g2.setColor(frameColor);
        g2.drawRect(x, y, WIDTH, height);
    }

    static void drawText(
            Graphics2D g2, int x, int y, Color titleColor, int titleBaseline, String title, List<String> bodyLines) {
        g2.setColor(titleColor);
        g2.drawString(title, x + TEXT_LEFT, y + titleBaseline);
        g2.setFont(Fonts.ti(26));
        g2.setColor(Color.WHITE);
        for (int i = 0; i < bodyLines.size(); i++) {
            g2.drawString(bodyLines.get(i), x + TEXT_LEFT, y + BODY_FIRST_LINE + i * BODY_LINE_STEP);
        }
    }
}
