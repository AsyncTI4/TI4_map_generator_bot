package ti4.image.tile;

import static ti4.image.tile.TileGeometry.TILE_HEIGHT;
import static ti4.image.tile.TileGeometry.TILE_WIDTH;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

final class LaneInk {

    static final Color BLUE_GLOW = new Color(0, 180, 255);
    static final Color BLUE_CORE = new Color(180, 200, 240);
    static final Color AMBER_GLOW = new Color(255, 200, 40);
    static final Color AMBER_CORE = new Color(240, 220, 170);
    static final Color ENTRY_GATE = new Color(225, 60, 55);
    static final Color EXIT_GATE = new Color(70, 230, 110);
    static final Color ARROW = new Color(70, 230, 110);
    static final Color OUTLINE = new Color(5, 10, 25);

    private static final float NODE_RADIUS = 20;
    private static final int NODE_CORE_ALPHA = 235;
    private static final int NODE_HALO_ALPHA = 170;

    private LaneInk() {}

    static void stroke(Graphics2D g, Shape shape, float width, float alpha, Color color, float[] dash) {
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g.setStroke(
                dash == null
                        ? new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND)
                        : new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f, dash, 0));
        g.setColor(color);
        g.draw(shape);
    }

    static void rampStroke(
            Graphics2D g,
            List<Point2D> points,
            float startWidth,
            float endWidth,
            float startAlpha,
            float endAlpha,
            Color color) {
        g.setColor(color);
        int segments = points.size() - 1;
        for (int i = 0; i < segments; i++) {
            float t = (float) i / segments;
            g.setComposite(
                    AlphaComposite.getInstance(AlphaComposite.SRC_OVER, startAlpha + (endAlpha - startAlpha) * t));
            g.setStroke(new BasicStroke(
                    startWidth + (endWidth - startWidth) * t, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
            g.draw(new Line2D.Double(points.get(i), points.get(i + 1)));
        }
    }

    static void clear(Graphics2D g, Shape shape) {
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.CLEAR, 0f));
        g.setStroke(new BasicStroke(10, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        g.draw(shape);
    }

    static void drawNode(Graphics2D g, Point2D at) {
        drawNode(g, at, NODE_RADIUS, NODE_CORE_ALPHA, NODE_HALO_ALPHA);
    }

    static void drawNode(Graphics2D g, Point2D at, float radius, int coreAlpha, int haloAlpha) {
        g.setPaint(new RadialGradientPaint(at, radius, new float[] {0f, 0.35f, 1f}, new Color[] {
            new Color(255, 255, 255, coreAlpha), new Color(150, 220, 255, haloAlpha), new Color(0, 160, 255, 0)
        }));
        g.fill(new Ellipse2D.Double(at.getX() - radius, at.getY() - radius, 2 * radius, 2 * radius));
    }

    static void arrowHead(Graphics2D g, double[] at, Color color) {
        AffineTransform original = g.getTransform();
        g.translate(at[0], at[1]);
        g.rotate(at[2]);
        g.scale(0.7, 0.7);
        Path2D head = new Path2D.Double();
        head.moveTo(14, 0);
        head.lineTo(-10, -12);
        head.lineTo(-4, 0);
        head.lineTo(-10, 12);
        head.closePath();
        g.setColor(OUTLINE);
        g.setStroke(new BasicStroke(3.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(head);
        g.setColor(color);
        g.fill(head);
        g.setTransform(original);
    }

    static void gateBar(Graphics2D g, Point2D at, double edgeAngle, Color color) {
        AffineTransform original = g.getTransform();
        g.translate(at.getX(), at.getY());
        g.rotate(edgeAngle);
        RoundRectangle2D bar = new RoundRectangle2D.Double(-13, -3, 26, 6, 5, 5);
        g.setColor(OUTLINE);
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(bar);
        g.setColor(color);
        g.fill(bar);
        g.setTransform(original);
    }

    static Shape hexOutline() {
        Path2D hex = new Path2D.Double();
        hex.moveTo(TILE_WIDTH / 4.0, 0);
        hex.lineTo(TILE_WIDTH * 3 / 4.0, 0);
        hex.lineTo(TILE_WIDTH, TILE_HEIGHT / 2.0);
        hex.lineTo(TILE_WIDTH * 3 / 4.0, TILE_HEIGHT);
        hex.lineTo(TILE_WIDTH / 4.0, TILE_HEIGHT);
        hex.lineTo(0, TILE_HEIGHT / 2.0);
        hex.closePath();
        return hex;
    }

    static Path2D path(List<Point2D> points) {
        Path2D path = new Path2D.Double();
        path.moveTo(points.getFirst().getX(), points.getFirst().getY());
        for (int i = 1; i < points.size(); i++)
            path.lineTo(points.get(i).getX(), points.get(i).getY());
        return path;
    }
}
