package ti4.image.playerarea;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ti4.game.Game;
import ti4.game.Player;

public final class NeighbourMap {

    private static final int EQUIVALENTS_PLAYER_LIMIT = 30;

    private final List<Player> realPlayers;
    private final Map<Player, Set<Player>> direct = new HashMap<>();
    private final Map<Player, Set<Player>> withEquivalents = new HashMap<>();

    public NeighbourMap(Game game) {
        this.realPlayers = game.getRealPlayers();
    }

    public Set<Player> direct(Player player) {
        Set<Player> cached = direct.get(player);
        if (cached == null) {
            cached = inGameOrder(player.getNeighbouringPlayers(false));
            direct.put(player, cached);
        }
        return cached;
    }

    Set<Player> withEquivalents(Player player) {
        Set<Player> cached = withEquivalents.get(player);
        if (cached == null) {
            cached = inGameOrder(collectWithEquivalents(player));
            withEquivalents.put(player, cached);
        }
        return cached;
    }

    private Set<Player> collectWithEquivalents(Player player) {
        Set<Player> neighbours = new HashSet<>(direct(player));
        if (realPlayers.size() < EQUIVALENTS_PLAYER_LIMIT) {
            for (Player other : realPlayers) {
                if (direct(other).contains(player)) {
                    neighbours.add(other);
                }
            }
        }
        neighbours.remove(player);
        return neighbours;
    }

    private Set<Player> inGameOrder(Set<Player> players) {
        Set<Player> ordered = new LinkedHashSet<>();
        for (Player real : realPlayers) {
            if (players.contains(real)) {
                ordered.add(real);
            }
        }
        ordered.addAll(players);
        return ordered;
    }
}
