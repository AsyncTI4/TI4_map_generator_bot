package ti4.image.draw;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.annotation.Nullable;

public final class ShapeDrawing {

    private ShapeDrawing() {}

    public static void redX(Graphics2D g2, int x, int y, int size, boolean thick) {
        int offset = size / 2;
        int strokeWidth = thick ? 6 : 4;
        RenderingHints originalHints = g2.getRenderingHints();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        drawCross(g2, x, y, offset, strokeWidth, Color.black);
        drawCross(g2, x, y, offset, strokeWidth - 2, Color.red);
        g2.setRenderingHints(originalHints);
    }

    public static void gradientOutline(
            Graphics2D g2, @Nullable Color main, @Nullable Color accent, int x, int y, int width, int height) {
        gradientOutline(g2, main, accent, new Rectangle(x, y, width, height));
    }

    public static void gradientOutline(Graphics2D g2, @Nullable Color main, @Nullable Color accent, Rectangle rect) {
        Paint original = g2.getPaint();
        g2.setPaint(ColorUtil.gradient(main, accent, rect));
        g2.draw(rect);
        g2.setPaint(original);
    }

    private static void drawCross(Graphics2D g2, int x, int y, int offset, int strokeWidth, Color color) {
        g2.setStroke(new BasicStroke(strokeWidth));
        g2.setColor(color);
        g2.drawLine(x - offset, y - offset, x + offset, y + offset);
        g2.drawLine(x - offset, y + offset, x + offset, y - offset);
    }
}
