package ti4.image.map;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.annotation.Nullable;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.adjacency.Adjacency;
import ti4.model.MapPositionMapper;
import ti4.model.WormholeModel;

final class FocusIslands {

    static final int COLUMN_STEP = 260;
    static final int HALF_ROW_STEP = 150;
    static final String HOME_LETTER = "H";
    private static final int CLUSTER_DISTANCE = 2;
    private static final int MIN_SIDES_APART = 2;
    private static final String LETTERS = "ABCDEFGIJKLMNOPQRSTUVWXYZ";
    private static final List<Point> NEIGHBOUR_STEPS = List.of(
            new Point(0, -2), new Point(1, -1), new Point(1, 1), new Point(0, 2), new Point(-1, 1), new Point(-1, -1));

    record Island(
            String letter,
            String title,
            Map<String, Point> cells,
            Set<String> dimmed,
            Map<String, List<String>> badges) {

        boolean isLit(String position) {
            return cells.containsKey(position) && !dimmed.contains(position);
        }
    }

    private record Link(String from, String to) {}

    private FocusIslands() {}

    static List<Island> of(Game game, Player player, Set<String> litPositions) {
        Set<String> lit = new HashSet<>(litPositions);
        lit.retainAll(game.getTileMap().keySet());
        Map<String, Point> gridCells = gridCells(game);
        String home = homePosition(player);
        List<List<String>> clusters = order(clusters(lit, gridCells), home);
        Map<String, Integer> clusterOf = new HashMap<>();
        for (int index = 0; index < clusters.size(); index++) {
            for (String position : clusters.get(index)) {
                clusterOf.put(position, index);
            }
        }
        List<String> letters = letters(clusters, home);
        Map<Integer, Set<String>> wormholesOf = new HashMap<>();
        Map<Integer, Set<Link>> otherLinksOf = new HashMap<>();
        Map<String, Set<String>> badgeLetters = new HashMap<>();
        Adjacency adjacency = Adjacency.of(game, player).forVision();
        for (String from : lit) {
            for (String to : adjacency.from(from)) {
                Integer target = clusterOf.get(to);
                Integer source = clusterOf.get(from);
                if (target == null || target.equals(source)) {
                    continue;
                }
                Link link = new Link(from, to);
                Set<String> wormholes = sharedWormholes(game, link);
                if (!wormholes.isEmpty()) {
                    wormholesOf.computeIfAbsent(target, key -> new TreeSet<>()).addAll(wormholes);
                    wormholesOf.computeIfAbsent(source, key -> new TreeSet<>()).addAll(wormholes);
                    continue;
                }
                otherLinksOf.computeIfAbsent(target, key -> new HashSet<>()).add(link);
                otherLinksOf.computeIfAbsent(source, key -> new HashSet<>()).add(link);
                badgeLetters.computeIfAbsent(from, key -> new TreeSet<>()).add(letters.get(target));
                badgeLetters.computeIfAbsent(to, key -> new TreeSet<>()).add(letters.get(source));
            }
        }
        List<Island> islands = new ArrayList<>();
        for (int index = 0; index < clusters.size(); index++) {
            List<String> cluster = clusters.get(index);
            Map<String, Point> cells = localCells(cluster, gridCells);
            Set<String> dimmed = gapFill(game, cluster, gridCells, lit, cells);
            Map<String, List<String>> badges = new TreeMap<>(MapFrame.POSITION_ORDER);
            cluster.forEach(position -> {
                Set<String> targets = badgeLetters.get(position);
                if (targets != null) {
                    badges.put(position, List.copyOf(targets));
                }
            });
            String letter = letters.get(index);
            String title = title(
                    letter,
                    cluster,
                    wormholesOf.getOrDefault(index, Set.of()),
                    otherLinksOf.getOrDefault(index, Set.of()));
            islands.add(new Island(letter, title, cells, dimmed, badges));
        }
        return islands;
    }

    @Nullable
    static Point gridCell(String position) {
        return isCorner(position) ? null : mapCell(position);
    }

    @Nullable
    private static Point mapCell(String position) {
        Point raw = MapPositionMapper.getTilePosition(position);
        Point origin = MapPositionMapper.getTilePosition("000");
        if (raw == null || origin == null) {
            return null;
        }
        int col = Math.round((raw.x - origin.x) / (float) COLUMN_STEP);
        int row = Math.round((raw.y - origin.y) / (float) HALF_ROW_STEP);
        if ((col + row) % 2 != 0) {
            row++;
        }
        return new Point(col, row);
    }

    static int gridDistance(Point a, Point b) {
        int dc = Math.abs(a.x - b.x);
        int dr = Math.abs(a.y - b.y);
        return dc + Math.max(0, (dr - dc) / 2);
    }

    private static Map<String, Point> gridCells(Game game) {
        Map<String, Point> cells = new HashMap<>();
        for (String position : game.getTileMap().keySet()) {
            Point cell = gridCell(position);
            if (cell != null) {
                cells.put(position, cell);
            }
        }
        return cells;
    }

    private static List<List<String>> clusters(Set<String> lit, Map<String, Point> gridCells) {
        List<String> positions = new ArrayList<>(lit);
        positions.sort(MapFrame.POSITION_ORDER);
        int[] parent = new int[positions.size()];
        for (int index = 0; index < parent.length; index++) {
            parent[index] = index;
        }
        for (int a = 0; a < positions.size(); a++) {
            Point cellA = gridCells.get(positions.get(a));
            if (cellA == null) {
                continue;
            }
            for (int b = a + 1; b < positions.size(); b++) {
                Point cellB = gridCells.get(positions.get(b));
                if (cellB != null && gridDistance(cellA, cellB) <= CLUSTER_DISTANCE) {
                    parent[root(parent, a)] = root(parent, b);
                }
            }
        }
        Map<Integer, List<String>> byRoot = new LinkedHashMap<>();
        for (int index = 0; index < positions.size(); index++) {
            byRoot.computeIfAbsent(root(parent, index), key -> new ArrayList<>())
                    .add(positions.get(index));
        }
        return new ArrayList<>(byRoot.values());
    }

    private static int root(int[] parent, int index) {
        while (parent[index] != index) {
            parent[index] = parent[parent[index]];
            index = parent[index];
        }
        return index;
    }

    private static List<List<String>> order(List<List<String>> clusters, @Nullable String home) {
        List<List<String>> ordered = new ArrayList<>(clusters);
        ordered.sort(Comparator.comparing((List<String> cluster) -> !cluster.contains(home))
                .thenComparing(List::size, Comparator.reverseOrder())
                .thenComparing(cluster -> cluster.getFirst(), MapFrame.POSITION_ORDER));
        return ordered;
    }

    private static List<String> letters(List<List<String>> clusters, @Nullable String home) {
        List<String> letters = new ArrayList<>();
        int next = 0;
        for (List<String> cluster : clusters) {
            if (home != null && cluster.contains(home)) {
                letters.add(HOME_LETTER);
            } else {
                letters.add(String.valueOf(LETTERS.charAt(next++ % LETTERS.length())));
            }
        }
        return letters;
    }

    private static Map<String, Point> localCells(List<String> cluster, Map<String, Point> gridCells) {
        Map<String, Point> cells = new TreeMap<>(MapFrame.POSITION_ORDER);
        for (String position : cluster) {
            Point cell = mapCell(position);
            cells.put(position, cell == null ? new Point(0, 0) : cell);
        }
        return cells;
    }

    private static Set<String> gapFill(
            Game game, List<String> cluster, Map<String, Point> gridCells, Set<String> lit, Map<String, Point> cells) {
        Set<Point> clusterCells = new HashSet<>();
        cluster.forEach(position -> {
            Point cell = gridCells.get(position);
            if (cell != null) {
                clusterCells.add(cell);
            }
        });
        Set<String> dimmed = new TreeSet<>(MapFrame.POSITION_ORDER);
        for (String position : game.getTileMap().keySet()) {
            Point cell = gridCells.get(position);
            if (cell != null && !lit.contains(position) && bridgesLitNeighbours(cell, clusterCells)) {
                dimmed.add(position);
                cells.put(position, cell);
            }
        }
        return dimmed;
    }

    private static boolean bridgesLitNeighbours(Point cell, Set<Point> clusterCells) {
        List<Integer> litSides = new ArrayList<>();
        for (int side = 0; side < NEIGHBOUR_STEPS.size(); side++) {
            Point step = NEIGHBOUR_STEPS.get(side);
            if (clusterCells.contains(new Point(cell.x + step.x, cell.y + step.y))) {
                litSides.add(side);
            }
        }
        for (int a : litSides) {
            for (int b : litSides) {
                int apart = Math.abs(a - b);
                if (Math.min(apart, NEIGHBOUR_STEPS.size() - apart) >= MIN_SIDES_APART) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String title(String letter, List<String> cluster, Set<String> wormholes, Set<Link> otherLinks) {
        if (HOME_LETTER.equals(letter)) {
            return "Home";
        }
        if (!wormholes.isEmpty()) {
            return letter + " - " + String.join(", ", wormholes);
        }
        boolean corner = cluster.stream().allMatch(FocusIslands::isCorner)
                || otherLinks.stream().anyMatch(link -> isCorner(link.from()) || isCorner(link.to()));
        if (corner) {
            return letter + " - corner";
        }
        return letter + " - " + (otherLinks.isEmpty() ? cluster.getFirst() : "link");
    }

    private static Set<String> sharedWormholes(Game game, Link link) {
        Tile from = game.getTileByPosition(link.from());
        Tile to = game.getTileByPosition(link.to());
        if (from == null || to == null) {
            return Set.of();
        }
        List<WormholeModel.Wormhole> toWormholes = to.getWormholes(game);
        Set<String> shared = new TreeSet<>();
        from.getWormholes(game).stream()
                .filter(toWormholes::contains)
                .map(WormholeModel.Wormhole::toString)
                .forEach(shared::add);
        return shared;
    }

    @Nullable
    private static String homePosition(Player player) {
        Tile home = player.getHomeSystemTile();
        return home == null ? null : home.getPosition();
    }

    private static boolean isCorner(String position) {
        return BoardLayout.CORNER_POSITIONS.contains(position.toLowerCase());
    }
}
