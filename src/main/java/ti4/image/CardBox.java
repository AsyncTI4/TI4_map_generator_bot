package ti4.image;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
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
    private static final float SMALLEST_BODY_FONT_SIZE = 16f;
    private static final float BODY_FONT_SHRINK_STEP = 2f;

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
        Font bodyFont = g2.getFont();
        TextWrap.Lines lines = linesThatFit(g2, bodyFont, body, breakAtLastSpace);
        g2.drawString(lines.first(), x + TEXT_LEFT, y + BODY_FIRST_LINE);
        if (lines.second() != null) {
            g2.drawString(lines.second(), x + TEXT_LEFT, y + BODY_SECOND_LINE);
        }
        g2.setFont(bodyFont);
    }

    private static TextWrap.Lines linesThatFit(Graphics2D g2, Font bodyFont, String body, boolean breakAtLastSpace) {
        TextWrap.Lines lines = TextWrap.wrapToWidth(body, g2.getFontMetrics(), MAX_BODY_WIDTH, breakAtLastSpace);
        float size = bodyFont.getSize2D();
        while (!fits(lines, g2.getFontMetrics()) && size - BODY_FONT_SHRINK_STEP >= SMALLEST_BODY_FONT_SIZE) {
            size -= BODY_FONT_SHRINK_STEP;
            g2.setFont(bodyFont.deriveFont(size));
            lines = TextWrap.wrapToWidth(body, g2.getFontMetrics(), MAX_BODY_WIDTH, breakAtLastSpace);
        }
        return fits(lines, g2.getFontMetrics()) ? lines : withEllipsis(lines, g2.getFontMetrics());
    }

    private static boolean fits(TextWrap.Lines lines, FontMetrics metrics) {
        return lines.second() == null || metrics.stringWidth(lines.second()) <= MAX_BODY_WIDTH;
    }

    private static TextWrap.Lines withEllipsis(TextWrap.Lines lines, FontMetrics metrics) {
        return new TextWrap.Lines(lines.first(), TextWrap.ellipsized(lines.second(), metrics, MAX_BODY_WIDTH));
    }
}
