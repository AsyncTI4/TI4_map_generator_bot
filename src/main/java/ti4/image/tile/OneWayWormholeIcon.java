package ti4.image.tile;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.model.TokenModel;
import ti4.model.WormholeModel.OneWay;

final class OneWayWormholeIcon {

    static final int SIZE = 80;
    private static final int TOKEN_SIZE = 64;
    private static final double RING_RADIUS = 35;
    private static final double ARC_EXTENT = 88;
    private static final Color ENTRY_COLOR = new Color(60, 200, 90);
    private static final Color EXIT_COLOR = new Color(225, 60, 55);
    private static final Color OUTLINE = new Color(10, 10, 10, 230);
    private static final Font BADGE_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 12);

    private OneWayWormholeIcon() {}

    static BufferedImage image(TokenModel token) {
        String basePath = ImageResources.tokenOrAttachment(token.getBaseImagePath());
        BufferedImage base = basePath == null ? null : ImageHelper.read(basePath);
        OneWay oneWay = token.getOneWay();
        if (base == null) return compose(null, oneWay);
        String key = "oneWayWormhole:" + token.getBaseImagePath() + ":" + oneWay;
        BufferedImage cached = ImageHelper.cached(key, () -> compose(base, oneWay));
        return cached != null ? cached : compose(base, oneWay);
    }

    static BufferedImage compose(BufferedImage base, OneWay oneWay) {
        BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        Color color = oneWay == OneWay.ENTRY ? ENTRY_COLOR : EXIT_COLOR;
        drawArrowRing(g, oneWay == OneWay.ENTRY, color);
        if (base != null) {
            int offset = (SIZE - TOKEN_SIZE) / 2;
            g.drawImage(base, offset, offset, TOKEN_SIZE, TOKEN_SIZE, null);
        }
        drawBadge(g, oneWay == OneWay.ENTRY ? "IN" : "OUT", color);
        g.dispose();
        return out;
    }

    private static void drawArrowRing(Graphics2D g, boolean counterClockwise, Color color) {
        double center = SIZE / 2.0;
        for (int i = 0; i < 3; i++) {
            double start = 90 + i * 120 + (120 - ARC_EXTENT) / 2;
            double extent = counterClockwise ? ARC_EXTENT : -ARC_EXTENT;
            double from = counterClockwise ? start : start + ARC_EXTENT;
            Arc2D arc = new Arc2D.Double(
                    center - RING_RADIUS,
                    center - RING_RADIUS,
                    2 * RING_RADIUS,
                    2 * RING_RADIUS,
                    from,
                    extent,
                    Arc2D.OPEN);
            Path2D head = arrowHead(center, Math.toRadians(from + extent), counterClockwise);
            g.setColor(OUTLINE);
            g.setStroke(new BasicStroke(7f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
            g.draw(arc);
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(head);
            g.setColor(color);
            g.setStroke(new BasicStroke(4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
            g.draw(arc);
            g.fill(head);
        }
    }

    private static Path2D arrowHead(double center, double angle, boolean counterClockwise) {
        double x = center + Math.cos(angle) * RING_RADIUS;
        double y = center - Math.sin(angle) * RING_RADIUS;
        double sign = counterClockwise ? 1 : -1;
        double tangentX = -Math.sin(angle) * sign;
        double tangentY = -Math.cos(angle) * sign;
        double normalX = Math.cos(angle);
        double normalY = -Math.sin(angle);
        double length = 9;
        double halfWidth = 6;
        Path2D head = new Path2D.Double();
        head.moveTo(x + tangentX * length, y + tangentY * length);
        head.lineTo(x + normalX * halfWidth, y + normalY * halfWidth);
        head.lineTo(x - normalX * halfWidth, y - normalY * halfWidth);
        head.closePath();
        return head;
    }

    private static void drawBadge(Graphics2D g, String text, Color color) {
        g.setFont(BADGE_FONT);
        FontMetrics metrics = g.getFontMetrics();
        int width = metrics.stringWidth(text) + 8;
        int height = metrics.getAscent() + 2;
        double x = SIZE - width - 1;
        double y = SIZE - height - 1;
        RoundRectangle2D badge = new RoundRectangle2D.Double(x, y, width, height, 6, 6);
        g.setColor(color.darker());
        g.fill(badge);
        g.setColor(OUTLINE);
        g.setStroke(new BasicStroke(1.5f));
        g.draw(badge);
        g.setColor(Color.WHITE);
        g.drawString(text, (float) x + 4, (float) y + metrics.getAscent());
    }
}
