package ti4.image.tile;

import static ti4.image.tile.LaneInk.AMBER_CORE;
import static ti4.image.tile.LaneInk.AMBER_GLOW;
import static ti4.image.tile.LaneInk.ARROW;
import static ti4.image.tile.LaneInk.BLUE_CORE;
import static ti4.image.tile.LaneInk.BLUE_GLOW;
import static ti4.image.tile.LaneInk.ENTRY_GATE;
import static ti4.image.tile.LaneInk.EXIT_GATE;
import static ti4.image.tile.LaneInk.OUTLINE;
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
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import ti4.model.HyperlaneStyle;
import ti4.service.map.BridgeEnd;
import ti4.service.map.BridgeMatrix;

final class HyperlaneBridgeOverlay {

    private static final double GATE_DEPTH = 9;
    private static final double TUNNEL_HALF_LENGTH = 11;
    private static final double TUNNEL_HALF_WIDTH = 14;
    private static final double TUNNEL_MOUTH_OFFSET = 12;
    private static final float LANE_END_GLOW_RADIUS = 24;
    private static final int LANE_END_GLOW_CORE = 210;
    private static final int LANE_END_GLOW_HALO = 140;
    private static final double CENTER_X = TILE_WIDTH / 2.0;
    private static final double CENTER_Y = TILE_HEIGHT / 2.0;

    private static final int CACHED_IMAGES = 256;
    private static final Map<String, BufferedImage> CACHE =
            Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest) {
                    return size() > CACHED_IMAGES;
                }
            });

    private HyperlaneBridgeOverlay() {}

    record Marks(Set<Integer> tunnels, Set<Integer> laneEnds) {

        static final Marks NONE = new Marks(Set.of(), Set.of());

        Set<Integer> tunnelSides() {
            Set<Integer> sides = new TreeSet<>(tunnels);
            sides.addAll(laneEnds);
            return sides;
        }
    }

    static BufferedImage onSystem(
            String artKey, BufferedImage art, Shape occluders, BridgeMatrix lanes, Marks marks, HyperlaneStyle style) {
        return cached(
                key(artKey, lanes, style) + "|x" + marks.tunnels() + "|g" + marks.laneEnds(),
                () -> compose(art, occluders, lanes, marks, style, false));
    }

    static BufferedImage onUnexplored(
            String artKey, Supplier<BufferedImage> background, BridgeMatrix lanes, HyperlaneStyle style) {
        return cached(
                key("unexplored:" + artKey, lanes, style),
                () -> compose(background.get(), new Path2D.Double(), lanes, Marks.NONE, style, true));
    }

    private static BufferedImage cached(String key, Supplier<BufferedImage> compose) {
        BufferedImage image = CACHE.get(key);
        if (image != null) {
            return image;
        }
        BufferedImage composed = compose.get();
        BufferedImage raced = CACHE.putIfAbsent(key, composed);
        return raced == null ? composed : raced;
    }

    private static String key(String artKey, BridgeMatrix lanes, HyperlaneStyle style) {
        return artKey + '|' + style.name() + '|' + lanes.stored();
    }

    private static BufferedImage compose(
            BufferedImage art,
            Shape occluders,
            BridgeMatrix lanes,
            Marks marks,
            HyperlaneStyle style,
            boolean unexplored) {
        BridgeTrail.Trail trail = BridgeTrail.of(lanes, occluders);
        List<BridgeTrail.Piece> pieces = new ArrayList<>(trail.ring());
        pieces.addAll(trail.stubs());
        List<BridgeTrail.Tunnel> tunnels = trail.tunnelsAt(marks.tunnelSides());
        BufferedImage image = new BufferedImage(TILE_WIDTH, TILE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(art, 0, 0, null);
        g.drawImage(lanes(pieces, tunnels, style), 0, 0, null);
        if (!unexplored) {
            g.setClip(occluders);
            g.drawImage(art, 0, 0, null);
            g.setClip(null);
        } else {
            unexploredPing(g);
        }
        g.drawImage(markers(lanes.ends(), trail, tunnels, marks.laneEnds()), 0, 0, null);
        g.dispose();
        return image;
    }

    private static BufferedImage lanes(
            List<BridgeTrail.Piece> pieces, List<BridgeTrail.Tunnel> tunnels, HyperlaneStyle style) {
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
        for (BridgeTrail.Tunnel tunnel : tunnels) cutUnder(g, tunnel);
        g.dispose();
        return image;
    }

    private static void cutUnder(Graphics2D g, BridgeTrail.Tunnel tunnel) {
        Graphics2D cut = alongEdge(g, tunnel, 0);
        cut.setComposite(AlphaComposite.Clear);
        cut.fill(new Rectangle2D.Double(
                -TUNNEL_HALF_LENGTH, -TUNNEL_HALF_WIDTH, 2 * TUNNEL_HALF_LENGTH, 2 * TUNNEL_HALF_WIDTH));
        cut.dispose();
    }

    private static void tunnelMouths(Graphics2D g, BridgeTrail.Tunnel tunnel) {
        for (int direction : new int[] {-1, 1}) {
            Graphics2D mouth = alongEdge(g, tunnel, TUNNEL_MOUTH_OFFSET * direction);
            Arc2D arc = new Arc2D.Double(-6, -9, 12, 18, direction > 0 ? 90 : -90, 180, Arc2D.OPEN);
            mouth.setStroke(new BasicStroke(4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            mouth.setColor(OUTLINE);
            mouth.draw(arc);
            mouth.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            mouth.setColor(tunnel.oneWay() ? AMBER_CORE : BLUE_CORE);
            mouth.draw(arc);
            mouth.dispose();
        }
    }

    private static Graphics2D alongEdge(Graphics2D g, BridgeTrail.Tunnel tunnel, double offset) {
        Graphics2D local = (Graphics2D) g.create();
        local.translate(tunnel.at().getX(), tunnel.at().getY());
        local.rotate(tunnel.edgeAngle());
        local.translate(offset, 0);
        return local;
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

    private static BufferedImage markers(
            List<BridgeEnd> ends, BridgeTrail.Trail trail, List<BridgeTrail.Tunnel> tunnels, Set<Integer> laneEnds) {
        BufferedImage image = new BufferedImage(TILE_WIDTH, TILE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setClip(LaneInk.hexOutline());
        g.setComposite(AlphaComposite.SrcOver);
        for (BridgeTrail.Arrow arrow : trail.cornerArrows()) {
            LaneInk.arrowHead(g, new double[] {arrow.x(), arrow.y(), arrow.angle()}, ARROW);
        }
        for (BridgeTrail.Tunnel tunnel : tunnels) {
            tunnelMouths(g, tunnel);
            if (laneEnds.contains(tunnel.side())) {
                LaneInk.drawNode(
                        g,
                        BridgeTrail.edgeMidpoint(tunnel.side()),
                        LANE_END_GLOW_RADIUS,
                        LANE_END_GLOW_CORE,
                        LANE_END_GLOW_HALO);
            }
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
