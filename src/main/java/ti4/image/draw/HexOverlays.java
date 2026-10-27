package ti4.image.draw;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.util.List;
import ti4.model.ColorModel;

public final class HexOverlays {

    private static final int BORDER_CANVAS = 400;
    private static final int TINT_WIDTH = 345;
    private static final int TINT_HEIGHT = 299;
    private static final float SPARSE_DASH = 11.0f;
    private static final float GAP = 1000.0f;
    private static final List<Point> CORNERS = List.of(
            new Point(88, 2),
            new Point(256, 2),
            new Point(342, 149),
            new Point(256, 296),
            new Point(88, 296),
            new Point(2, 149));

    private HexOverlays() {}

    public static BufferedImage border(String style, ColorModel color, List<Integer> openSides) {
        return border(style, color, openSides, 1.0f);
    }

    public static BufferedImage border(String style, ColorModel color, List<Integer> openSides, float widthScale) {
        boolean solid = "solid".equals(style);
        float inline = 3.0f * widthScale;
        float outline = inline + 3.0f;
        float[] dash = {solid ? 85.0f : 30.0f, solid ? GAP : 17.0f, 30.0f, GAP};
        float[] sparse = {SPARSE_DASH, GAP};

        BufferedImage image = new BufferedImage(BORDER_CANVAS, BORDER_CANVAS, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        g2.setColor(Color.BLACK);
        for (int side = 0; side < 6; side++) {
            g2.setStroke(dashed(outline, openSides.contains(side) ? sparse : dash));
            Point from = CORNERS.get(side);
            Point to = CORNERS.get((side + 1) % 6);
            g2.drawLine(from.x, from.y, to.x, to.y);
            g2.drawLine(to.x, to.y, from.x, from.y);
        }
        SideColors colors = SideColors.of(color);
        for (int side = 0; side < 6; side++) {
            g2.setStroke(dashed(inline, openSides.contains(side) ? sparse : dash));
            Point from = CORNERS.get(side);
            Point to = CORNERS.get((side + 1) % 6);
            g2.setPaint(colors.forward(side, from, to));
            g2.drawLine(from.x, from.y, to.x, to.y);
            g2.setPaint(colors.backward(side, from, to));
            g2.drawLine(to.x, to.y, from.x, from.y);
        }
        return image;
    }

    public static BufferedImage tint(Color color, float alpha) {
        BufferedImage image = new BufferedImage(TINT_WIDTH, TINT_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Polygon hex = new Polygon();
        for (int i = 0; i < 6; i++) {
            int theta = i * 60;
            int x = Math.clamp(Math.round(172.0 * Math.cos(Math.toRadians(theta)) + 172.5), 0, TINT_WIDTH);
            int y = Math.clamp(Math.round(172.0 * Math.sin(Math.toRadians(theta)) + 149.5), 0, TINT_HEIGHT);
            hex.addPoint(x, y);
        }
        Graphics2D g2 = image.createGraphics();
        g2.setColor(color);
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g2.fillPolygon(hex);
        return image;
    }

    private static Stroke dashed(float width, float[] pattern) {
        return new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1.0f, pattern, 0.0f);
    }

    private record SideColors(Color primary, Color secondary, boolean rainbow) {

        static SideColors of(ColorModel color) {
            Color primary = color.getPrimaryColor();
            Color secondary = color.getSecondaryColor() == null ? primary : color.getSecondaryColor();
            if ("black".equals(color.getName())) {
                primary = Color.darkGray;
                secondary = Color.darkGray;
            }
            return new SideColors(primary, secondary, color.getName().endsWith("rainbow"));
        }

        GradientPaint forward(int side, Point from, Point to) {
            if (rainbow) {
                Point mid = midpoint(from, to);
                return side % 2 == 0
                        ? new GradientPaint(from, Color.red, mid, Color.yellow)
                        : new GradientPaint(from, Color.green, mid, Color.blue);
            }
            return side % 2 == 0
                    ? new GradientPaint(from, primary, to, secondary)
                    : new GradientPaint(from, secondary, to, primary);
        }

        GradientPaint backward(int side, Point from, Point to) {
            if (!rainbow) return null;
            Point mid = midpoint(from, to);
            return side % 2 == 0
                    ? new GradientPaint(mid, Color.yellow, to, Color.green)
                    : new GradientPaint(mid, Color.blue, to, Color.red);
        }

        private static Point midpoint(Point from, Point to) {
            return new Point((from.x + to.x) / 2, (from.y + to.y) / 2);
        }
    }
}
