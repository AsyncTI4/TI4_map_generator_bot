package ti4.image.map;

import java.awt.Point;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

final class HexReach {

    private static final double NUDGE = 1e-6;

    private HexReach() {}

    static int distance(Point from, Point to) {
        int columns = Math.abs(columnsBetween(from, to));
        int halfRows = Math.abs(halfRowsBetween(from, to));
        return columns + Math.max(0, (halfRows - columns) / 2);
    }

    static List<Map<String, Point>> parts(Map<String, Point> centres, int reach) {
        List<String> positions = new ArrayList<>(centres.keySet());
        Set<String> seen = new HashSet<>();
        List<Map<String, Point>> parts = new ArrayList<>();
        for (String start : positions) {
            if (!seen.add(start)) {
                continue;
            }
            Map<String, Point> part = new TreeMap<>(MapFrame.POSITION_ORDER);
            ArrayDeque<String> queue = new ArrayDeque<>(List.of(start));
            while (!queue.isEmpty()) {
                String position = queue.poll();
                Point centre = centres.get(position);
                part.put(position, centre);
                for (String other : positions) {
                    if (!seen.contains(other) && distance(centre, centres.get(other)) <= reach) {
                        seen.add(other);
                        queue.add(other);
                    }
                }
            }
            parts.add(part);
        }
        parts.sort(Comparator.comparingInt((Map<String, Point> part) -> -part.size())
                .thenComparing(part -> part.keySet().iterator().next(), MapFrame.POSITION_ORDER));
        return parts;
    }

    static List<Point> withBridges(List<Point> centres, int reach) {
        if (reach == Integer.MAX_VALUE) {
            return List.copyOf(centres);
        }
        Set<Point> hexes = new LinkedHashSet<>(centres);
        for (int i = 0; i < centres.size(); i++) {
            for (int j = i + 1; j < centres.size(); j++) {
                int steps = distance(centres.get(i), centres.get(j));
                if (steps > 1 && steps <= reach) {
                    hexes.addAll(between(centres.get(i), centres.get(j), steps));
                }
            }
        }
        return List.copyOf(hexes);
    }

    private static List<Point> between(Point from, Point to, int steps) {
        int column = columnsBetween(from, to);
        int row = (halfRowsBetween(from, to) - column) / 2;
        double q = column + NUDGE;
        double r = row + NUDGE;
        double s = -column - row - 2 * NUDGE;
        List<Point> line = new ArrayList<>();
        for (int step = 1; step < steps; step++) {
            double t = step / (double) steps;
            int[] cube = roundCube(q * t, r * t, s * t);
            int halfRows = 2 * cube[1] + cube[0];
            line.add(new Point(
                    from.x + cube[0] * FocusIslands.COLUMN_STEP, from.y + halfRows * FocusIslands.HALF_ROW_STEP));
        }
        return line;
    }

    private static int[] roundCube(double q, double r, double s) {
        long roundedQ = Math.round(q);
        long roundedR = Math.round(r);
        long roundedS = Math.round(s);
        double driftQ = Math.abs(roundedQ - q);
        double driftR = Math.abs(roundedR - r);
        double driftS = Math.abs(roundedS - s);
        if (driftQ > driftR && driftQ > driftS) {
            roundedQ = -roundedR - roundedS;
        } else if (driftR > driftS) {
            roundedR = -roundedQ - roundedS;
        }
        return new int[] {(int) roundedQ, (int) roundedR};
    }

    private static int columnsBetween(Point from, Point to) {
        return Math.round((to.x - from.x) / (float) FocusIslands.COLUMN_STEP);
    }

    private static int halfRowsBetween(Point from, Point to) {
        return Math.round((to.y - from.y) / (float) FocusIslands.HALF_ROW_STEP);
    }
}
