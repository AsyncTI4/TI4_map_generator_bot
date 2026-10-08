package ti4.image;

import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import ti4.game.Game;

public record MapFrame(int offsetX, int offsetY, int width, int height) {

    static final int MAX_WIDTH = 5800;
    static final int MAX_HEIGHT = 6400;
    private static final Set<String> CORNER_POSITIONS = Set.of("tl", "tr", "bl", "br");
    static final Comparator<String> POSITION_ORDER =
            Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder());

    @Nullable
    static MapFrame around(
            Game game, Collection<String> positions, int fractureYbump, int padX, int padY, int minWidth) {
        Rectangle bounds = bounds(game, positions, fractureYbump, padX, padY);
        return bounds == null ? null : fit(bounds, minWidth, null);
    }

    @Nullable
    static Rectangle bounds(Game game, Collection<String> positions, int fractureYbump, int padX, int padY) {
        Rectangle hexes = null;
        for (String position : positions) {
            Rectangle hex = hexBounds(game, position, fractureYbump, padX, padY);
            if (hex == null) continue;
            hexes = hexes == null ? hex : hexes.union(hex);
        }
        if (hexes == null) return null;
        hexes.grow(padX, padY);
        return hexes;
    }

    @Nullable
    static Rectangle hexBounds(Game game, @Nullable String position, int fractureYbump, int padX, int padY) {
        if (position == null || CORNER_POSITIONS.contains(position.toLowerCase())) return null;
        Point raw = PositionMapper.getTilePosition(position);
        if (raw == null) return null;
        Point scaled = PositionMapper.getScaledTilePosition(game, position, raw.x, raw.y, fractureYbump);
        return new Rectangle(scaled.x + padX, scaled.y + padY, TileGenerator.TILE_WIDTH, TileGenerator.TILE_HEIGHT);
    }

    static Polygon hexPolygon(Rectangle hex) {
        int quarter = hex.width / 4;
        int[] xs = {
            hex.x + quarter,
            hex.x + hex.width - quarter,
            hex.x + hex.width,
            hex.x + hex.width - quarter,
            hex.x + quarter,
            hex.x
        };
        int[] ys = {hex.y, hex.y, hex.y + hex.height / 2, hex.y + hex.height, hex.y + hex.height, hex.y + hex.height / 2
        };
        return new Polygon(xs, ys, xs.length);
    }

    static boolean fitsCap(Rectangle bounds) {
        return bounds.width <= MAX_WIDTH && bounds.height <= MAX_HEIGHT;
    }

    static MapFrame fit(Rectangle bounds, int minWidth, @Nullable Rectangle keepVisible) {
        int width = Math.min(bounds.width, MAX_WIDTH);
        int height = Math.min(bounds.height, MAX_HEIGHT);
        Double focusX = keepVisible == null ? null : keepVisible.getCenterX();
        Double focusY = keepVisible == null ? null : keepVisible.getCenterY();
        int offsetX = slide(bounds.x, bounds.width, width, focusX);
        int offsetY = slide(bounds.y, bounds.height, height, focusY);
        int canvasWidth = Math.max(minWidth, width);
        return new MapFrame(offsetX - (canvasWidth - width) / 2, offsetY, canvasWidth, height);
    }

    private static int slide(int start, int length, int window, @Nullable Double focus) {
        if (window >= length) return start;
        int preferred = focus == null ? start + (length - window) / 2 : (int) Math.round(focus - window / 2.0);
        return Math.clamp(preferred, start, start + length - window);
    }

    static Set<String> cluster(Set<String> positions, String seed, int reach) {
        return cluster(positions, seed, reach, Map.of());
    }

    static Set<String> cluster(Set<String> positions, String seed, int reach, Map<String, Set<String>> links) {
        Set<String> cluster = new HashSet<>(Set.of(seed));
        Deque<String> frontier = new ArrayDeque<>(cluster);
        while (!frontier.isEmpty()) {
            String position = frontier.poll();
            Set<String> nearby = new HashSet<>(positionsWithin(position, reach));
            nearby.addAll(links.getOrDefault(position, Set.of()));
            for (String neighbour : nearby) {
                if (positions.contains(neighbour) && cluster.add(neighbour)) {
                    frontier.add(neighbour);
                }
            }
        }
        return cluster;
    }

    static List<Set<String>> clusters(Set<String> positions, int reach) {
        return clusters(positions, reach, Map.of());
    }

    static List<Set<String>> clusters(Set<String> positions, int reach, Map<String, Set<String>> links) {
        Set<String> unvisited = new HashSet<>(positions);
        List<Set<String>> clusters = new ArrayList<>();
        while (!unvisited.isEmpty()) {
            String seed = unvisited.stream().min(POSITION_ORDER).orElseThrow();
            Set<String> cluster = cluster(positions, seed, reach, links);
            unvisited.removeAll(cluster);
            clusters.add(cluster);
        }
        return clusters;
    }

    static Set<String> largestCluster(Set<String> positions, int reach, Map<String, Set<String>> links) {
        return clusters(positions, reach, links).stream()
                .max(Comparator.comparingInt(Set::size))
                .orElse(Set.of());
    }

    static Set<String> positionsWithin(String centre, int radius) {
        Set<String> reached = new HashSet<>(Set.of(centre));
        Deque<String> frontier = new ArrayDeque<>(reached);
        for (int step = 0; step < radius; step++) {
            Deque<String> next = new ArrayDeque<>();
            for (String position : frontier) {
                for (String adjacent : PositionMapper.getAdjacentTilePositions(position)) {
                    if (PositionMapper.getTilePosition(adjacent) != null && reached.add(adjacent)) {
                        next.add(adjacent);
                    }
                }
            }
            frontier = next;
        }
        return reached;
    }
}
