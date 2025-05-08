package ti4.image;

import ti4.map.Game;
import ti4.service.map.CustomHyperlaneService;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.QuadCurve2D;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class HyperlaneTileGenerator {
    private static final int TILE_WIDTH = 345;
    private static final int TILE_HEIGHT = 299;
    private static final int CENTER_X = TILE_WIDTH / 2;
    private static final int CENTER_Y = TILE_HEIGHT / 2;

    public enum HLColor {
        BLUE(new Color(0, 180, 255), new Color(180, 200, 240)),
        GREEN(new Color(0, 220, 120), new Color(170, 220, 180)),
        RED(new Color(255, 80, 80), new Color(240, 170, 170)),
        YELLOW(new Color(255, 200, 40), new Color(240, 220, 170));

        private final Color glow;
        private final Color core;

        HLColor(Color glow, Color core) {
            this.glow = glow;
            this.core = core;
        }
    }

    private static final int GLOW_WIDTH = 20;
    private static final int GAP_WIDTH = 10;
    private static final int CORE_WIDTH = 4;
    private static final int ROUNDABOUT_RADIUS = 60;

    private static final String[] DIRECTION_KEYS = {"n", "ne", "se", "s", "sw", "nw"};

    // Positions for direction anchors
    private static final Map<String, Point> DIRECTION_POINTS = Map.of(
        "n",  new Point(172, 0),
        "ne", new Point(298, 75),
        "se", new Point(298, 224),
        "s",  new Point(172, 299),
        "sw", new Point(47, 224),
        "nw", new Point(47, 75)
    );

    private static final Set<Set<String>> STRAIGHT_CONNECTIONS = Set.of(
        Set.of("n", "s"),
        Set.of("ne", "sw"),
        Set.of("nw", "se")
    );

    /*
     * Supports the current hyperlane format but internally uses DIRECTIONS for readability
     *  0,0,0,1,0,0;0,0,0,0,0,0;0,0,0,0,0,0;1,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0 
     * 
     * Generates the hyperlane as roundabout if any connections connect to itself
     */
    public static BufferedImage generateHyperlaneTile(Game game, String connections) {
        boolean asRoundabout = CustomHyperlaneService.hasSelfConnection(connections);
        BufferedImage hyperlane = new BufferedImage(TILE_WIDTH, TILE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
    
        Graphics2D g = hyperlane.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    
        // Collect all shapes
        Set<Shape> shapes = new HashSet<>();
    
        if (asRoundabout) {
            shapes.add(new Ellipse2D.Float(CENTER_X - ROUNDABOUT_RADIUS, CENTER_Y - ROUNDABOUT_RADIUS, ROUNDABOUT_RADIUS * 2, ROUNDABOUT_RADIUS * 2));
            Set<String> directions = extractRoundaboutDirsFromMatrix(connections);
            shapes.addAll(buildRoundaboutConnectionShapes(directions));
        } else {
            for (String[] pair : parseDirectionalPairsFromMatrix(connections)) {
                Point from = DIRECTION_POINTS.get(pair[0]);
                Point to = DIRECTION_POINTS.get(pair[1]);
                if (from == null || to == null) continue;
    
                Shape shape = isStraight(from, to)
                    ? new Line2D.Float(from, to)
                    : buildCurvedShape(from, to);
    
                shapes.add(shape);
            }
        }
    
        // Pass 1: Glow
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
        g.setColor(HLColor.BLUE.glow);
        g.setStroke(new BasicStroke(GLOW_WIDTH, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        for (Shape shape : shapes) g.draw(shape);
    
        // Pass 2: Transparent gap
        g.setComposite(AlphaComposite.Clear);
        g.setStroke(new BasicStroke(GAP_WIDTH, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        for (Shape shape : shapes) g.draw(shape);
    
        // Pass 3: Core line
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.8f));
        g.setColor(HLColor.BLUE.core);
        g.setStroke(new BasicStroke(CORE_WIDTH, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        for (Shape shape : shapes) g.draw(shape);
    
        g.dispose();
        return hyperlane;
    }

    private static Set<Shape> buildRoundaboutConnectionShapes(Set<String> directions) {
        Set<Shape> result = new HashSet<>();
        Point center = new Point(CENTER_X, CENTER_Y);
        int stopGap = 30;
        int lineStopDist = ROUNDABOUT_RADIUS + stopGap;
        int rampOffsetAngle = 25;
    
        for (String dir : directions) {
            Point from = DIRECTION_POINTS.get(dir.toLowerCase());
            if (from == null) continue;
    
            double dx = center.x - from.x;
            double dy = center.y - from.y;
            double dist = Math.sqrt(dx * dx + dy * dy);
            double nx = dx / dist;
            double ny = dy / dist;
    
            Point lineEnd = new Point(
                (int) (center.x - nx * lineStopDist),
                (int) (center.y - ny * lineStopDist)
            );
    
            result.add(new Line2D.Float(from, lineEnd));
    
            double angleRad = Math.toRadians(rampOffsetAngle);
            double lx = nx * Math.cos(angleRad) - ny * Math.sin(angleRad);
            double ly = nx * Math.sin(angleRad) + ny * Math.cos(angleRad);
            double rx = nx * Math.cos(-angleRad) - ny * Math.sin(-angleRad);
            double ry = nx * Math.sin(-angleRad) + ny * Math.cos(-angleRad);
    
            Point leftEntry = new Point((int) (center.x - lx * ROUNDABOUT_RADIUS), (int) (center.y - ly * ROUNDABOUT_RADIUS));
            Point rightEntry = new Point((int) (center.x - rx * ROUNDABOUT_RADIUS), (int) (center.y - ry * ROUNDABOUT_RADIUS));
    
            result.add(buildCurvedShape(lineEnd, leftEntry));
            result.add(buildCurvedShape(lineEnd, rightEntry));
        }
    
        return result;
    }

    private static boolean isStraight(Point a, Point b) {
        // Look up direction names for points
        String dirA = getDirectionKey(a);
        String dirB = getDirectionKey(b);
        if (dirA == null || dirB == null) return false;
    
        Set<String> pair = Set.of(dirA, dirB);
        return STRAIGHT_CONNECTIONS.contains(pair);
    }

    private static String getDirectionKey(Point p) {
        for (Map.Entry<String, Point> entry : DIRECTION_POINTS.entrySet()) {
            if (entry.getValue().equals(p)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static Shape buildCurvedShape(Point from, Point to) {
        QuadCurve2D curve = new QuadCurve2D.Float();
    
        // Midpoint between from and to
        double mx = (from.x + to.x) / 2.0;
        double my = (from.y + to.y) / 2.0;
    
        // Vector from midpoint to center
        double cx = CENTER_X - mx;
        double cy = CENTER_Y - my;
    
        // Normalize that vector
        double len = Math.sqrt(cx * cx + cy * cy);
        if (len == 0) len = 1;
        cx /= len;
        cy /= len;
    
        // Curvature magnitude
        double curveAmount = from.distance(to) * 0.25; // tweak multiplier if needed
    
        // Final control point
        double ctrlX = mx + cx * curveAmount;
        double ctrlY = my + cy * curveAmount;
    
        curve.setCurve(from.x, from.y, ctrlX, ctrlY, to.x, to.y);
        return curve;
    }

    private static Set<String[]> parseDirectionalPairsFromMatrix(String matrix) {
        Set<String[]> pairs = new HashSet<>();
        String[] rows = matrix.split(";");
        if (rows.length != 6) return pairs;
    
        for (int i = 0; i < 6; i++) {
            String[] cols = rows[i].split(",");
            if (cols.length != 6) continue;
    
            for (int j = 0; j < 6; j++) {
                if (cols[j].trim().equals("1") && i != j) {
                    String from = DIRECTION_KEYS[i];
                    String to = DIRECTION_KEYS[j];
                    pairs.add(new String[]{from, to});
                }
            }
        }
        return pairs;
    }
    
    private static Set<String> extractRoundaboutDirsFromMatrix(String matrix) {
        Set<String> dirs = new HashSet<>();
        String[] rows = matrix.split(";");
        if (rows.length != 6) return dirs;
    
        for (int i = 0; i < 6; i++) {
            String[] cols = rows[i].split(",");
            if (cols.length != 6) continue;
    
            for (int j = 0; j < 6; j++) {
                if (cols[j].trim().equals("1")) {
                    dirs.add(DIRECTION_KEYS[i]);
                    break;
                }
            }
        }
        return dirs;
    }
}