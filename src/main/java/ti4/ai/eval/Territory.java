package ti4.ai.eval;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.model.MapPositionMapper;

public final class Territory {

    public enum Zone {
        HOME,
        LANE,
        SLICE,
        BORDER,
        FOREIGN,
        OTHER_HOME,
        MECATOL,
        NEUTRAL
    }

    private static final int RADIUS = 12;
    private static final String OFF_MAP = "x";
    private static final long UNREACHED = Long.MAX_VALUE;

    private final String homePosition;
    private final String mecatolPosition;
    private final List<String> otherHomePositions;
    private final Map<String, Integer> fromHome;
    private final Map<String, Integer> fromMecatol;
    private final List<Map<String, Integer>> fromOtherHomes;
    private final Map<String, Zone> zones = new HashMap<>();

    private Territory(Game game, Player seat) {
        Tile home = seat.getHomeSystemTile();
        homePosition = home == null ? null : home.getPosition();
        Tile mecatol = game.getMecatolTile();
        mecatolPosition = mecatol == null ? null : mecatol.getPosition();
        otherHomePositions = game.getRealPlayers().stream()
                .filter(other -> other != seat)
                .map(Player::getHomeSystemTile)
                .filter(tile -> tile != null)
                .map(Tile::getPosition)
                .distinct()
                .toList();
        fromHome = distancesFrom(homePosition);
        fromMecatol = distancesFrom(mecatolPosition);
        fromOtherHomes =
                otherHomePositions.stream().map(Territory::distancesFrom).toList();
    }

    public static Territory of(Game game, Player seat) {
        return new Territory(game, seat);
    }

    public Zone zone(Tile tile) {
        return zones.computeIfAbsent(tile.getPosition(), this::classify);
    }

    private Zone classify(String position) {
        if (position.equals(homePosition)) return Zone.HOME;
        if (position.equals(mecatolPosition)) return Zone.MECATOL;
        if (otherHomePositions.contains(position)) return Zone.OTHER_HOME;
        Integer own = fromHome.get(position);
        if (own == null) return Zone.NEUTRAL;
        long other = nearestOtherHome(position);
        if (own <= other && onShortestPathToMecatol(position, own)) return Zone.LANE;
        if (own < other) return Zone.SLICE;
        return own == other ? Zone.BORDER : Zone.FOREIGN;
    }

    private long nearestOtherHome(String position) {
        long nearest = UNREACHED;
        for (Map<String, Integer> distances : fromOtherHomes) {
            Integer distance = distances.get(position);
            if (distance != null) nearest = Math.min(nearest, distance);
        }
        return nearest;
    }

    private boolean onShortestPathToMecatol(String position, int own) {
        Integer homeToMecatol = mecatolPosition == null ? null : fromHome.get(mecatolPosition);
        Integer toMecatol = fromMecatol.get(position);
        return homeToMecatol != null && toMecatol != null && (long) own + toMecatol == homeToMecatol;
    }

    private static Map<String, Integer> distancesFrom(@Nullable String start) {
        Map<String, Integer> distances = new HashMap<>();
        if (start == null || MapPositionMapper.getAdjacentTilePositions(start).isEmpty()) return distances;
        distances.put(start, 0);
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            String position = queue.removeFirst();
            int distance = distances.get(position);
            if (distance >= RADIUS) continue;
            for (String next : MapPositionMapper.getAdjacentTilePositions(position)) {
                if (OFF_MAP.equals(next) || next.isBlank() || distances.containsKey(next)) continue;
                distances.put(next, distance + 1);
                queue.addLast(next);
            }
        }
        return distances;
    }
}
