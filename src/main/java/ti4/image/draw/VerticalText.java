package ti4.image.draw;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.substringBefore;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;

public final class VerticalText {

    private static final double NEGATIVE_NINETY_DEGREES_RADIANS = -1.570_796_326_794_896_6;
    private static final int MAX_WRAPPED_COLUMNS = 9;

    private VerticalText() {}

    public static void draw(Graphics2D g2, String text, int x, int y, Font font) {
        draw(g2, text, x, y, font, false);
    }

    public static void draw(Graphics2D g2, String text, int x, int y, Font font, boolean rightAlign) {
        AffineTransform originalTransform = g2.getTransform();
        g2.rotate(NEGATIVE_NINETY_DEGREES_RADIANS);
        g2.setFont(font);
        if (rightAlign) {
            y += g2.getFontMetrics().stringWidth(text);
        }
        int baseline = x + g2.getFontMetrics().getHeight() / 2;

        Color originalColor = g2.getColor();
        g2.setColor(Color.BLACK);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                g2.drawString(text, -(y + dy), baseline + dx);
            }
        }
        g2.setColor(originalColor);
        g2.drawString(text, -y, baseline);
        g2.setTransform(originalTransform);
    }

    public static void drawTwoLines(Graphics2D g2, String text, int x, int y, int maxWidth) {
        drawTwoLines(g2, text, x, y, maxWidth, false);
    }

    public static void drawOneOrTwoLines(Graphics2D g2, String text, int x, int y, int maxWidth) {
        drawOneOrTwoLines(g2, text, x, y, maxWidth, false);
    }

    public static void drawOneOrTwoLines(Graphics2D g2, String text, int x, int y, int maxWidth, boolean rightAlign) {
        if (text.contains("\n")) {
            drawTwoLines(g2, text, x, y, maxWidth, rightAlign);
            return;
        }
        String upper = text.toUpperCase();
        FontMetrics metrics = g2.getFontMetrics();
        if (upper.equals(TextWrap.trimToWidth(upper, metrics, maxWidth))) {
            draw(g2, upper, x + lineSpacing(metrics) / 2, y, g2.getFont(), rightAlign);
            return;
        }
        drawTwoLines(g2, breakNearestTheMiddle(upper), x, y, maxWidth, rightAlign);
    }

    public static void drawWrappedColumns(Graphics2D g2, String text, int x, int y, int maxWidth) {
        FontMetrics metrics = g2.getFontMetrics();
        int spacing = lineSpacing(metrics);
        String remaining = text.toUpperCase();
        for (int column = 0; column < MAX_WRAPPED_COLUMNS; column++) {
            String fitting = TextWrap.trimToWidth(remaining, metrics, maxWidth);
            if (remaining.equals(fitting)) {
                draw(g2, remaining, x + spacing / 2, y, g2.getFont());
                return;
            }
            int space = fitting.lastIndexOf(' ');
            if (space == -1) {
                draw(g2, fitting, x + spacing / 2, y, g2.getFont());
                remaining = remaining.substring(fitting.length());
            } else {
                draw(g2, remaining.substring(0, space), x + spacing / 2, y, g2.getFont());
                remaining = remaining.substring(space + 1);
            }
            x += spacing;
        }
    }

    private static void drawTwoLines(Graphics2D g2, String text, int x, int y, int maxWidth, boolean rightAlign) {
        FontMetrics metrics = g2.getFontMetrics();
        String upper = text.toUpperCase();
        String firstRow = TextWrap.trimToWidth(substringBefore(upper, "\n"), metrics, maxWidth);
        String secondRow =
                TextWrap.trimToWidth(upper.substring(firstRow.length()).replace("\n", ""), metrics, maxWidth);
        draw(g2, firstRow, x, y, g2.getFont(), rightAlign);
        if (isNotBlank(secondRow)) {
            draw(g2, secondRow, x + lineSpacing(metrics), y, g2.getFont(), rightAlign);
        }
    }

    private static String breakNearestTheMiddle(String text) {
        if (!text.contains(" ")) return text;
        float center = text.length() / 2.0f + 0.5f;
        int before = text.substring(0, (int) center).lastIndexOf(' ');
        int after = text.indexOf(' ', (int) (center - 0.5f));
        boolean breakBefore = before != -1 && (after == -1 || center - before - 1 <= after - center + 1);
        int at = breakBefore ? before : after;
        return text.substring(0, at) + "\n" + text.substring(at + 1);
    }

    private static int lineSpacing(FontMetrics metrics) {
        return metrics.getAscent() + metrics.getLeading();
    }
}
