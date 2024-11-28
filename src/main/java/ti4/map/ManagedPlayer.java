package ti4.map;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import lombok.Getter;

@Getter
public class ManagedPlayer {

    private final String id;
    private final String name;
    private final Set<ManagedGame> games;

    public ManagedPlayer(ManagedGame game, Player player) {
        id = player.getUserID();
        name = player.getUserName();
        games = new HashSet<>();
        games.add(game);
    }

    public synchronized void merge(ManagedGame game, Player player) {
        if (!player.getUserID().equals(id)) {
            throw new IllegalArgumentException("Player " + player.getUserID() + " attempted merge with " + id);
        }
        games.add(game);
        afkHours = player.getHoursThatPlayerIsAFK();
        distanceBasedTacticalActions = player.doesPlayerPreferDistanceBasedTacticalActions();
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
