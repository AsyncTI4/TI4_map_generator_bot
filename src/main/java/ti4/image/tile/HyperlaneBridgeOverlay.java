package ti4.image.tile;

import static ti4.image.tile.LaneInk.AMBER_CORE;
import static ti4.image.tile.LaneInk.AMBER_GLOW;
import static ti4.image.tile.LaneInk.ARROW;
import static ti4.image.tile.LaneInk.BLUE_CORE;
import static ti4.image.tile.LaneInk.BLUE_GLOW;
import static ti4.image.tile.LaneInk.ENTRY_GATE;
import static ti4.image.tile.LaneInk.EXIT_GATE;
import static ti4.image.tile.LaneInk.path;
import static ti4.image.tile.LaneInk.rampStroke;
import static ti4.image.tile.LaneInk.stroke;
import static ti4.image.tile.TileGeometry.TILE_HEIGHT;
import static ti4.image.tile.TileGeometry.TILE_WIDTH;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import ti4.model.HyperlaneStyle;
import ti4.service.map.HyperlaneBridgeEnds.BridgeEnd;

final class HyperlaneBridgeOverlay {

    private static final double GATE_DEPTH = 9;
    private static final double CENTER_X = TILE_WIDTH / 2.0;
    private static final double CENTER_Y = TILE_HEIGHT / 2.0;

    private static final Map<String, BufferedImage> CACHE = new ConcurrentHashMap<>();

    private HyperlaneBridgeOverlay() {}

    static BufferedImage onSystem(
            String artKey, BufferedImage art, Shape occluders, List<BridgeEnd> ends, HyperlaneStyle style) {
        return CACHE.computeIfAbsent(key(artKey, ends, style), _ -> compose(art, occluders, ends, style, false));
    }

    static BufferedImage onUnexplored(
            String artKey, BufferedImage background, List<BridgeEnd> ends, HyperlaneStyle style) {
        return CACHE.computeIfAbsent(
                key("unexplored:" + artKey, ends, style),
                _ -> compose(background, new Path2D.Double(), ends, style, true));
    }

    private static String key(String artKey, List<BridgeEnd> ends, HyperlaneStyle style) {
        StringBuilder key = new StringBuilder(artKey).append('|').append(style.name());
        for (BridgeEnd end : ends) {
            key.append('|').append(end.side()).append(end.entry() ? 'i' : '-').append(end.exit() ? 'o' : '-');
        }
        return key.toString();
    }

    private static BufferedImage compose(
            BufferedImage art, Shape occluders, List<BridgeEnd> ends, HyperlaneStyle style, boolean unexplored) {
        BridgeTrail.Trail trail = BridgeTrail.of(ends, occluders);
        List<BridgeTrail.Piece> pieces = new ArrayList<>(trail.ring());
        pieces.addAll(trail.stubs());
        BufferedImage image = new BufferedImage(TILE_WIDTH, TILE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(art, 0, 0, null);
        g.drawImage(lanes(pieces, style), 0, 0, null);
        if (!unexplored) {
            g.setClip(occluders);
            g.drawImage(art, 0, 0, null);
            g.setClip(null);
        } else {
            unexploredPing(g);
        }
        g.drawImage(markers(ends, trail), 0, 0, null);
        g.dispose();
        return image;
    }

    private static BufferedImage lanes(List<BridgeTrail.Piece> pieces, HyperlaneStyle style) {
        BufferedImage image = new BufferedImage(TILE_WIDTH, TILE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setClip(LaneInk.hexOutline());
        for (BridgeTrail.Piece piece : pieces) {
            if (piece.isOneWay() && style == HyperlaneStyle.FADING) {
                rampStroke(g, piece.inTravelDirection(), 20, 20, 0.08f, 0.5f, AMBER_GLOW);
            } else {
                stroke(g, path(piece.points()), 20, 0.4f, piece.isOneWay() ? AMBER_GLOW : BLUE_GLOW, null);
            }
        }
        for (BridgeTrail.Piece piece : pieces) LaneInk.clear(g, path(piece.points()));
        for (BridgeTrail.Piece piece : pieces) core(g, piece, style);
        g.dispose();
        return image;
    }

    private static void core(Graphics2D g, BridgeTrail.Piece piece, HyperlaneStyle style) {
        if (!piece.isOneWay()) {
            stroke(g, path(piece.points()), 4, 0.8f, BLUE_CORE, null);
            return;
        }
        switch (style) {
            case DASHED -> stroke(g, path(piece.points()), 4, 0.9f, AMBER_CORE, new float[] {9, 7});
            case FADING -> rampStroke(g, piece.inTravelDirection(), 1.5f, 5f, 0.2f, 1f, AMBER_CORE);
            default -> stroke(g, path(piece.points()), 4, 0.85f, AMBER_CORE, null);
        }
    }

    private static BufferedImage markers(List<BridgeEnd> ends, BridgeTrail.Trail trail) {
        BufferedImage image = new BufferedImage(TILE_WIDTH, TILE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setClip(LaneInk.hexOutline());
        g.setComposite(AlphaComposite.SrcOver);
        for (BridgeTrail.Arrow arrow : trail.cornerArrows()) {
            LaneInk.arrowHead(g, new double[] {arrow.x(), arrow.y(), arrow.angle()}, ARROW);
        }
        for (BridgeEnd end : ends) LaneInk.drawNode(g, BridgeTrail.edgeMidpoint(end.side()));
        for (BridgeEnd end : ends) {
            if (!end.isOneWay()) continue;
            LaneInk.gateBar(
                    g, gatePoint(end.side()), Math.toRadians(end.side() * 60), end.entry() ? ENTRY_GATE : EXIT_GATE);
        }
        g.dispose();
        return image;
    }

    private static Point2D gatePoint(int side) {
        Point2D edge = BridgeTrail.edgeMidpoint(side);
        double towardCenter = GATE_DEPTH / edge.distance(CENTER_X, CENTER_Y);
        return new Point2D.Double(
                edge.getX() + (CENTER_X - edge.getX()) * towardCenter,
                edge.getY() + (CENTER_Y - edge.getY()) * towardCenter);
    }

    private static void unexploredPing(Graphics2D g) {
        g.setColor(new Color(10, 20, 40, 120));
        g.fill(new Ellipse2D.Double(CENTER_X - 30, CENTER_Y - 30, 60, 60));
        g.setColor(new Color(225, 245, 255));
        g.fill(new Ellipse2D.Double(CENTER_X - 3, CENTER_Y - 3, 6, 6));
        g.setStroke(new BasicStroke(2f));
        for (int ring = 1; ring <= 4; ring++) {
            double radius = 3 + ring * 6.5;
            g.setColor(new Color(80, 190, 255, 255 - ring * 45));
            g.draw(new Ellipse2D.Double(CENTER_X - radius, CENTER_Y - radius, 2 * radius, 2 * radius));
        }
    }
}
