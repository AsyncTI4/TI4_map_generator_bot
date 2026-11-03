package ti4.image.tile;

import static ti4.image.tile.TileGeometry.TILE_HEIGHT;
import static ti4.image.tile.TileGeometry.TILE_WIDTH;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.QuadCurve2D;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.model.HyperlaneStyle;
import ti4.service.map.CustomHyperlaneService;
import ti4.service.map.HyperlaneStyleService;

public final class HyperlaneTileGenerator {

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

    private static final String UNSEEN_LANES_KEY = "unseenLanes";
    private static final double PING_INSET = 0.17;

    private static final Point2D SMALL_CURVE_CONTROL = new Point2D.Float(197.25f, 106.78f);
    private static final Point2D LARGE_CURVE_CONTROL = new Point2D.Float(181.2f, 144.98f);

    private static final Shape SMALL_CURVE_TEMPLATE = new QuadCurve2D.Float(
            CENTER_X, 0, (float) SMALL_CURVE_CONTROL.getX(), (float) SMALL_CURVE_CONTROL.getY(), 302, 75);

    private static final Shape LARGE_CURVE_TEMPLATE = new QuadCurve2D.Float(
            CENTER_X, 0, (float) LARGE_CURVE_CONTROL.getX(), (float) LARGE_CURVE_CONTROL.getY(), 302, 225);

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
    private static final Map<String, BufferedImage> HYPERLANE_CACHE = new ConcurrentHashMap<>();

    /*
     * Connection matrix format: 0,0,0,1,0,0;0,0,0,0,0,0;0,0,0,0,0,0;1,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0
     * Generates the hyperlane as roundabout if any connections connect to itself
     */
    public static BufferedImage generateHyperlaneTile(Tile tile, Game game) {
        return generateHyperlaneTile(tile, game, HyperlaneStyleService.gameDefault(game));
    }

    public static BufferedImage generateHyperlaneTile(Tile tile, Game game, HyperlaneStyle style) {
        String matrix = game.getCustomHyperlaneData().get(tile.getPosition());
        return generateHyperlaneTile(tile, matrix, matrix, false, style);
    }

    public static BufferedImage generateHyperlaneTile(
            Tile tile,
            @Nullable String backgroundSeed,
            @Nullable String matrix,
            boolean lanesHidden,
            HyperlaneStyle style) {
        boolean asRoundabout = CustomHyperlaneService.hasSelfConnection(matrix);

        BufferedImage hyperlaneBackground = getRandomTransformedBackground(tile, backgroundSeed);

        // Find canonical matrix and rotation offset
        MatrixRotationResult canonical = getCanonicalMatrix(matrix);

        if (lanesHidden) {
            drawOverlay(
                    hyperlaneBackground, HYPERLANE_CACHE.computeIfAbsent(UNSEEN_LANES_KEY, _ -> unseenLanePings()), 0);
        }

        if (CustomHyperlaneService.hasOneWayLanes(canonical.matrix)) {
            BufferedImage oneWayOverlay = HYPERLANE_CACHE.computeIfAbsent(
                    style.name() + ":" + canonical.matrix,
                    _ -> OneWayHyperlaneOverlay.draw(CustomHyperlaneService.parseMatrix(canonical.matrix), style));
            return drawOverlay(hyperlaneBackground, oneWayOverlay, canonical.rotation);
        }

        // Use canonical matrix as cache key
        String cacheKey = canonical.matrix == null ? "null" : canonical.matrix;

        BufferedImage overlay = HYPERLANE_CACHE.computeIfAbsent(cacheKey, _ -> {
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

        return drawOverlay(hyperlaneBackground, overlay, canonical.rotation);
    }

    private static BufferedImage drawOverlay(BufferedImage background, BufferedImage overlay, int rotation) {
        // Rotate overlay if needed
        BufferedImage rotatedOverlay = overlay;
        if (rotation != 0) {
            rotatedOverlay = new BufferedImage(TILE_WIDTH, TILE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = rotatedOverlay.createGraphics();
            g2.rotate(Math.toRadians(-rotation), CENTER_X, CENTER_Y);
            g2.drawImage(overlay, 0, 0, null);
            g2.dispose();
        }

        // Draw overlay on top of background
        Graphics2D g = background.createGraphics();
        g.drawImage(rotatedOverlay, 0, 0, null);
        g.dispose();

        return background;
    }

    private static BufferedImage unseenLanePings() {
        BufferedImage img = new BufferedImage(TILE_WIDTH, TILE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double[][] corners = {
            {TILE_WIDTH / 4.0, 0}, {TILE_WIDTH * 3 / 4.0, 0}, {TILE_WIDTH, CENTER_Y},
            {TILE_WIDTH * 3 / 4.0, TILE_HEIGHT}, {TILE_WIDTH / 4.0, TILE_HEIGHT}, {0, CENTER_Y}
        };
        for (double[] corner : corners) {
            double x = corner[0] + (CENTER_X - corner[0]) * PING_INSET;
            double y = corner[1] + (CENTER_Y - corner[1]) * PING_INSET;
            drawRipple(g, x, y, Math.atan2(CENTER_Y - y, CENTER_X - x));
        }
        g.dispose();
        return img;
    }

    private static void drawRipple(Graphics2D g, double x, double y, double towardCenter) {
        g.setColor(new Color(10, 20, 40, 120));
        g.fill(new Ellipse2D.Double(x - 19, y - 19, 38, 38));
        g.setColor(new Color(225, 245, 255));
        g.fill(new Ellipse2D.Double(x - 1.8, y - 1.8, 3.6, 3.6));
        g.setStroke(new BasicStroke(1.4f));
        for (int ring = 1; ring <= 4; ring++) {
            double radius = 1.8 + ring * 4;
            Color bright = new Color(80, 190, 255, 255 - ring * 35);
            Color faded = new Color(80, 190, 255, 0);
            double dx = Math.cos(towardCenter) * radius, dy = Math.sin(towardCenter) * radius;
            g.setPaint(new GradientPaint(
                    (float) (x + dx), (float) (y + dy), bright, (float) (x - dx), (float) (y - dy), faded));
            g.draw(new Ellipse2D.Double(x - radius, y - radius, 2 * radius, 2 * radius));
        }
        g.setPaint(null);
    }

    @Nullable
    static Point2D curveControlPoint(int from, int to) {
        int steps = Math.floorMod(to - from, 6);
        if (steps == 3) return null;
        int firstSide = steps < 3 ? from : to;
        Point2D template = Math.min(steps, 6 - steps) == 1 ? SMALL_CURVE_CONTROL : LARGE_CURVE_CONTROL;
        return AffineTransform.getRotateInstance(Math.toRadians(firstSide * 60), CENTER_X, CENTER_Y)
                .transform(template, null);
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
        String tilePath = ImageResources.tilePath(tile);
        int transform = -1;
        if (matrix != null) {
            // Use matrix hashCode as seed for determinism: same matrix always yields same background/transform
            long seed = matrix.hashCode();
            int tileIndex = Math.floorMod(seed, RANDOM_BACKGROUNDS.size());
            String randomTile = RANDOM_BACKGROUNDS.get(tileIndex);
            String randomTilePath = ImageResources.tile(randomTile);
            if (randomTilePath != null) {
                tilePath = randomTilePath;
            }
            transform = Math.floorMod(
                    seed * 6364136223846793005L + 1442695040888963407L,
                    4); // LCG step (Knuth MMIX constants) for independent transform selection; 0, 1, 2, or 3
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
            current = CustomHyperlaneService.rotateMatrix60(current);
            if (current.compareTo(minMatrix) < 0) {
                minMatrix = current;
                minRotation = rot * 60;
            }
        }
        return new MatrixRotationResult(minMatrix, minRotation);
    }

    // Connection rules to angles with shape cache
    private static class ConnectionRule {
        private final Map<List<Integer>, Shape> rotatedCache = new ConcurrentHashMap<>();
        private final Map<List<Integer>, Integer> angleMap;
        private final Shape template;

        ConnectionRule(Map<List<Integer>, Integer> angleMap, Shape template) {
            this.angleMap = angleMap;
            this.template = template;
        }

        boolean matches(List<Integer> connection) {
            return angleMap.containsKey(connection);
        }

        Shape getShape(List<Integer> connection) {
            return rotatedCache.computeIfAbsent(connection, conn -> {
                double angleRad = Math.toRadians(angleMap.get(conn));
                AffineTransform transform = AffineTransform.getRotateInstance(angleRad, CENTER_X, CENTER_Y);
                return transform.createTransformedShape(template);
            });
        }
    }

    /**
     * @param rotation in degrees, 0, 60, ..., 300
     */
    // Helper class to hold canonical matrix and rotation
    private record MatrixRotationResult(String matrix, int rotation) {}
}
