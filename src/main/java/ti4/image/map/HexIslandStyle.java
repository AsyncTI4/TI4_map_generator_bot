package ti4.image.map;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import ti4.game.Tile;
import ti4.image.draw.HorizontalAlign;
import ti4.image.draw.OutlinedText;
import ti4.image.draw.VerticalAlign;
import ti4.image.io.Fonts;
import ti4.image.tile.TileGeometry;
import ti4.image.tile.TileLayers;
import ti4.image.tile.TileStep;

final class HexIslandStyle {

    static final int MARGIN = 60;
    static final int HEADER = 50;
    private static final int OUTLINE_WIDTH = 10;
    private static final int LABEL_HEIGHT = 80;
    private static final int LABEL_PADDING = 30;
    private static final int LABEL_LIFT = 6;
    private static final Color LABEL_FILL = new Color(0, 0, 0, 200);
    private static final Font LABEL_FONT = Fonts.ti(32).deriveFont(56f);
    private static final FontMetrics LABEL_METRICS =
            new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics().getFontMetrics(LABEL_FONT);

    record Canvas(Rectangle content, double scale, int width, int height) {

        static Canvas of(Rectangle content, int maxSize, String header) {
            int contentWidth = content.width + 2 * MARGIN;
            int contentHeight = content.height + 2 * MARGIN;
            double scale = Math.min(1.0, (double) maxSize / Math.max(contentWidth, contentHeight));
            int width = Math.max((int) Math.ceil(contentWidth * scale), headerWidth(header));
            int height = HEADER + (int) Math.ceil(contentHeight * scale);
            return new Canvas(content, scale, width, height);
        }

        BufferedImage newImage() {
            return new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        }

        void enter(Graphics2D graphics) {
            AffineTransform transform = new AffineTransform();
            transform.translate(0, HEADER);
            transform.scale(scale, scale);
            transform.translate(MARGIN - content.x, MARGIN - content.y);
            graphics.transform(transform);
        }
    }

    private HexIslandStyle() {}

    static int labelWidth(String title) {
        return textWidth(title) + 2 * LABEL_PADDING;
    }

    static int labelHeight() {
        return LABEL_HEIGHT;
    }

    static int textWidth(String text) {
        return LABEL_METRICS.stringWidth(text);
    }

    static void drawText(Graphics2D graphics, String text, int left, int centreY, Color color) {
        graphics.setFont(LABEL_FONT);
        OutlinedText.draw(
                graphics,
                text,
                left,
                centreY,
                color,
                HorizontalAlign.Left,
                VerticalAlign.Center,
                new BasicStroke(4),
                Color.BLACK);
    }

    static List<Rectangle> labelChoices(String title, Collection<Point> centres) {
        int width = labelWidth(title);
        Point top = extreme(
                centres, Comparator.comparingInt((Point centre) -> centre.y).thenComparingInt(centre -> centre.x));
        Point bottom = extreme(
                centres, Comparator.comparingInt((Point centre) -> -centre.y).thenComparingInt(centre -> centre.x));
        Point left = extreme(
                centres, Comparator.comparingInt((Point centre) -> centre.x).thenComparingInt(centre -> centre.y));
        Point right = extreme(
                centres, Comparator.comparingInt((Point centre) -> -centre.x).thenComparingInt(centre -> centre.y));
        int besideY = LABEL_HEIGHT / 2;
        return List.of(
                new Rectangle(
                        top.x - width / 2,
                        top.y - HexPacker.HEX_HALF_HEIGHT - LABEL_HEIGHT - LABEL_LIFT,
                        width,
                        LABEL_HEIGHT),
                new Rectangle(
                        bottom.x - width / 2, bottom.y + HexPacker.HEX_HALF_HEIGHT + LABEL_LIFT, width, LABEL_HEIGHT),
                new Rectangle(
                        left.x - HexPacker.HEX_HALF_WIDTH - LABEL_LIFT - width, left.y - besideY, width, LABEL_HEIGHT),
                new Rectangle(right.x + HexPacker.HEX_HALF_WIDTH + LABEL_LIFT, right.y - besideY, width, LABEL_HEIGHT));
    }

    private static Point extreme(Collection<Point> centres, Comparator<Point> order) {
        return centres.stream().min(order).orElse(new Point(0, 0));
    }

    static Rectangle contentBounds(Collection<Point> centres, Collection<Rectangle> labels) {
        Rectangle bounds = null;
        for (Rectangle label : labels) {
            bounds = bounds == null ? new Rectangle(label) : bounds.union(label);
        }
        for (Point centre : centres) {
            Rectangle hex = HexPacker.hexBox(centre);
            bounds = bounds == null ? hex : bounds.union(hex);
        }
        return bounds == null ? new Rectangle(0, 0, 1, 1) : bounds;
    }

    private static int headerWidth(String header) {
        Graphics2D probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics();
        try {
            return probe.getFontMetrics(Fonts.ti(32)).stringWidth(header) + 2 * MARGIN;
        } finally {
            probe.dispose();
        }
    }

    static Rectangle tileRectangle(Point centre) {
        return new Rectangle(
                centre.x - TileGeometry.TILE_WIDTH / 2,
                centre.y - TileGeometry.TILE_HEIGHT / 2,
                TileGeometry.TILE_WIDTH,
                TileGeometry.TILE_HEIGHT);
    }

    static void drawLayer(Graphics2D graphics, TileLayers layers, Tile tile, Point centre, TileStep step) {
        Rectangle hex = tileRectangle(centre);
        Point shift = TileGeometry.followsArtworkOffset(step) ? TileGeometry.hexOffsetInArtwork(tile) : new Point();
        Shape previousClip = graphics.getClip();
        if (step == TileStep.Tile) {
            graphics.clip(MapFrame.hexPolygon(hex));
        }
        try {
            layers.paintPaintedPart(
                    tile,
                    step,
                    graphics,
                    hex.x - TileGeometry.TILE_PADDING - shift.x,
                    hex.y - TileGeometry.TILE_PADDING - shift.y);
        } finally {
            graphics.setClip(previousClip);
        }
    }

    static void drawOutline(Graphics2D graphics, Collection<Point> centres, Color color) {
        Set<Point> occupied = new HashSet<>(centres);
        graphics.setColor(color);
        graphics.setStroke(new BasicStroke(OUTLINE_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (Point centre : centres) {
            Polygon hex = MapFrame.hexPolygon(tileRectangle(centre));
            for (int edge = 0; edge < 6; edge++) {
                Point step = HexPacker.NEIGHBOUR_OFFSETS.get(edge);
                if (occupied.contains(new Point(centre.x + step.x, centre.y + step.y))) {
                    continue;
                }
                int next = (edge + 1) % 6;
                graphics.drawLine(hex.xpoints[edge], hex.ypoints[edge], hex.xpoints[next], hex.ypoints[next]);
            }
        }
    }

    static void drawLabel(Graphics2D graphics, Rectangle label, String title, Color color) {
        graphics.setColor(LABEL_FILL);
        graphics.fillRoundRect(label.x, label.y, label.width, label.height, LABEL_HEIGHT / 2, LABEL_HEIGHT / 2);
        graphics.setColor(color);
        graphics.setStroke(new BasicStroke(5));
        graphics.drawRoundRect(label.x, label.y, label.width, label.height, LABEL_HEIGHT / 2, LABEL_HEIGHT / 2);
        graphics.setFont(LABEL_FONT);
        OutlinedText.draw(
                graphics,
                title,
                label.x + label.width / 2,
                label.y + label.height / 2,
                color,
                HorizontalAlign.Center,
                VerticalAlign.Center,
                new BasicStroke(4),
                Color.BLACK);
    }
}
