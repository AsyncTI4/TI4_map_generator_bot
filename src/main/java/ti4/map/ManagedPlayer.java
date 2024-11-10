package ti4.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import lombok.Getter;

import static org.apache.commons.lang3.StringUtils.defaultIfBlank;

@Getter
public class ManagedPlayer {

    private final String id;
    private final String name;
    private final List<ManagedGame> games = new ArrayList<>();
    private final String afkHours;
    private final boolean distanceBasedTacticalActions;

    public ManagedPlayer(ManagedGame game, Player player) {
        id = player.getUserID();
        name = player.getUserName();
        games.add(game);
        afkHours = defaultIfBlank(player.getHoursThatPlayerIsAFK(), null);
        distanceBasedTacticalActions = player.doesPlayerPreferDistanceBasedTacticalActions();
    }

    public void merge(ManagedGame game, Player player) {
        if (!player.getUserID().equals(id)) {
            throw new IllegalArgumentException("Player " + player.getUserID() + " attempted merge with " + id);
        }
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
