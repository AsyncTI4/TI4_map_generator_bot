package ti4.image.tile;

import static ti4.image.tile.TileGeometry.TILE_HEIGHT;
import static ti4.image.tile.TileGeometry.TILE_WIDTH;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.List;
import ti4.service.map.PortalSlot;
import ti4.service.map.PortalSlot.Flow;

final class PortalTokens {

    private static final double CENTER_X = TILE_WIDTH / 2.0;
    private static final double CENTER_Y = TILE_HEIGHT / 2.0;
    private static final double RING_RADIUS = 60;
    private static final double SLOT_DISTANCE = 104;
    private static final double TOKEN_RADIUS = 21;
    private static final double CENTRE_TOKEN_RADIUS = 26;
    private static final int HUES = 13;
    private static final Color TOKEN_INSIDE = new Color(25, 35, 70);
    private static final Color TOKEN_EDGE = new Color(5, 10, 25);

    private PortalTokens() {}

    static BufferedImage drawnOn(BufferedImage hyperlane, List<PortalSlot> slots, boolean drawRing) {
        Graphics2D g = hyperlane.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setClip(LaneInk.hexOutline());
        if (drawRing) ring(g);
        for (PortalSlot slot : slots) {
            if (slot.slot() != PortalSlot.CENTRE) {
                connector(g, slot);
            } else if (slot.flow() != Flow.BOTH) {
                centreArrow(g, slot.flow());
            }
        }
        for (PortalSlot slot : slots) {
            boolean centre = slot.slot() == PortalSlot.CENTRE;
            token(
                    g,
                    centre ? new Point2D.Double(CENTER_X, CENTER_Y) : slotPoint(slot.slot(), SLOT_DISTANCE),
                    centre ? CENTRE_TOKEN_RADIUS : TOKEN_RADIUS,
                    slot.letter());
        }
        g.dispose();
        return hyperlane;
    }

    static Color colourOf(char letter) {
        int index = Math.floorMod(Character.toUpperCase(letter) - 'A', 2 * HUES);
        float hue = ((index * 5) % HUES) / (float) HUES;
        boolean pastel = index >= HUES;
        return Color.getHSBColor(hue, pastel ? 0.45f : 0.85f, pastel ? 1.0f : 0.95f);
    }

    static Point2D slotPoint(int slot, double distance) {
        double angle = Math.toRadians((slot - 1) * 60 + 30);
        return new Point2D.Double(CENTER_X + distance * Math.sin(angle), CENTER_Y - distance * Math.cos(angle));
    }

    private static void ring(Graphics2D g) {
        Ellipse2D circle =
                new Ellipse2D.Double(CENTER_X - RING_RADIUS, CENTER_Y - RING_RADIUS, 2 * RING_RADIUS, 2 * RING_RADIUS);
        LaneInk.stroke(g, circle, 20, 0.4f, LaneInk.BLUE_GLOW, null);
        LaneInk.stroke(g, circle, 4, 0.8f, LaneInk.BLUE_CORE, null);
    }

    private static void connector(Graphics2D g, PortalSlot slot) {
        Color colour = colourOf(slot.letter());
        Line2D line = new Line2D.Double(
                slotPoint(slot.slot(), RING_RADIUS), slotPoint(slot.slot(), SLOT_DISTANCE - TOKEN_RADIUS));
        g.setStroke(new BasicStroke(12f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), 90));
        g.draw(line);
        g.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(colour);
        g.draw(line);
        if (slot.flow() != Flow.BOTH) {
            Point2D middle = slotPoint(slot.slot(), (RING_RADIUS + SLOT_DISTANCE - TOKEN_RADIUS) / 2);
            double a = Math.toRadians((slot.slot() - 1) * 60 + 30);
            double outward = Math.atan2(-Math.cos(a), Math.sin(a));
            double angle = slot.flow() == Flow.ENTRY_ONLY ? outward : outward + Math.PI;
            LaneInk.arrowHead(g, new double[] {middle.getX(), middle.getY(), angle}, LaneInk.ARROW);
        }
    }

    private static void centreArrow(Graphics2D g, Flow flow) {
        double y = CENTER_Y - (RING_RADIUS + CENTRE_TOKEN_RADIUS) / 2;
        double intoThePortal = Math.PI / 2;
        double angle = flow == Flow.ENTRY_ONLY ? intoThePortal : -intoThePortal;
        LaneInk.arrowHead(g, new double[] {CENTER_X, y, angle}, LaneInk.ARROW);
    }

    private static void token(Graphics2D g, Point2D at, double radius, char letter) {
        Color colour = colourOf(letter);
        float halo = (float) (radius * 1.6);
        g.setPaint(new RadialGradientPaint(at, halo, new float[] {0f, 0.6f, 1f}, new Color[] {
            withAlpha(colour, 130), withAlpha(colour, 50), withAlpha(colour, 0)
        }));
        g.fill(new Ellipse2D.Double(at.getX() - halo, at.getY() - halo, 2 * halo, 2 * halo));
        g.setPaint(new RadialGradientPaint(
                at, (float) radius, new float[] {0f, 1f}, new Color[] {TOKEN_INSIDE, TOKEN_EDGE}));
        Ellipse2D disc = new Ellipse2D.Double(at.getX() - radius, at.getY() - radius, 2 * radius, 2 * radius);
        g.fill(disc);
        g.setColor(colour);
        g.setStroke(new BasicStroke(3.5f));
        g.draw(disc);
        String text = String.valueOf(letter);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) (radius * 1.2)));
        FontMetrics metrics = g.getFontMetrics();
        int x = (int) (at.getX() - metrics.stringWidth(text) / 2.0);
        int y = (int) (at.getY() + metrics.getAscent() * 0.36);
        g.setColor(TOKEN_EDGE);
        g.drawString(text, x + 1, y + 1);
        g.setColor(colour.brighter());
        g.drawString(text, x, y);
    }

    private static Color withAlpha(Color colour, int alpha) {
        return new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), alpha);
    }
}
