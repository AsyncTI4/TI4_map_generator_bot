package ti4.image.tile;

import static ti4.image.tile.TileGeometry.TILE_HEIGHT;
import static ti4.image.tile.TileGeometry.TILE_WIDTH;

import java.awt.Shape;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import ti4.service.map.HyperlaneBridgeEnds.BridgeEnd;

final class BridgeTrail {

    static final double INSET = 8;
    private static final double CORNER_ROUNDING = 26;
    private static final int CORNER_POINTS = 9;
    private static final int EDGE_POINTS = 15;
    private static final int POINTS_PER_SIDE = CORNER_POINTS + EDGE_POINTS;
    private static final double CENTER_X = TILE_WIDTH / 2.0;
    private static final double CENTER_Y = TILE_HEIGHT / 2.0;
    private static final double EDGE_DISTANCE = TILE_HEIGHT / 2.0;
    private static final double OCCLUDED_COST = 30;

    private static final List<Point2D> RING = ring();

    private BridgeTrail() {}

    record Piece(List<Point2D> points, boolean forward, boolean backward) {

        boolean isOneWay() {
            return forward != backward;
        }

        List<Point2D> inTravelDirection() {
            if (!backward || forward) return points;
            List<Point2D> reversed = new ArrayList<>(points);
            Collections.reverse(reversed);
            return reversed;
        }
    }

    record Arrow(double x, double y, double angle) {}

    record Trail(List<Piece> ring, List<Piece> stubs, List<Arrow> cornerArrows) {}

    static Trail of(List<BridgeEnd> ends, Shape occluders) {
        List<BridgeEnd> byRing = new ArrayList<>(ends);
        byRing.sort(Comparator.comparingInt(end -> ringIndex(end.side())));
        List<Piece> best = List.of();
        double bestCost = Double.MAX_VALUE;
        for (int dropped = 0; dropped < byRing.size(); dropped++) {
            List<Piece> pieces = piecesWithoutGapAfter(byRing, dropped);
            double cost = pieces.stream()
                    .mapToDouble(piece -> cost(piece.points(), occluders))
                    .sum();
            if (cost < bestCost) {
                bestCost = cost;
                best = pieces;
            }
        }
        List<Piece> stubs = new ArrayList<>();
        for (BridgeEnd end : ends) {
            stubs.add(new Piece(
                    List.of(edgeMidpoint(end.side()), RING.get(ringIndex(end.side()))), end.entry(), end.exit()));
        }
        return new Trail(best, List.copyOf(stubs), cornerArrows(best));
    }

    private static List<Piece> piecesWithoutGapAfter(List<BridgeEnd> byRing, int dropped) {
        int count = byRing.size();
        List<BridgeEnd> order = new ArrayList<>();
        for (int k = 1; k <= count; k++) order.add(byRing.get((dropped + k) % count));
        List<Piece> pieces = new ArrayList<>();
        for (int k = 0; k < count - 1; k++) {
            boolean forward = anyEntry(order, 0, k) && anyExit(order, k + 1, count - 1);
            boolean backward = anyEntry(order, k + 1, count - 1) && anyExit(order, 0, k);
            if (!forward && !backward) continue;
            pieces.add(new Piece(arc(order.get(k).side(), order.get(k + 1).side()), forward, backward));
        }
        return pieces;
    }

    private static boolean anyEntry(List<BridgeEnd> order, int from, int to) {
        return order.subList(from, to + 1).stream().anyMatch(BridgeEnd::entry);
    }

    private static boolean anyExit(List<BridgeEnd> order, int from, int to) {
        return order.subList(from, to + 1).stream().anyMatch(BridgeEnd::exit);
    }

    private static List<Point2D> arc(int fromSide, int toSide) {
        List<Point2D> points = new ArrayList<>();
        int end = ringIndex(toSide);
        for (int i = ringIndex(fromSide); ; i = (i + 1) % RING.size()) {
            points.add(RING.get(i));
            if (i == end) break;
        }
        return List.copyOf(points);
    }

    private static double cost(List<Point2D> points, Shape occluders) {
        double total = 0;
        for (int i = 1; i < points.size(); i++) {
            Point2D point = points.get(i);
            double weight = occluders.contains(point) ? OCCLUDED_COST : 1;
            total += weight * point.distance(points.get(i - 1));
        }
        return total;
    }

    private static List<Arrow> cornerArrows(List<Piece> pieces) {
        List<Arrow> arrows = new ArrayList<>();
        for (Piece piece : pieces) {
            if (!piece.isOneWay()) continue;
            List<Point2D> points = piece.inTravelDirection();
            for (int k = 1; k < points.size() - 1; k++) {
                if (!isCornerApex(points.get(k))) continue;
                Point2D before = points.get(k - 1), after = points.get(k + 1);
                arrows.add(new Arrow(
                        points.get(k).getX(),
                        points.get(k).getY(),
                        Math.atan2(after.getY() - before.getY(), after.getX() - before.getX())));
            }
        }
        return List.copyOf(arrows);
    }

    private static boolean isCornerApex(Point2D point) {
        for (int corner = 0; corner < 6; corner++) {
            if (RING.get(corner * POINTS_PER_SIDE + CORNER_POINTS / 2).equals(point)) return true;
        }
        return false;
    }

    static int ringIndex(int side) {
        return side * POINTS_PER_SIDE + CORNER_POINTS + EDGE_POINTS / 2;
    }

    static Point2D edgeMidpoint(int side) {
        double angle = Math.toRadians(side * 60);
        return new Point2D.Double(
                CENTER_X + EDGE_DISTANCE * Math.sin(angle), CENTER_Y - EDGE_DISTANCE * Math.cos(angle));
    }

    // Hex corners from the top-left one clockwise; side n is the edge after corner n.
    private static List<Point2D> ring() {
        double scale = (EDGE_DISTANCE - INSET) / EDGE_DISTANCE;
        List<Point2D> corners = new ArrayList<>();
        for (double[] corner : hexCorners()) {
            corners.add(new Point2D.Double(
                    CENTER_X + (corner[0] - CENTER_X) * scale, CENTER_Y + (corner[1] - CENTER_Y) * scale));
        }
        List<Point2D> points = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Point2D previous = corners.get((i + 5) % 6), corner = corners.get(i), next = corners.get((i + 1) % 6);
            Point2D in = toward(corner, previous, CORNER_ROUNDING), out = toward(corner, next, CORNER_ROUNDING);
            for (int k = 0; k < CORNER_POINTS; k++) {
                double t = k / (double) (CORNER_POINTS - 1), u = 1 - t;
                points.add(new Point2D.Double(
                        u * u * in.getX() + 2 * u * t * corner.getX() + t * t * out.getX(),
                        u * u * in.getY() + 2 * u * t * corner.getY() + t * t * out.getY()));
            }
            Point2D nextIn = toward(next, corner, CORNER_ROUNDING);
            for (int k = 1; k <= EDGE_POINTS; k++) {
                double t = k / (double) (EDGE_POINTS + 1);
                points.add(new Point2D.Double(
                        out.getX() + (nextIn.getX() - out.getX()) * t, out.getY() + (nextIn.getY() - out.getY()) * t));
            }
        }
        return List.copyOf(points);
    }

    private static List<double[]> hexCorners() {
        return List.of(
                new double[] {TILE_WIDTH / 4.0, 0},
                new double[] {TILE_WIDTH * 3 / 4.0, 0},
                new double[] {TILE_WIDTH, CENTER_Y},
                new double[] {TILE_WIDTH * 3 / 4.0, TILE_HEIGHT},
                new double[] {TILE_WIDTH / 4.0, TILE_HEIGHT},
                new double[] {0, CENTER_Y});
    }

    private static Point2D toward(Point2D from, Point2D to, double distance) {
        double length = from.distance(to);
        return new Point2D.Double(
                from.getX() + (to.getX() - from.getX()) / length * distance,
                from.getY() + (to.getY() - from.getY()) / length * distance);
    }
}
