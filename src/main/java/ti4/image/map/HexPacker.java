package ti4.image.map;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

final class HexPacker {

    static final int GUTTER = 36;
    static final int HEX_HALF_WIDTH = 173;
    static final int HEX_HALF_HEIGHT = 150;
    private static final double COS_30 = Math.sqrt(3) / 2;
    private static final double TARGET_ASPECT = 1 / 1.2;
    private static final double DIRECTION_WEIGHT = 0.15;
    private static final int BUCKET = 400;
    private static final Comparator<Point> TOP_FIRST =
            Comparator.comparingInt((Point point) -> point.y).thenComparingInt(point -> point.x);
    private static final List<Double> EDGE_FRACTIONS = List.of(0.0, 0.25, 0.5, 0.75);
    private static final List<Point> CONTACT_POINTS = contactPoints();
    static final List<Point> NEIGHBOUR_OFFSETS = List.of(
            new Point(0, -2 * HEX_HALF_HEIGHT),
            new Point(FocusIslands.COLUMN_STEP, -FocusIslands.HALF_ROW_STEP),
            new Point(FocusIslands.COLUMN_STEP, FocusIslands.HALF_ROW_STEP),
            new Point(0, 2 * HEX_HALF_HEIGHT),
            new Point(-FocusIslands.COLUMN_STEP, FocusIslands.HALF_ROW_STEP),
            new Point(-FocusIslands.COLUMN_STEP, -FocusIslands.HALF_ROW_STEP));

    record Shape(List<Point> centres, List<Rectangle> labels, List<Point> footprint) {

        Shape {
            centres = List.copyOf(centres);
            labels = labels.stream().map(Rectangle::new).toList();
            footprint = List.copyOf(footprint);
        }

        Shape(List<Point> centres, List<Rectangle> labels) {
            this(centres, labels, withEnclosedHoles(centres));
        }

        Rectangle hexBounds() {
            Rectangle bounds = null;
            for (Point centre : centres) {
                Rectangle hex = hexBox(centre);
                bounds = bounds == null ? hex : bounds.union(hex);
            }
            return bounds == null ? new Rectangle() : bounds;
        }

        Rectangle bounds(@Nullable Rectangle label) {
            Rectangle bounds = hexBounds();
            return label == null ? bounds : bounds.union(label);
        }

        List<Point> boundaryCentres() {
            Set<Point> all = new HashSet<>(footprint);
            return footprint.stream()
                    .filter(centre -> NEIGHBOUR_OFFSETS.stream()
                            .anyMatch(step -> !all.contains(new Point(centre.x + step.x, centre.y + step.y))))
                    .toList();
        }

        @Nullable
        Rectangle label(int index) {
            return labels.isEmpty() ? null : labels.get(index);
        }

        int labelChoices() {
            return Math.max(1, labels.size());
        }
    }

    record Placement(Point offset, @Nullable Rectangle label) {}

    private record Candidate(Point offset, int label, double score, double spread) {

        boolean beats(@Nullable Candidate other) {
            if (other == null) {
                return true;
            }
            if (score != other.score) {
                return score < other.score;
            }
            if (spread != other.spread) {
                return spread < other.spread;
            }
            if (offset.y != other.offset.y) {
                return offset.y < other.offset.y;
            }
            if (offset.x != other.offset.x) {
                return offset.x < other.offset.x;
            }
            return label < other.label;
        }
    }

    private final Map<Long, List<Point>> placedHexes = new HashMap<>();
    private final List<Point> placedBoundary = new ArrayList<>();
    private final List<Rectangle> placedLabels = new ArrayList<>();
    private Rectangle placedBounds;
    private long centreSumX;
    private long centreSumY;
    private int centreCount;
    private Point anchor;

    private HexPacker() {}

    static List<Placement> pack(List<Shape> shapes) {
        HexPacker packer = new HexPacker();
        List<Placement> placements = new ArrayList<>();
        for (Shape shape : shapes) {
            Placement placement = packer.placedBounds == null ? packer.first(shape) : packer.best(shape);
            packer.place(shape, placement);
            placements.add(placement);
        }
        return placements;
    }

    static double separation(int dx, int dy) {
        int absY = Math.abs(dy);
        return Math.max(absY, COS_30 * Math.abs(dx) + absY / 2.0);
    }

    static boolean hexesClear(Point a, Point b, int gap) {
        return separation(a.x - b.x, a.y - b.y) >= 2 * HEX_HALF_HEIGHT + gap;
    }

    static Rectangle hexBox(Point centre) {
        return new Rectangle(
                centre.x - HEX_HALF_WIDTH, centre.y - HEX_HALF_HEIGHT, 2 * HEX_HALF_WIDTH, 2 * HEX_HALF_HEIGHT);
    }

    static boolean rectanglesClear(Rectangle a, Rectangle b, int gap) {
        return !new Rectangle(a.x - gap, a.y - gap, a.width + 2 * gap, a.height + 2 * gap).intersects(b);
    }

    private Placement first(Shape shape) {
        anchor = centreOf(shape.centres());
        return placement(shape, new Point(0, 0), 0);
    }

    private Placement best(Shape shape) {
        Point localCentre = centreOf(shape.centres());
        Point packedCentre = new Point((int) (centreSumX / centreCount), (int) (centreSumY / centreCount));
        Point wanted = new Point(localCentre.x - anchor.x, localCentre.y - anchor.y);
        List<Point> ownBoundary = shape.boundaryCentres();
        Set<Point> tried = new HashSet<>();
        Map<Point, Boolean> hexesFit = new HashMap<>();
        Candidate best = null;
        for (Point placed : placedBoundary) {
            for (Point contact : CONTACT_POINTS) {
                for (Point own : ownBoundary) {
                    Point offset = new Point(placed.x + contact.x - own.x, placed.y + contact.y - own.y);
                    if (!tried.add(offset)) {
                        continue;
                    }
                    double spread = packedCentre.distance(localCentre.x + offset.x, localCentre.y + offset.y);
                    double direction = directionPenalty(wanted, new Point(wanted.x + offset.x, wanted.y + offset.y));
                    for (int label = 0; label < shape.labelChoices(); label++) {
                        Rectangle moved = shape.bounds(shape.label(label));
                        moved.translate(offset.x, offset.y);
                        Candidate candidate =
                                new Candidate(offset, label, score(placedBounds.union(moved)) * direction, spread);
                        if (candidate.beats(best) && fits(shape, offset, label, hexesFit)) {
                            best = candidate;
                        }
                    }
                }
            }
        }
        if (best != null) {
            return placement(shape, best.offset(), best.label());
        }
        Rectangle localBounds = shape.bounds(shape.label(0));
        return placement(
                shape,
                new Point(placedBounds.x + placedBounds.width + GUTTER - localBounds.x, placedBounds.y - localBounds.y),
                0);
    }

    private static Placement placement(Shape shape, Point offset, int label) {
        Rectangle chosen = shape.label(label);
        if (chosen == null) {
            return new Placement(offset, null);
        }
        Rectangle moved = new Rectangle(chosen);
        moved.translate(offset.x, offset.y);
        return new Placement(offset, moved);
    }

    private static double directionPenalty(Point wanted, Point actual) {
        double wantedLength = Math.hypot(wanted.x, wanted.y);
        double actualLength = Math.hypot(actual.x, actual.y);
        if (wantedLength < 1 || actualLength < 1) {
            return 1;
        }
        double cos = (wanted.x * (double) actual.x + wanted.y * (double) actual.y) / (wantedLength * actualLength);
        return 1 + DIRECTION_WEIGHT * (1 - cos) / 2;
    }

    private static List<Point> contactPoints() {
        double reach = (2 * HEX_HALF_HEIGHT + GUTTER + 1) / COS_30;
        List<Point> points = new ArrayList<>();
        for (int corner = 0; corner < 6; corner++) {
            double fromAngle = Math.toRadians(60 * corner);
            double toAngle = Math.toRadians(60 * (corner + 1));
            for (double fraction : EDGE_FRACTIONS) {
                double x = reach * (Math.cos(fromAngle) + fraction * (Math.cos(toAngle) - Math.cos(fromAngle)));
                double y = reach * (Math.sin(fromAngle) + fraction * (Math.sin(toAngle) - Math.sin(fromAngle)));
                points.add(new Point((int) Math.round(x), (int) Math.round(y)));
            }
        }
        return List.copyOf(points);
    }

    private static double score(Rectangle bounds) {
        double ratio = (bounds.width / (double) Math.max(1, bounds.height)) / TARGET_ASPECT;
        return (double) bounds.width * bounds.height * Math.sqrt(Math.max(ratio, 1 / ratio));
    }

    private boolean fits(Shape shape, Point offset, int labelIndex, Map<Point, Boolean> hexesFit) {
        if (!hexesFit.computeIfAbsent(offset, key -> hexesFit(shape, key))) {
            return false;
        }
        Rectangle label = placement(shape, offset, labelIndex).label();
        return label == null || (clearOfPlacedLabels(label) && labelClearOfPlacedHexes(label));
    }

    private boolean hexesFit(Shape shape, Point offset) {
        for (Point local : shape.footprint()) {
            Point centre = new Point(local.x + offset.x, local.y + offset.y);
            if (!clearOfPlacedHexes(centre) || !clearOfPlacedLabels(hexBox(centre))) {
                return false;
            }
        }
        return true;
    }

    private boolean clearOfPlacedHexes(Point centre) {
        int bucketX = Math.floorDiv(centre.x, BUCKET);
        int bucketY = Math.floorDiv(centre.y, BUCKET);
        for (int x = bucketX - 1; x <= bucketX + 1; x++) {
            for (int y = bucketY - 1; y <= bucketY + 1; y++) {
                for (Point placed : placedHexes.getOrDefault(key(x, y), List.of())) {
                    if (!hexesClear(placed, centre, GUTTER)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean labelClearOfPlacedHexes(Rectangle label) {
        int fromX = Math.floorDiv(label.x, BUCKET) - 1;
        int toX = Math.floorDiv(label.x + label.width, BUCKET) + 1;
        int fromY = Math.floorDiv(label.y, BUCKET) - 1;
        int toY = Math.floorDiv(label.y + label.height, BUCKET) + 1;
        for (int x = fromX; x <= toX; x++) {
            for (int y = fromY; y <= toY; y++) {
                for (Point placed : placedHexes.getOrDefault(key(x, y), List.of())) {
                    if (!rectanglesClear(label, hexBox(placed), GUTTER)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean clearOfPlacedLabels(Rectangle box) {
        return placedLabels.stream().allMatch(label -> rectanglesClear(label, box, GUTTER));
    }

    private void place(Shape shape, Placement placement) {
        Point offset = placement.offset();
        for (Point local : shape.footprint()) {
            Point centre = new Point(local.x + offset.x, local.y + offset.y);
            placedHexes
                    .computeIfAbsent(
                            key(Math.floorDiv(centre.x, BUCKET), Math.floorDiv(centre.y, BUCKET)),
                            k -> new ArrayList<>())
                    .add(centre);
        }
        for (Point local : shape.centres()) {
            Point centre = new Point(local.x + offset.x, local.y + offset.y);
            centreSumX += centre.x;
            centreSumY += centre.y;
            centreCount++;
        }
        for (Point local : shape.boundaryCentres()) {
            placedBoundary.add(new Point(local.x + offset.x, local.y + offset.y));
        }
        Rectangle moved = shape.hexBounds();
        moved.translate(offset.x, offset.y);
        if (placement.label() != null) {
            placedLabels.add(placement.label());
            moved = moved.union(placement.label());
        }
        placedBounds = placedBounds == null ? moved : placedBounds.union(moved);
    }

    static List<Point> withEnclosedHoles(List<Point> centres) {
        if (centres.isEmpty()) {
            return List.of();
        }
        Set<Point> occupied = new HashSet<>(centres);
        Rectangle area = latticeArea(centres);
        Point top = centres.stream().min(TOP_FIRST).orElseThrow();
        Set<Point> outside = flood(new Point(top.x, area.y), occupied, area);
        List<Point> footprint = new ArrayList<>(centres);
        Set<Point> holes = new HashSet<>();
        for (Point centre : centres) {
            for (Point step : NEIGHBOUR_OFFSETS) {
                Point next = new Point(centre.x + step.x, centre.y + step.y);
                if (!occupied.contains(next) && !outside.contains(next) && !holes.contains(next)) {
                    holes.addAll(flood(next, occupied, area));
                }
            }
        }
        holes.stream().sorted(TOP_FIRST).forEach(footprint::add);
        return footprint;
    }

    private static Rectangle latticeArea(List<Point> centres) {
        int minX = centres.stream().mapToInt(point -> point.x).min().orElse(0);
        int maxX = centres.stream().mapToInt(point -> point.x).max().orElse(0);
        int minY = centres.stream().mapToInt(point -> point.y).min().orElse(0);
        int maxY = centres.stream().mapToInt(point -> point.y).max().orElse(0);
        int marginX = 2 * FocusIslands.COLUMN_STEP;
        int marginY = 4 * HEX_HALF_HEIGHT;
        return new Rectangle(minX - marginX, minY - marginY, maxX - minX + 2 * marginX, maxY - minY + 2 * marginY);
    }

    private static Set<Point> flood(Point start, Set<Point> occupied, Rectangle area) {
        Set<Point> reached = new HashSet<>();
        ArrayDeque<Point> queue = new ArrayDeque<>();
        reached.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            Point point = queue.poll();
            for (Point step : NEIGHBOUR_OFFSETS) {
                Point next = new Point(point.x + step.x, point.y + step.y);
                if (inside(area, next) && !occupied.contains(next) && reached.add(next)) {
                    queue.add(next);
                }
            }
        }
        return reached;
    }

    private static boolean inside(Rectangle area, Point point) {
        return point.x >= area.x
                && point.x <= area.x + area.width
                && point.y >= area.y
                && point.y <= area.y + area.height;
    }

    private static Point centreOf(List<Point> centres) {
        long x = 0;
        long y = 0;
        for (Point centre : centres) {
            x += centre.x;
            y += centre.y;
        }
        int count = Math.max(1, centres.size());
        return new Point((int) (x / count), (int) (y / count));
    }

    private static long key(int x, int y) {
        return ((long) x << 32) ^ (y & 0xffffffffL);
    }
}
