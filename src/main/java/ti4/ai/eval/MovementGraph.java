package ti4.ai.eval;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.FoWHelper;

@UtilityClass
public class MovementGraph {

    private static final int NEBULA_MOVE = 1;

    public static Map<String, Integer> reach(Game game, Player player, String origin, int moveValue) {
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
            if (!position.equals(origin) && blocksPassage(game, player, tile)) continue;
            for (String next : FoWHelper.getAdjacentTiles(game, position, player, false, false, true)) {
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
                || FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game);
    }

    public static boolean canEnter(Game game, Player player, @Nullable Tile tile) {
        if (tile == null || tile.getTileModel() == null) return false;
        if (tile.getTileModel().isHyperlane() || tile.isSupernova()) return false;
        return !tile.isAsteroidField() || hasAntimassDeflectors(player);
    }

    private static boolean hasAntimassDeflectors(Player player) {
        return player.hasTech("amd") || player.hasTech("absol_amd");
    }
}
