package ti4.game.persistence;

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

    private final Set<ManagedGame> games;

    public ManagedPlayer(ManagedGame game, Player player) {
        id = player.getUserID();
        name = player.getUserName();
        games = ConcurrentHashMap.newKeySet();
        games.add(game);
    }

    void removeGame(ManagedGame game) {
        games.remove(game);
    }

    void addOrReplaceGame(ManagedGame game, Player player) {
        if (!player.getUserID().equals(id)) {
            throw new IllegalArgumentException("Player " + player.getUserID() + " attempted merge with " + id);
        }
        games.add(game);
    }

    public Set<ManagedGame> getGames() {
        return Set.copyOf(games);
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
