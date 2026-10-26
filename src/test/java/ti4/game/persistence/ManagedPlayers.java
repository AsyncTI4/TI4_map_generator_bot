package ti4.game.persistence;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;

/**
 * Test access to the managed player registry, which keeps every player id it has seen even after their last game is
 * deleted. Replaying a seeded self-play game draws the same AI seat ids again, and the seat id generator would
 * otherwise reject them as taken.
 */
public final class ManagedPlayers {

    private ManagedPlayers() {}

    public static void forgetWithoutGames(Collection<String> playerIds) {
        Map<String, ManagedPlayer> players = registry();
        for (String id : playerIds) {
            ManagedPlayer player = players.get(id);
            if (player != null && player.getGames().isEmpty()) players.remove(id);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ManagedPlayer> registry() {
        try {
            Field field = GameManager.class.getDeclaredField("userIdToManagedPlayer");
            field.setAccessible(true);
            return (Map<String, ManagedPlayer>) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
