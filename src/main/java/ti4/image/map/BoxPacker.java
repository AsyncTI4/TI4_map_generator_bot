package ti4.image.map;

import java.awt.Dimension;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

final class BoxPacker {

    private static final List<Double> WIDTH_FACTORS = List.of(0.9, 1.0, 1.1, 1.25, 1.5, 1.75, 2.0);

    record Packing(List<Point> positions, int width, int height) {

        long score() {
            return (long) Math.max(width, height) * 1_000_000L + (long) width * height / 1_000L;
        }
    }

    private record Segment(int x, int y, int width) {}

    private BoxPacker() {}

    static Packing pack(List<Dimension> sizes, int gap) {
        List<Dimension> padded = sizes.stream()
                .map(size -> new Dimension(size.width + gap, size.height + gap))
                .toList();
        int widest = padded.stream().mapToInt(size -> size.width).max().orElse(gap);
        long area = padded.stream()
                .mapToLong(size -> (long) size.width * size.height)
                .sum();
        Packing best = null;
        for (double factor : WIDTH_FACTORS) {
            int binWidth = Math.max(widest, (int) (Math.sqrt(area) * factor));
            Packing packing = packInto(padded, binWidth, gap);
            if (best == null || packing.score() < best.score()) {
                best = packing;
            }
        }
        return best == null ? new Packing(List.of(), 0, 0) : best;
    }

    private static Packing packInto(List<Dimension> sizes, int binWidth, int gap) {
        List<Segment> skyline = new ArrayList<>(List.of(new Segment(0, 0, binWidth)));
        List<Point> positions = new ArrayList<>();
        int usedWidth = 0;
        int usedHeight = 0;
        for (Dimension size : sizes) {
            Point spot = lowestSpot(skyline, size, binWidth);
            positions.add(spot);
            raise(skyline, spot.x, spot.y + size.height, size.width);
            usedWidth = Math.max(usedWidth, spot.x + size.width);
            usedHeight = Math.max(usedHeight, spot.y + size.height);
        }
        return new Packing(positions, Math.max(0, usedWidth - gap), Math.max(0, usedHeight - gap));
    }

    private static Point lowestSpot(List<Segment> skyline, Dimension size, int binWidth) {
        Point best = null;
        for (Segment segment : skyline) {
            int x = segment.x();
            if (x + size.width > binWidth) {
                continue;
            }
            int y = topOver(skyline, x, size.width);
            if (best == null || y < best.y || (y == best.y && x < best.x)) {
                best = new Point(x, y);
            }
        }
        return best == null ? new Point(0, topOver(skyline, 0, size.width)) : best;
    }

    private static int topOver(List<Segment> skyline, int x, int width) {
        int top = 0;
        for (Segment segment : skyline) {
            if (segment.x() < x + width && segment.x() + segment.width() > x) {
                top = Math.max(top, segment.y());
            }
        }
        return top;
    }

    private static void raise(List<Segment> skyline, int x, int y, int width) {
        List<Segment> raised = new ArrayList<>();
        for (Segment segment : skyline) {
            int end = segment.x() + segment.width();
            if (end <= x || segment.x() >= x + width) {
                raised.add(segment);
                continue;
            }
            if (segment.x() < x) {
                raised.add(new Segment(segment.x(), segment.y(), x - segment.x()));
            }
            if (end > x + width) {
                raised.add(new Segment(x + width, segment.y(), end - x - width));
            }
        }
        raised.add(new Segment(x, y, width));
        raised.sort((a, b) -> Integer.compare(a.x(), b.x()));
        skyline.clear();
        for (Segment segment : raised) {
            Segment last = skyline.isEmpty() ? null : skyline.getLast();
            if (last != null && last.y() == segment.y() && last.x() + last.width() == segment.x()) {
                skyline.set(skyline.size() - 1, new Segment(last.x(), last.y(), last.width() + segment.width()));
            } else {
                skyline.add(segment);
            }
        }
    }
}
