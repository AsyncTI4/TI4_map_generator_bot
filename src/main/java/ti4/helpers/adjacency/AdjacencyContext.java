package ti4.helpers.adjacency;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import lombok.Getter;
import ti4.game.Game;
import ti4.game.Player;

@Getter
public final class AdjacencyContext {
    private final Game game;
    private final Player player;
    private final AdjacencyPurpose purpose;
    private final Map<String, Object> memo = new HashMap<>();

    AdjacencyContext(Game game, Player player, AdjacencyPurpose purpose) {
        this.game = game;
        this.player = player;
        this.purpose = purpose;
    }

    public boolean isVision() {
        return purpose == AdjacencyPurpose.VISION;
    }

    public boolean isMovement() {
        return purpose == AdjacencyPurpose.MOVEMENT;
    }

    public boolean isActivePlayer() {
        return player != null && player == game.getActivePlayer();
    }

    public boolean hasActivatedSystem() {
        String activeSystem = game.getCurrentActiveSystem();
        return activeSystem != null && !activeSystem.isEmpty();
    }

    @SuppressWarnings("unchecked")
    <T> T memo(String key, Supplier<T> supplier) {
        Object value = memo.get(key);
        if (value == null) {
            value = supplier.get();
            memo.put(key, value);
        }
        return (T) value;
    }
}
