package ti4.game.persistence;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import ti4.game.Player;

public class ManagedPlayer {

    @Getter
    private final String id;

    @Getter
    private final String name;

    // We have to use a map for the "replace" logic to work, a set won't provide an atomic replace
    private final Map<String, ManagedGame> games;

    public ManagedPlayer(ManagedGame game, Player player) {
        id = player.getUserID();
        name = player.getUserName();
        games = new ConcurrentHashMap<>();
        games.put(game.getName(), game);
    }

    void removeGame(String gameName) {
        games.remove(gameName);
    }

    void addOrReplaceGame(ManagedGame game, Player player) {
        if (!player.getUserID().equals(id)) {
            throw new IllegalArgumentException("Player " + player.getUserID() + " attempted merge with " + id);
        }
        games.put(game.getName(), game);
    }

    public Set<ManagedGame> getGames() {
        return Set.copyOf(games.values());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ManagedPlayer that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
