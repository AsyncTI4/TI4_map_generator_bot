package ti4.game.persistence;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;

public class ManagedPlayer {

    @Getter
    private final String id;

    @Getter
    private final String name;

    // We have to use a map for the "replace" logic to work, a set won't provide an atomic replace
    private final Map<String, ManagedGame> games;

    public ManagedPlayer(ManagedGame game, String id, String name) {
        this.id = id;
        this.name = name;
        games = new ConcurrentHashMap<>();
        games.put(game.getName(), game);
    }

    void removeGame(String gameName) {
        games.remove(gameName);
    }

    void addOrReplaceGame(ManagedGame game, String userId) {
        if (!userId.equals(id)) {
            throw new IllegalArgumentException("Player " + userId + " attempted merge with " + id);
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
