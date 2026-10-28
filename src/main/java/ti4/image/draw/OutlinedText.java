package ti4.image.draw;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import javax.annotation.Nullable;

public final class OutlinedText {

    private static final BasicStroke DEFAULT_OUTLINE = Strokes.STROKE_2;

    private OutlinedText() {}

    public static void draw(
            Graphics2D g2,
            @Nullable String text,
            int x,
            int y,
            @Nullable Color fill,
            @Nullable HorizontalAlign horizontal,
            @Nullable VerticalAlign vertical,
            @Nullable Stroke outline,
            @Nullable Color outlineColor) {
        if (text == null) return;
        FontMetrics metrics = g2.getFontMetrics();
        int left = alignX(x, metrics.stringWidth(text), horizontal);
        int baseline = alignY(y, metrics.getAscent() - metrics.getDescent(), vertical);
        if (outlineColor == null && fill == null) {
            outlineColor = Color.BLACK;
            fill = Color.WHITE;
        }
        if (outlineColor == null) {
            g2.drawString(text, left, baseline);
            return;
        }
        drawOutlined(g2, text, left, baseline, outline == null ? DEFAULT_OUTLINE : outline, outlineColor, fill);
    }

    public static void drawCentered(
            Graphics2D g2,
            @Nullable String text,
            int x,
            int y,
            @Nullable Color fill,
            Stroke outline,
            Color outlineColor) {
        draw(g2, text, x, y, fill, HorizontalAlign.Center, VerticalAlign.Center, outline, outlineColor);
    }

    public static void drawCenteredWhiteOnBlack(Graphics2D g2, @Nullable String text, int x, int y) {
        drawCentered(g2, text, x, y, Color.white, DEFAULT_OUTLINE, Color.black);
    }

    public static void drawCentered(Graphics2D g2, @Nullable String text, Rectangle area, Font font) {
        g2.setFont(font);
        int x = (int) Math.round(area.getCenterX());
        int y = (int) Math.round(area.getCenterY());
        draw(g2, text, x, y, null, HorizontalAlign.Center, VerticalAlign.Center, null, null);
    }

    private static int alignX(int x, int width, @Nullable HorizontalAlign horizontal) {
        if (horizontal == null) return x;
        return switch (horizontal) {
            case Center -> x - (int) (width / 2.0);
            case Right -> x - width;
            case Left -> x;
        };
    }

    private static int alignY(int y, int height, @Nullable VerticalAlign vertical) {
        if (vertical == null) return y;
        return switch (vertical) {
            case Center -> y + height / 2;
            case Top -> y + height;
            case Bottom -> y;
        };
    }

    private static void drawOutlined(
            Graphics2D g2, String text, int x, int y, Stroke outline, Color outlineColor, @Nullable Color fill) {
        Color originalColor = g2.getColor();
        AffineTransform originalTransform = g2.getTransform();
        Stroke originalStroke = g2.getStroke();
        RenderingHints originalHints = g2.getRenderingHints();

        Shape glyphs =
                g2.getFont().createGlyphVector(g2.getFontRenderContext(), text).getOutline();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.translate(x, y);
        g2.setColor(outlineColor);
        g2.setStroke(outline);
        g2.draw(glyphs);
        g2.setColor(fill);
        g2.fill(glyphs);

        g2.setColor(originalColor);
        g2.setStroke(originalStroke);
        g2.setTransform(originalTransform);
        g2.setRenderingHints(originalHints);
    }
}
