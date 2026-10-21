package ti4.image;

import java.awt.Color;
import java.awt.Graphics2D;
import ti4.helpers.Storage;

final class CardBox {

    static final int WIDTH = 1178;
    static final int HEIGHT = 110;
    static final int COLUMN_STEP = WIDTH + 8;

    private static final int TEXT_LEFT = 95;
    private static final int BODY_FIRST_LINE = 70;
    private static final int BODY_SECOND_LINE = 96;
    private static final int MAX_BODY_WIDTH = 1076;

    private CardBox() {}

    static void drawFrame(Graphics2D g2, int x, int y, Color frameColor) {
        g2.setFont(Storage.getFont35());
        g2.setColor(frameColor);
        g2.drawRect(x, y, WIDTH, HEIGHT);
    }

    static void drawText(
            Graphics2D g2,
            int x,
            int y,
            Color titleColor,
            int titleBaseline,
            String title,
            String body,
            boolean breakAtLastSpace) {
        g2.setColor(titleColor);
        g2.drawString(title, x + TEXT_LEFT, y + titleBaseline);
        g2.setFont(Storage.getFont26());
        g2.setColor(Color.WHITE);
        drawBody(g2, x, y, body, breakAtLastSpace);
    }

    private static void drawBody(Graphics2D g2, int x, int y, String body, boolean breakAtLastSpace) {
        TextWrap.Lines lines = TextWrap.wrapToWidth(body, g2.getFontMetrics(), MAX_BODY_WIDTH, breakAtLastSpace);
        g2.drawString(lines.first(), x + TEXT_LEFT, y + BODY_FIRST_LINE);
        if (lines.second() != null) {
            g2.drawString(lines.second(), x + TEXT_LEFT, y + BODY_SECOND_LINE);
        }
    }
}
