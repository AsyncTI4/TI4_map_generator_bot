package ti4.ai.eval;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.FoWHelper;

@UtilityClass
public class MovementGraph {

    private static final int NEBULA_MOVE = 1;
    private static final String ANTIMASS_DEFLECTORS = "amd";
    private static final ThreadLocal<Boolean> ASSUMING_ANTIMASS = new ThreadLocal<>();

    public static <T> T withAntimassDeflectors(Supplier<T> evaluation) {
        Boolean before = ASSUMING_ANTIMASS.get();
        ASSUMING_ANTIMASS.set(Boolean.TRUE);
        try {
            return evaluation.get();
        } finally {
            if (before == null) {
                ASSUMING_ANTIMASS.remove();
            } else {
                ASSUMING_ANTIMASS.set(before);
            }
        }
    }

    public static Map<String, Integer> reach(Game game, Player player, String origin, int moveValue) {
        return reach(game, player, origin, moveValue, Set.of());
    }

    public static Map<String, Integer> reach(
            Game game, Player player, String origin, int moveValue, Set<String> screened) {
        Map<String, Integer> distances = new HashMap<>();
        Tile start = game.getTileByPosition(origin);
        if (start == null || moveValue <= 0 || start.isGravityRift(game, player)) return distances;
        int budget = start.isNebula(game) ? Math.min(moveValue, NEBULA_MOVE) : moveValue;
        distances.put(origin, 0);
        Deque<String> queue = new ArrayDeque<>();
        queue.add(origin);
        while (!queue.isEmpty()) {
            String position = queue.removeFirst();
            int distance = distances.get(position);
            if (distance >= budget) continue;
            Tile tile = game.getTileByPosition(position);
            if (!position.equals(origin) && (screened.contains(position) || blocksPassage(game, player, tile)))
                continue;
            for (String next : Adjacency.forMovement(game, player, position)) {
                if (distances.containsKey(next) || !canEnter(game, player, game.getTileByPosition(next))) continue;
                distances.put(next, distance + 1);
                queue.addLast(next);
            }
        }
        return distances;
    }

    static boolean blocksPassage(Game game, Player player, @Nullable Tile tile) {
        return tile == null
                || tile.isNebula(game)
                || tile.isGravityRift(game, player)
                || (FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game) && !hasLightWaveDeflector(player));
    }

    public static boolean canEnter(Game game, Player player, @Nullable Tile tile) {
        if (tile == null || tile.getTileModel() == null) return false;
        if (tile.getTileModel().isHyperlane() || tile.isSupernova()) return false;
        return !tile.isAsteroidField() || hasAntimassDeflectors(player);
    }

    private static boolean hasLightWaveDeflector(Player player) {
        return player.hasTech("lwd") || player.hasTech("absol_lwd");
    }

    private static boolean hasAntimassDeflectors(Player player) {
        return Boolean.TRUE.equals(ASSUMING_ANTIMASS.get())
                || player.hasTech(ANTIMASS_DEFLECTORS)
                || player.hasTech("absol_amd");
    }
}
