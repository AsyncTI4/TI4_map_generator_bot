package ti4.image;

import java.awt.*;
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

import ti4.ResourceHelper;
import ti4.map.Game;
import ti4.map.Tile;
import ti4.service.map.CustomHyperlaneService;

import static ti4.image.TileGenerator.TILE_HEIGHT;
import static ti4.image.TileGenerator.TILE_WIDTH;

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
            "hl_bg/hl_empty_16.png");

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

    // Line format
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

    // Connection mappings
    private static final Map<List<Integer>, Integer> STRAIGHT_CONNECTIONS = Map.of(
            List.of(0, 3), 0,
            List.of(1, 4), 60,
            List.of(2, 5), 120);

    private static final Map<List<Integer>, Integer> SMALL_CURVE_CONNECTIONS = Map.of(
            List.of(0, 1), 0,
            List.of(1, 2), 60,
            List.of(2, 3), 120,
            List.of(3, 4), 180,
            List.of(4, 5), 240,
            List.of(0, 5), 300);

    private static final Map<List<Integer>, Integer> LARGE_CURVE_CONNECTIONS = Map.of(
            List.of(0, 2), 0,
            List.of(1, 3), 60,
            List.of(2, 4), 120,
            List.of(3, 5), 180,
            List.of(0, 4), 240,
            List.of(1, 5), 300);

    private static final Map<List<Integer>, Integer> ROUNDABOUT_CONNECTIONS = Map.of(
            List.of(0, 0), 0,
            List.of(1, 1), 60,
            List.of(2, 2), 120,
            List.of(3, 3), 180,
            List.of(4, 4), 240,
            List.of(5, 5), 300);

    // Shape templates
    private static final Shape STRAIGHT_LINE_TEMPLATE = new Line2D.Float(CENTER_X, 0, CENTER_X, TILE_HEIGHT);

    private static final Shape SMALL_CURVE_TEMPLATE = new QuadCurve2D.Float(CENTER_X, 0, 197.25f, 106.78f, 302, 75);

    private static final Shape LARGE_CURVE_TEMPLATE = new QuadCurve2D.Float(CENTER_X, 0, 181.2f, 144.98f, 302, 225);

    private static final Shape ROUNDABOUT = new Ellipse2D.Float(112.5f, 90, 120, 120);

    private static final Shape ROUNDABOUT_CONNECTOR_TEMPLATE = new Line2D.Float(
            CENTER_X, 0,
            CENTER_X, 90);

    // Map connections to templates
    private static final List<ConnectionRule> CONNECTION_RULES = List.of(
            new ConnectionRule(STRAIGHT_CONNECTIONS, STRAIGHT_LINE_TEMPLATE),
            new ConnectionRule(SMALL_CURVE_CONNECTIONS, SMALL_CURVE_TEMPLATE),
            new ConnectionRule(LARGE_CURVE_CONNECTIONS, LARGE_CURVE_TEMPLATE),
            new ConnectionRule(ROUNDABOUT_CONNECTIONS, ROUNDABOUT_CONNECTOR_TEMPLATE));

    // Cache for overlays to avoid re-generating the same overlay multiple times
    // Key as canonical matrix to save only once for each unique matrix and rotate as needed
    private static final Map<String, BufferedImage> HYPERLANE_CACHE = new HashMap<>();

    /*
     * Connection matrix format: 0,0,0,1,0,0;0,0,0,0,0,0;0,0,0,0,0,0;1,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0
     * Generates the hyperlane as roundabout if any connections connect to itself
     */
    public static BufferedImage generateHyperlaneTile(Tile tile, Game game) {
        String matrix = game.getCustomHyperlaneData().get(tile.getPosition());
        boolean asRoundabout = CustomHyperlaneService.hasSelfConnection(matrix);

        BufferedImage hyperlaneBackground = getRandomTransformedBackground(tile, matrix);

        // Find canonical matrix and rotation offset
        MatrixRotationResult canonical = getCanonicalMatrix(matrix);

        // Use canonical matrix as cache key
        String cacheKey = canonical.matrix == null ? "null" : canonical.matrix;

        BufferedImage overlay = HYPERLANE_CACHE.computeIfAbsent(cacheKey, k -> {
            BufferedImage img = new BufferedImage(TILE_WIDTH, TILE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            Set<Shape> shapes = new LinkedHashSet<>();
            if (asRoundabout || matrix == null) {
                shapes.add(ROUNDABOUT);
            }
            for (List<Integer> connection : getConnectionsFromMatrix(canonical.matrix, asRoundabout)) {
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
            return img;
        });

        // Rotate overlay if needed
        BufferedImage rotatedOverlay = overlay;
        if (canonical.rotation != 0) {
            rotatedOverlay = new BufferedImage(TILE_WIDTH, TILE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = rotatedOverlay.createGraphics();
            g2.rotate(Math.toRadians(-canonical.rotation), CENTER_X, CENTER_Y);
            g2.drawImage(overlay, 0, 0, null);
            g2.dispose();
        }

        // Draw overlay on top of background
        Graphics2D g = hyperlaneBackground.createGraphics();
        g.drawImage(rotatedOverlay, 0, 0, null);
        g.dispose();

        return hyperlaneBackground;
    }

    private static void drawShapes(Graphics2D g, Set<Shape> shapes, HLStroke hlStroke, Color color) {
        g.setComposite(hlStroke.composite);
        g.setStroke(new BasicStroke(hlStroke.width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        g.setColor(color);
        for (Shape shape : shapes) g.draw(shape);
    }

    // If looking for selfConnections, return only those
    private static Set<List<Integer>> getConnectionsFromMatrix(String matrix, boolean selfConnections) {
        Set<List<Integer>> pairs = new HashSet<>();
        if (matrix == null) {
            return pairs;
        }

        String[] rows = matrix.split(";");
        for (int i = 0; i < 6; i++) {
            String[] cols = rows[i].split(",");
            for (int j = 0; j < 6; j++) {
                if ("1".equals(cols[j].trim()) && (!selfConnections || i == j)) {
                    pairs.add(List.of(Math.min(i, j), Math.max(i, j)));
                }
            }
        }
        return pairs;
    }

    // Randomize hyperlane tile background based on matrix, or use default if no matrix present
    private static BufferedImage getRandomTransformedBackground(Tile tile, String matrix) {
        String tilePath = tile.getTilePath();
        int transform = -1;
        if (matrix != null) {
            Random rand = new Random(matrix.hashCode());
            String randomTile = RANDOM_BACKGROUNDS.get(rand.nextInt(RANDOM_BACKGROUNDS.size()));
            tilePath = ResourceHelper.getInstance().getTileFile(randomTile);
            if (tilePath == null) {
                tilePath = tile.getTilePath();
            }
            transform = rand.nextInt(4); // 0, 1, 2, or 3
        }

        BufferedImage original = ImageHelper.read(tilePath);
        BufferedImage transformed = new BufferedImage(TILE_WIDTH, TILE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = transformed.createGraphics();

        switch (transform) {
            case 1 ->
                g.drawImage(
                        original,
                        0,
                        0,
                        TILE_WIDTH,
                        TILE_HEIGHT,
                        TILE_WIDTH,
                        0,
                        0,
                        TILE_HEIGHT,
                        null); // Horizontal flip
            case 2 ->
                g.drawImage(
                        original, 0, 0, TILE_WIDTH, TILE_HEIGHT, 0, TILE_HEIGHT, TILE_WIDTH, 0, null); // Vertical flip
            case 3 ->
                g.drawImage(
                        original,
                        0,
                        0,
                        TILE_WIDTH,
                        TILE_HEIGHT,
                        TILE_WIDTH,
                        TILE_HEIGHT,
                        0,
                        0,
                        null); // Both flips (180° rotate)
            default -> g.drawImage(original, 0, 0, null); // No transformation
        }

        g.dispose();
        return transformed;
    }

    // Returns the canonical matrix and the rotation needed to match the input
    private static MatrixRotationResult getCanonicalMatrix(String matrix) {
        if (matrix == null) return new MatrixRotationResult(null, 0);
        String minMatrix = matrix;
        int minRotation = 0;
        String current = matrix;
        for (int rot = 1; rot < 6; rot++) {
            current = rotateMatrix60(current);
            if (current.compareTo(minMatrix) < 0) {
                minMatrix = current;
                minRotation = rot * 60;
            }
        }
        return new MatrixRotationResult(minMatrix, minRotation);
    }

    // Rotates a 6x6 matrix string by 60 degrees
    private static String rotateMatrix60(String matrix) {
        if (matrix == null) return null;
        String[] rows = matrix.split(";");
        int size = 6;
        int[][] mat = new int[size][size];

        // Parse matrix string to int array
        for (int i = 0; i < size; i++) {
            String[] cols = rows[i].split(",");
            for (int j = 0; j < size; j++) {
                mat[i][j] = Integer.parseInt(cols[j].trim());
            }
        }

        // Rotate: [i][j] -> [(i+1)%6][(j+1)%6]
        int[][] rotated = new int[size][size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                rotated[(i + 1) % size][(j + 1) % size] = mat[i][j];
            }
        }

        // Convert back to string
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size; i++) {
            if (i > 0) sb.append(";");
            for (int j = 0; j < size; j++) {
                if (j > 0) sb.append(",");
                sb.append(rotated[i][j]);
            }
        }
        return sb.toString();
    }

    // Connection rules to angles with shape cache
    private static class ConnectionRule {
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

    // Helper class to hold canonical matrix and rotation
    private static class MatrixRotationResult {
        final String matrix;
        final int rotation; // in degrees, 0, 60, ..., 300

        MatrixRotationResult(String matrix, int rotation) {
            this.matrix = matrix;
            this.rotation = rotation;
        }
    }
}
