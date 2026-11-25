package ti4.image.tile;

import static ti4.image.tile.TileGeometry.TILE_HEIGHT;
import static ti4.image.tile.TileGeometry.TILE_WIDTH;

import java.awt.Shape;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import ti4.service.map.BridgeEnd;
import ti4.service.map.BridgeMatrix;

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
    private static final double ARROW_CLEARANCE = 9;
    private static final int ARROW_SEARCH_STEPS = 8;

    private static final List<Point2D> RING = ringWithSideNAfterCornerN();

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

    record Tunnel(int side, Point2D at, double edgeAngle, boolean oneWay) {}

    record Trail(List<Piece> ring, List<Piece> stubs, List<Arrow> cornerArrows) {

        List<Tunnel> tunnelsAt(Collection<Integer> crossingSides) {
            List<Tunnel> tunnels = new ArrayList<>();
            for (int side : crossingSides) {
                Point2D midpoint = RING.get(ringIndex(side));
                ring.stream()
                        .filter(piece -> piece.points().contains(midpoint))
                        .findFirst()
                        .ifPresent(piece ->
                                tunnels.add(new Tunnel(side, midpoint, Math.toRadians(side * 60), piece.isOneWay())));
            }
            return List.copyOf(tunnels);
        }
    }

    static Trail of(BridgeMatrix lanes, Shape occluders) {
        int segments = RING.size();
        boolean[] clockwise = new boolean[segments];
        boolean[] counterClockwise = new boolean[segments];
        List<BridgeMatrix.Pair> pairs = lanes.pairs();
        List<Boolean> layout = cheapestLayout(lanes, pairs, occluders);
        for (int i = 0; i < pairs.size(); i++) {
            route(lanes, pairs.get(i), layout.get(i), clockwise, counterClockwise);
        }
        List<Piece> ring = runs(clockwise, counterClockwise);
        List<Piece> stubs = new ArrayList<>();
        for (BridgeEnd end : lanes.ends()) {
            stubs.add(new Piece(
                    List.of(edgeMidpoint(end.side()), RING.get(ringIndex(end.side()))), end.entry(), end.exit()));
        }
        return new Trail(ring, List.copyOf(stubs), cornerArrows(ring, occluders));
    }

    private static List<Boolean> cheapestLayout(BridgeMatrix lanes, List<BridgeMatrix.Pair> pairs, Shape occluders) {
        List<List<Boolean>> layouts = lanes.rimLayoutsWithoutSharing();
        if (layouts.isEmpty()) {
            List<Boolean> eachOnItsOwn = new ArrayList<>();
            for (BridgeMatrix.Pair pair : pairs)
                eachOnItsOwn.add(arcCost(pair, true, occluders) <= arcCost(pair, false, occluders));
            return eachOnItsOwn;
        }
        List<Boolean> best = layouts.getFirst();
        double bestCost = Double.MAX_VALUE;
        for (List<Boolean> layout : layouts) {
            double total = 0;
            for (int i = 0; i < pairs.size(); i++) total += arcCost(pairs.get(i), layout.get(i), occluders);
            if (total < bestCost) {
                bestCost = total;
                best = layout;
            }
        }
        return best;
    }

    private static double arcCost(BridgeMatrix.Pair pair, boolean clockwiseFromLow, Shape occluders) {
        return clockwiseFromLow
                ? cost(arc(pair.low(), pair.high()), occluders)
                : cost(arc(pair.high(), pair.low()), occluders);
    }

    private static void route(
            BridgeMatrix lanes,
            BridgeMatrix.Pair pair,
            boolean clockwiseFromLow,
            boolean[] clockwise,
            boolean[] counterClockwise) {
        int from = clockwiseFromLow ? pair.low() : pair.high(), to = clockwiseFromLow ? pair.high() : pair.low();
        boolean along = lanes.leads(from, to), against = lanes.leads(to, from);
        for (int k = ringIndex(from); k != ringIndex(to); k = (k + 1) % RING.size()) {
            clockwise[k] |= along;
            counterClockwise[k] |= against;
        }
    }

    private static List<Piece> runs(boolean[] clockwise, boolean[] counterClockwise) {
        int segments = RING.size();
        int start = -1;
        for (int k = 0; k < segments; k++) {
            int previous = (k + segments - 1) % segments;
            if (clockwise[k] != clockwise[previous] || counterClockwise[k] != counterClockwise[previous]) {
                start = k;
                break;
            }
        }
        List<Piece> pieces = new ArrayList<>();
        if (start < 0) {
            if (clockwise[0] || counterClockwise[0]) pieces.add(piece(0, segments, clockwise[0], counterClockwise[0]));
            return pieces;
        }
        int runStart = start;
        for (int step = 1; step <= segments; step++) {
            int k = (start + step) % segments;
            int last = (k + segments - 1) % segments;
            boolean changes = step == segments
                    || clockwise[k] != clockwise[last]
                    || counterClockwise[k] != counterClockwise[last];
            if (!changes) continue;
            if (clockwise[last] || counterClockwise[last]) {
                int length = (k - runStart + segments) % segments;
                pieces.add(piece(runStart, length == 0 ? segments : length, clockwise[last], counterClockwise[last]));
            }
            runStart = k;
        }
        return pieces;
    }

    private static Piece piece(int first, int segmentCount, boolean forward, boolean backward) {
        List<Point2D> points = new ArrayList<>();
        for (int k = 0; k <= segmentCount; k++) points.add(RING.get((first + k) % RING.size()));
        return new Piece(List.copyOf(points), forward, backward);
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

    private static List<Arrow> cornerArrows(List<Piece> pieces, Shape occluders) {
        List<Arrow> arrows = new ArrayList<>();
        for (Piece piece : pieces) {
            if (!piece.isOneWay()) continue;
            List<Point2D> points = piece.inTravelDirection();
            for (int k = 1; k < points.size() - 1; k++) {
                if (!isCornerApex(points.get(k))) continue;
                int spot = clearSpotNear(points, k, occluders);
                if (spot < 0) continue;
                Point2D before = points.get(spot - 1), after = points.get(spot + 1);
                arrows.add(new Arrow(
                        points.get(spot).getX(),
                        points.get(spot).getY(),
                        Math.atan2(after.getY() - before.getY(), after.getX() - before.getX())));
            }
        }
        return List.copyOf(arrows);
    }

    private static int clearSpotNear(List<Point2D> points, int apex, Shape occluders) {
        for (int step = 0; step <= ARROW_SEARCH_STEPS; step++) {
            for (int candidate : new int[] {apex + step, apex - step}) {
                if (candidate > 0 && candidate < points.size() - 1 && isClear(points.get(candidate), occluders)) {
                    return candidate;
                }
            }
        }
        return -1;
    }

    private static boolean isClear(Point2D point, Shape occluders) {
        return !occluders.intersects(
                point.getX() - ARROW_CLEARANCE,
                point.getY() - ARROW_CLEARANCE,
                2 * ARROW_CLEARANCE,
                2 * ARROW_CLEARANCE);
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

    private static List<Point2D> ringWithSideNAfterCornerN() {
        double scale = (EDGE_DISTANCE - INSET) / EDGE_DISTANCE;
        List<Point2D> corners = new ArrayList<>();
        for (double[] corner : hexCornersClockwiseFromTopLeft()) {
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

    private static List<double[]> hexCornersClockwiseFromTopLeft() {
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
