package ti4.image;

import ti4.ResourceHelper;
import ti4.map.Game;
import ti4.map.Tile;
import ti4.service.map.CustomHyperlaneService;

import static ti4.image.TileGenerator.TILE_HEIGHT;
import static ti4.image.TileGenerator.TILE_WIDTH;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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
import java.util.Random;
import java.util.Set;

public class HyperlaneTileGenerator {

    private static final float CENTER_X = TILE_WIDTH / 2.0f;
    private static final float CENTER_Y = TILE_HEIGHT / 2.0f;

    private static final List<String> RANDOM_BACKGROUNDS = List.of(
      "hl_bg/hl_empty_0.png",
      "hl_bg/hl_empty_1.png",
      "hl_bg/hl_empty_2.png",
      "hl_bg/hl_empty_3.png",
      "hl_bg/hl_empty_4.png",
      "hl_bg/hl_empty_5.png",
      "hl_bg/hl_empty_6.png",
      "hl_bg/hl_empty_7.png",
      "hl_bg/hl_empty_8.png",
      "hl_bg/hl_empty_9.png",
      "hl_bg/hl_empty_10.png",
      "hl_bg/hl_empty_11.png",
      "hl_bg/hl_empty_12.png",
      "hl_bg/hl_empty_13.png",
      "hl_bg/hl_empty_14.png",
      "hl_bg/hl_empty_15.png",
      "hl_bg/hl_empty_16.png"
    );

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
        CORE(4, AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.8f));

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
        CENTER_X, 0,
        CENTER_X, TILE_HEIGHT
    );

    private static final Shape SMALL_CURVE_TEMPLATE = new QuadCurve2D.Float(
        CENTER_X, 0,
        197.25f, 106.78f,
        302, 75
    );

    private static final Shape LARGE_CURVE_TEMPLATE = new QuadCurve2D.Float(
        CENTER_X, 0,
        181.2f, 144.98f,
        302, 225
    );

    private static final Shape ROUNDABOUT = new Ellipse2D.Float(
          112.5f, 90, 
          120, 120
    );

    private static final Shape ROUNDABOUT_CONNECTOR_TEMPLATE = new Line2D.Float(
        CENTER_X, 0,
        CENTER_X, 90
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
    public static BufferedImage generateHyperlaneTile(Tile tile, Game game) {
        String matrix = game.getCustomHyperlaneData().get(tile.getPosition());
        boolean asRoundabout = CustomHyperlaneService.hasSelfConnection(matrix);

        BufferedImage hyperlane = getRandomTransformedBackground(tile, matrix);
        Graphics2D g = hyperlane.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        Set<Shape> shapes = new LinkedHashSet<>();
   
        //If no connection matrix, generate just the circle
        if (asRoundabout || matrix == null) {
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
    
    //If looking for selfConnections, return only those
    private static Set<List<Integer>> getConnectionsFromMatrix(String matrix, boolean selfConnections) {
        Set<List<Integer>> pairs = new HashSet<>();
        if (matrix == null) {
            return pairs;
        }

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

    //Randomize hyperlane tile background based on matrix, or use default if no matrix present
    private static BufferedImage getRandomTransformedBackground(Tile tile, String matrix) {
        String tilePath = tile.getTilePath();
        if (matrix != null) {
            String randomTile = RANDOM_BACKGROUNDS.get(new Random(matrix.hashCode()).nextInt(RANDOM_BACKGROUNDS.size()));
            tilePath = ResourceHelper.getInstance().getTileFile(randomTile);
            if (tilePath == null) {
                tilePath = tile.getTilePath();
            }
        }

        BufferedImage original = ImageHelper.read(tilePath);
        BufferedImage transformed = new BufferedImage(TILE_WIDTH, TILE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = transformed.createGraphics();
    
        int transform = matrix != null ? Math.abs(matrix.hashCode()) % 4 : -1;
        switch (transform) {
            case 1 -> g.drawImage(original, 0, 0, TILE_WIDTH, TILE_HEIGHT, TILE_WIDTH, 0, 0, TILE_HEIGHT, null); // Horizontal flip
            case 2 -> g.drawImage(original, 0, 0, TILE_WIDTH, TILE_HEIGHT, 0, TILE_HEIGHT, TILE_WIDTH, 0, null); // Vertical flip
            case 3 -> g.drawImage(original, 0, 0, TILE_WIDTH, TILE_HEIGHT, TILE_WIDTH, TILE_HEIGHT, 0, 0, null); // Both flips (180° rotate)
            default -> g.drawImage(original, 0, 0, null);  // No transformation
        }
    
        g.dispose();
        return transformed;
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