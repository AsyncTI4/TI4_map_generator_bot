package ti4.image;

import ti4.service.map.CustomHyperlaneService;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.QuadCurve2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HyperlaneTileGenerator {

    private static final int CENTER_X = TileGenerator.TILE_WIDTH / 2;
    private static final int CENTER_Y = TileGenerator.TILE_HEIGHT / 2;

    public enum HLColor {
        BLUE(new Color(0, 180, 255), new Color(180, 200, 240)),
        GREEN(new Color(0, 220, 120), new Color(170, 220, 180)),
        RED(new Color(255, 80, 80), new Color(240, 170, 170)),
        YELLOW(new Color(255, 200, 40), new Color(240, 220, 170));

        final Color glow;
        final Color core;

        HLColor(Color glow, Color core) {
            this.glow = glow;
            this.core = core;
        }
    }

    //Line format
    private enum HLStroke {
        GLOW(20, AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f)),
        GAP(10, AlphaComposite.getInstance(AlphaComposite.CLEAR, 0.0f)),
        CORE(4, AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));

        final float width;
        final AlphaComposite composite;

        HLStroke(float width, AlphaComposite composite) {
            this.width = width;
            this.composite = composite;
        }
    }

    //Connection mappings
    private static final Map<List<Integer>, Integer> STRAIGHT_CONNECTIONS = Map.of(
        List.of(0, 3), 0,
        List.of(1, 4), 60,
        List.of(2, 5), 120
    );

    private static final Map<List<Integer>, Integer> SMALL_CURVE_CONNECTIONS = Map.of(
        List.of(0, 1), 0,
        List.of(1, 2), 60,
        List.of(2, 3), 120,
        List.of(3, 4), 180,
        List.of(4, 5), 240,
        List.of(0, 5), 300
    );

    private static final Map<List<Integer>, Integer> LARGE_CURVE_CONNECTIONS = Map.of(
        List.of(0, 2), 0,
        List.of(1, 3), 60,
        List.of(2, 4), 120,
        List.of(3, 5), 180,
        List.of(0, 4), 240,
        List.of(1, 5), 300
    );

    private static final Map<List<Integer>, Integer> ROUNDABOUT_CONNECTIONS = Map.of(
        List.of(0, 0), 0,
        List.of(1, 1), 60,
        List.of(2, 2), 120,
        List.of(3, 3), 180,
        List.of(4, 4), 240,
        List.of(5, 5), 300
    );

    //Shape templates
    private static final Shape STRAIGHT_LINE_TEMPLATE = new Line2D.Float(
        172, 0,
        172, 300
    );
 
    private static final Shape SMALL_CURVE_TEMPLATE = new QuadCurve2D.Float(
        172, 0,
        203.5f, 93.75f, 
        298, 75
    );

    private static final Shape LARGE_CURVE_TEMPLATE = new QuadCurve2D.Float(
        172, 0,
        166.5f, 153.5f, 
        298, 224
    );

    private static final Shape ROUNDABOUT = new Ellipse2D.Float(
          112, 90, 
          120, 120
    );

    private static final Shape ROUNDABOUT_CONNECTOR_TEMPLATE = new Line2D.Float(
        172, 0,
        172, 90
    );

    //Map connections to templates
    private static final List<ConnectionRule> CONNECTION_RULES = List.of(
        new ConnectionRule(STRAIGHT_CONNECTIONS, STRAIGHT_LINE_TEMPLATE),
        new ConnectionRule(SMALL_CURVE_CONNECTIONS, SMALL_CURVE_TEMPLATE),
        new ConnectionRule(LARGE_CURVE_CONNECTIONS, LARGE_CURVE_TEMPLATE),
        new ConnectionRule(ROUNDABOUT_CONNECTIONS, ROUNDABOUT_CONNECTOR_TEMPLATE)
    );

    /*
     * Connection matrix format: 0,0,0,1,0,0;0,0,0,0,0,0;0,0,0,0,0,0;1,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0 
     * Generates the hyperlane as roundabout if any connections connect to itself
     */
    public static BufferedImage generateHyperlaneTile(String matrix) {
        BufferedImage hyperlane = new BufferedImage(TileGenerator.TILE_WIDTH, TileGenerator.TILE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = hyperlane.createGraphics();
    
        Set<Shape> shapes = new LinkedHashSet<>();
    
        boolean asRoundabout = CustomHyperlaneService.hasSelfConnection(matrix);
        if (asRoundabout) {
            shapes.add(ROUNDABOUT);
        }

        for (List<Integer> connection : getConnectionsFromMatrix(matrix, asRoundabout)) {
            for (ConnectionRule rule : CONNECTION_RULES) {
                if (rule.matches(connection)) {
                    shapes.add(rule.getShape(connection));
                    break;
                }
            }
        }

        drawShapes(g, shapes, HLStroke.GLOW, HLColor.BLUE.glow);
        drawShapes(g, shapes, HLStroke.GAP, null);
        drawShapes(g, shapes, HLStroke.CORE, HLColor.BLUE.core);

        g.dispose();
        return hyperlane;
    }

    private static void drawShapes(Graphics2D g, Set<Shape> shapes, HLStroke hlStroke, Color color) {
        g.setComposite(hlStroke.composite);
        g.setStroke(new BasicStroke(hlStroke.width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        g.setColor(color);
        for (Shape shape : shapes) g.draw(shape);
    }
    
    private static Set<List<Integer>> getConnectionsFromMatrix(String matrix, boolean selfConnections) {
        Set<List<Integer>> pairs = new HashSet<>();
        String[] rows = matrix.split(";");
        for (int i = 0; i < 6; i++) {
            String[] cols = rows[i].split(",");
            for (int j = 0; j < 6; j++) {
                if (cols[j].trim().equals("1") && (!selfConnections || i == j)) {
                    pairs.add(List.of(Math.min(i, j), Math.max(i, j)));
                }
            }
        }
        return pairs;
    }

    //Connection rules to angles with shape cache
    public static class ConnectionRule {
        private final Map<List<Integer>, Shape> rotatedCache = new HashMap<>();      
        private final Map<List<Integer>, Integer> angleMap;
        private final Shape template;

        public ConnectionRule(Map<List<Integer>, Integer> angleMap, Shape template) {
            this.angleMap = angleMap;
            this.template = template;
        }

        public boolean matches(List<Integer> connection) {
            return angleMap.containsKey(connection);
        }
    
        public Shape getShape(List<Integer> connection) {
            return rotatedCache.computeIfAbsent(connection, conn -> {
                double angleRad = Math.toRadians(angleMap.get(conn));
                AffineTransform transform = AffineTransform.getRotateInstance(angleRad, CENTER_X, CENTER_Y);
                return transform.createTransformedShape(template);
            });
        }
    }
}