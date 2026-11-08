package ti4.image.tile;

import static ti4.image.tile.TileGeometry.TILE_HEIGHT;
import static ti4.image.tile.TileGeometry.TILE_WIDTH;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import ti4.model.HyperlaneStyle;

final class OneWayHyperlaneOverlay {

    private static final float CENTER_X = TILE_WIDTH / 2.0f;
    private static final float CENTER_Y = TILE_HEIGHT / 2.0f;
    private static final double EDGE_DISTANCE = TILE_HEIGHT / 2.0;
    private static final double FAN_DEGREES = 14;
    private static final double MARKER_DEPTH = 32;
    private static final double STRAIGHT_HANDLE = 100;
    private static final double ROUNDABOUT_RADIUS = 60;
    private static final float NODE_RADIUS = 20;
    private static final int NODE_CORE_ALPHA = 235;
    private static final int NODE_HALO_ALPHA = 170;
    private static final float HINT_RADIUS = 15;
    private static final int HINT_CORE_ALPHA = 130;
    private static final int HINT_HALO_ALPHA = 80;

    private static final Color BLUE_GLOW = new Color(0, 180, 255);
    private static final Color BLUE_CORE = new Color(180, 200, 240);
    private static final Color AMBER_GLOW = new Color(255, 200, 40);
    private static final Color AMBER_CORE = new Color(240, 220, 170);
    private static final Color ENTRY_GATE = new Color(225, 60, 55);
    private static final Color EXIT_GATE = new Color(70, 230, 110);
    private static final Color ARROW = new Color(70, 230, 110);
    private static final Color OUTLINE = new Color(5, 10, 25);

    private static final List<Point2D> EDGE_MIDPOINTS = edgeMidpoints();

    private OneWayHyperlaneOverlay() {}

    private record Lane(int from, int to, boolean oneWay, List<Point2D> points) {}

    static BufferedImage draw(int[][] matrix, HyperlaneStyle style) {
        BufferedImage img = new BufferedImage(TILE_WIDTH, TILE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setClip(hexOutline());
        if (isRoundabout(matrix)) {
            drawRoundabout(g, matrix, style);
        } else {
            drawLanes(g, matrix, style);
        }
        g.dispose();
        return img;
    }

    private static void drawLanes(Graphics2D g, int[][] matrix, HyperlaneStyle style) {
        List<int[]> pairs = connectedPairs(matrix);
        int[] lanesPerSide = new int[6];
        for (int[] pair : pairs) {
            lanesPerSide[pair[0]]++;
            lanesPerSide[pair[1]]++;
        }
        List<Lane> lanes = new ArrayList<>();
        for (int[] pair : pairs) {
            int from = pair[0], to = pair[1];
            boolean oneWay = matrix[from][to] != matrix[to][from];
            if (matrix[from][to] == 0) {
                from = pair[1];
                to = pair[0];
            }
            lanes.add(new Lane(from, to, oneWay, lanePoints(from, to, lanesPerSide)));
        }

        for (Lane lane : lanes) {
            if (lane.oneWay && style == HyperlaneStyle.FADING) {
                rampStroke(g, lane.points, 20, 20, 0.08f, 0.5f, AMBER_GLOW);
            } else {
                stroke(g, path(lane.points), 20, 0.4f, lane.oneWay ? AMBER_GLOW : BLUE_GLOW, null);
            }
        }
        for (Lane lane : lanes) clear(g, path(lane.points));
        for (Lane lane : lanes) drawCore(g, lane.points, lane.oneWay, style);

        g.setComposite(AlphaComposite.SrcOver);
        for (int side = 0; side < 6; side++) {
            if (lanesPerSide[side] >= 2) drawNode(g, EDGE_MIDPOINTS.get(side));
        }
        for (Lane lane : lanes) {
            if (!lane.oneWay) continue;
            if (style == HyperlaneStyle.GATES) {
                arrowHead(g, atDepthFromStart(lane.points, lane.from), ENTRY_GATE);
                arrowHead(g, atDepthFromEnd(lane.points, lane.to), EXIT_GATE);
            } else {
                arrowHead(g, atDepthFromEnd(lane.points, lane.to), ARROW);
            }
        }
    }

    private static void drawRoundabout(Graphics2D g, int[][] matrix, HyperlaneStyle style) {
        boolean[] entry = new boolean[6], exit = new boolean[6];
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 6; j++) {
                if (i == j || matrix[i][j] == 0) continue;
                entry[i] = true;
                exit[j] = true;
            }
        }
        Shape circle = new Ellipse2D.Double(
                CENTER_X - ROUNDABOUT_RADIUS,
                CENTER_Y - ROUNDABOUT_RADIUS,
                2 * ROUNDABOUT_RADIUS,
                2 * ROUNDABOUT_RADIUS);
        List<Integer> sides = new ArrayList<>();
        List<List<Point2D>> connectors = new ArrayList<>();
        for (int side = 0; side < 6; side++) {
            if (matrix[side][side] == 0) continue;
            List<Point2D> points = flatten(
                    new Line2D.Double(EDGE_MIDPOINTS.get(side), towardCenter(side, EDGE_DISTANCE - ROUNDABOUT_RADIUS)));
            if (exit[side] && !entry[side]) Collections.reverse(points);
            sides.add(side);
            connectors.add(points);
        }

        stroke(g, circle, 20, 0.4f, BLUE_GLOW, null);
        for (int k = 0; k < sides.size(); k++) {
            boolean oneWay = entry[sides.get(k)] != exit[sides.get(k)];
            if (oneWay && style == HyperlaneStyle.FADING) {
                rampStroke(g, connectors.get(k), 20, 20, 0.08f, 0.5f, AMBER_GLOW);
            } else {
                stroke(g, path(connectors.get(k)), 20, 0.4f, oneWay ? AMBER_GLOW : BLUE_GLOW, null);
            }
        }
        clear(g, circle);
        for (List<Point2D> connector : connectors) clear(g, path(connector));
        stroke(g, circle, 4, 0.8f, BLUE_CORE, null);
        for (int k = 0; k < sides.size(); k++) {
            drawCore(g, connectors.get(k), entry[sides.get(k)] != exit[sides.get(k)], style);
        }

        g.setComposite(AlphaComposite.SrcOver);
        for (int k = 0; k < sides.size(); k++) {
            int side = sides.get(k);
            if (entry[side] == exit[side]) continue;
            List<Point2D> connector = connectors.get(k);
            if (style == HyperlaneStyle.GATES) {
                List<Point2D> fromEdge = entry[side] ? connector : reversed(connector);
                double[] at = pointAt(fromEdge, 14);
                double angle = entry[side] ? at[2] : at[2] + Math.PI;
                arrowHead(g, new double[] {at[0], at[1], angle}, entry[side] ? ENTRY_GATE : EXIT_GATE);
            } else {
                arrowHead(g, pointAt(connector, length(connector) - 13), ARROW);
            }
        }
    }

    private static void drawCore(Graphics2D g, List<Point2D> points, boolean oneWay, HyperlaneStyle style) {
        if (!oneWay) {
            stroke(g, path(points), 4, 0.8f, BLUE_CORE, null);
            return;
        }
        switch (style) {
            case DASHED -> stroke(g, path(points), 4, 0.9f, AMBER_CORE, new float[] {9, 7});
            case FADING -> rampStroke(g, points, 1.5f, 5f, 0.2f, 1f, AMBER_CORE);
            default -> stroke(g, path(points), 4, 0.85f, AMBER_CORE, null);
        }
    }

    private static List<int[]> connectedPairs(int[][] matrix) {
        List<int[]> pairs = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            for (int j = i + 1; j < 6; j++) {
                if (matrix[i][j] == 1 || matrix[j][i] == 1) pairs.add(new int[] {i, j});
            }
        }
        return pairs;
    }

    private static boolean isRoundabout(int[][] matrix) {
        for (int i = 0; i < 6; i++) {
            if (matrix[i][i] == 1) return true;
        }
        return false;
    }

    private static List<Point2D> lanePoints(int from, int to, int[] lanesPerSide) {
        Point2D start = EDGE_MIDPOINTS.get(from), end = EDGE_MIDPOINTS.get(to);
        Point2D control = HyperlaneTileGenerator.curveControlPoint(from, to);
        Point2D handleStart = handle(start, control, from, to, lanesPerSide[from] >= 2);
        Point2D handleEnd = handle(end, control, to, from, lanesPerSide[to] >= 2);
        return flatten(new CubicCurve2D.Double(
                start.getX(),
                start.getY(),
                handleStart.getX(),
                handleStart.getY(),
                handleEnd.getX(),
                handleEnd.getY(),
                end.getX(),
                end.getY()));
    }

    private static Point2D handle(Point2D end, Point2D control, int side, int other, boolean fan) {
        double hx, hy;
        if (control == null) {
            Point2D inward = inward(side);
            hx = inward.getX() * STRAIGHT_HANDLE;
            hy = inward.getY() * STRAIGHT_HANDLE;
        } else {
            hx = (control.getX() - end.getX()) * 2 / 3;
            hy = (control.getY() - end.getY()) * 2 / 3;
        }
        if (fan) {
            double angle = -Math.toRadians((3 - Math.floorMod(other - side, 6)) * FAN_DEGREES);
            double cos = Math.cos(angle), sin = Math.sin(angle);
            double rotatedX = hx * cos - hy * sin;
            hy = hx * sin + hy * cos;
            hx = rotatedX;
        }
        return new Point2D.Double(end.getX() + hx, end.getY() + hy);
    }

    private static List<Point2D> edgeMidpoints() {
        List<Point2D> midpoints = new ArrayList<>();
        for (int side = 0; side < 6; side++) {
            double angle = Math.toRadians(side * 60);
            midpoints.add(new Point2D.Double(
                    CENTER_X + EDGE_DISTANCE * Math.sin(angle), CENTER_Y - EDGE_DISTANCE * Math.cos(angle)));
        }
        return List.copyOf(midpoints);
    }

    private static Point2D inward(int side) {
        Point2D edge = EDGE_MIDPOINTS.get(side);
        return new Point2D.Double((CENTER_X - edge.getX()) / EDGE_DISTANCE, (CENTER_Y - edge.getY()) / EDGE_DISTANCE);
    }

    private static Point2D towardCenter(int side, double distance) {
        Point2D edge = EDGE_MIDPOINTS.get(side), inward = inward(side);
        return new Point2D.Double(edge.getX() + inward.getX() * distance, edge.getY() + inward.getY() * distance);
    }

    private static Shape hexOutline() {
        Path2D hex = new Path2D.Double();
        hex.moveTo(TILE_WIDTH / 4.0, 0);
        hex.lineTo(TILE_WIDTH * 3 / 4.0, 0);
        hex.lineTo(TILE_WIDTH, CENTER_Y);
        hex.lineTo(TILE_WIDTH * 3 / 4.0, TILE_HEIGHT);
        hex.lineTo(TILE_WIDTH / 4.0, TILE_HEIGHT);
        hex.lineTo(0, CENTER_Y);
        hex.closePath();
        return hex;
    }

    private static List<Point2D> flatten(Shape shape) {
        List<Point2D> corners = new ArrayList<>();
        PathIterator iterator = shape.getPathIterator(null, 0.5);
        double[] coords = new double[6];
        while (!iterator.isDone()) {
            iterator.currentSegment(coords);
            corners.add(new Point2D.Double(coords[0], coords[1]));
            iterator.next();
        }
        List<Point2D> points = new ArrayList<>();
        for (int i = 1; i < corners.size(); i++) {
            Point2D a = corners.get(i - 1), b = corners.get(i);
            int steps = Math.max(1, (int) (a.distance(b) / 3));
            for (int k = 0; k < steps; k++) {
                double t = (double) k / steps;
                points.add(
                        new Point2D.Double(a.getX() + (b.getX() - a.getX()) * t, a.getY() + (b.getY() - a.getY()) * t));
            }
        }
        points.add(corners.getLast());
        return points;
    }

    private static List<Point2D> reversed(List<Point2D> points) {
        List<Point2D> copy = new ArrayList<>(points);
        Collections.reverse(copy);
        return copy;
    }

    private static Path2D path(List<Point2D> points) {
        Path2D path = new Path2D.Double();
        path.moveTo(points.getFirst().getX(), points.getFirst().getY());
        for (int i = 1; i < points.size(); i++)
            path.lineTo(points.get(i).getX(), points.get(i).getY());
        return path;
    }

    private static double length(List<Point2D> points) {
        double total = 0;
        for (int i = 1; i < points.size(); i++) total += points.get(i).distance(points.get(i - 1));
        return total;
    }

    private static double[] pointAt(List<Point2D> points, double distance) {
        double run = 0;
        for (int i = 1; i < points.size(); i++) {
            Point2D a = points.get(i - 1), b = points.get(i);
            double segment = b.distance(a);
            if (segment > 0 && run + segment >= distance) {
                double t = (distance - run) / segment;
                return new double[] {
                    a.getX() + (b.getX() - a.getX()) * t,
                    a.getY() + (b.getY() - a.getY()) * t,
                    Math.atan2(b.getY() - a.getY(), b.getX() - a.getX())
                };
            }
            run += segment;
        }
        Point2D a = points.get(points.size() - 2), b = points.getLast();
        return new double[] {b.getX(), b.getY(), Math.atan2(b.getY() - a.getY(), b.getX() - a.getX())};
    }

    private static double[] atDepthFromEnd(List<Point2D> points, int side) {
        Point2D edge = EDGE_MIDPOINTS.get(side), inward = inward(side);
        for (int i = points.size() - 1; i > 0; i--) {
            Point2D p = points.get(i);
            double depth = (p.getX() - edge.getX()) * inward.getX() + (p.getY() - edge.getY()) * inward.getY();
            if (depth >= MARKER_DEPTH) {
                Point2D next = points.get(Math.min(i + 1, points.size() - 1));
                return new double[] {p.getX(), p.getY(), Math.atan2(next.getY() - p.getY(), next.getX() - p.getX())};
            }
        }
        return pointAt(points, length(points) - 13);
    }

    private static double[] atDepthFromStart(List<Point2D> points, int side) {
        Point2D edge = EDGE_MIDPOINTS.get(side), inward = inward(side);
        for (int i = 0; i < points.size() - 1; i++) {
            Point2D p = points.get(i);
            double depth = (p.getX() - edge.getX()) * inward.getX() + (p.getY() - edge.getY()) * inward.getY();
            if (depth >= MARKER_DEPTH) {
                Point2D next = points.get(i + 1);
                return new double[] {p.getX(), p.getY(), Math.atan2(next.getY() - p.getY(), next.getX() - p.getX())};
            }
        }
        return pointAt(points, 13);
    }

    private static void stroke(Graphics2D g, Shape shape, float width, float alpha, Color color, float[] dash) {
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g.setStroke(
                dash == null
                        ? new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND)
                        : new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f, dash, 0));
        g.setColor(color);
        g.draw(shape);
    }

    private static void rampStroke(
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

    private static void clear(Graphics2D g, Shape shape) {
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.CLEAR, 0f));
        g.setStroke(new BasicStroke(10, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        g.draw(shape);
    }

    private static void drawNode(Graphics2D g, Point2D at) {
        drawNode(g, at, NODE_RADIUS, NODE_CORE_ALPHA, NODE_HALO_ALPHA);
    }

    static void drawEntryHint(Graphics2D target, int side) {
        Graphics2D g = (Graphics2D) target.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.clip(hexOutline());
        drawNode(g, EDGE_MIDPOINTS.get(side), HINT_RADIUS, HINT_CORE_ALPHA, HINT_HALO_ALPHA);
        g.dispose();
    }

    private static void drawNode(Graphics2D g, Point2D at, float radius, int coreAlpha, int haloAlpha) {
        g.setPaint(new RadialGradientPaint(at, radius, new float[] {0f, 0.35f, 1f}, new Color[] {
            new Color(255, 255, 255, coreAlpha), new Color(150, 220, 255, haloAlpha), new Color(0, 160, 255, 0)
        }));
        g.fill(new Ellipse2D.Double(at.getX() - radius, at.getY() - radius, 2 * radius, 2 * radius));
    }

    private static void arrowHead(Graphics2D g, double[] at, Color color) {
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
}
