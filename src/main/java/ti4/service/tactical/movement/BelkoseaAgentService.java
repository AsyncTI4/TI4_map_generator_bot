package ti4.service.tactical.movement;

import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;

@UtilityClass
public class BelkoseaAgentService {
    private static final String STATE = "belkoseaAgentIgnoredSystem_";

    public static void ignoreOtherShips(Game game, Player player, Tile tile) {
        game.setStoredValue(stateKey(player), tile.getPosition());
    }

    public static boolean ignoresOtherShips(Game game, Player player, Tile tile) {
        return tile != null && tile.getPosition().equals(game.getStoredValue(stateKey(player)));
    }

    public static void clear(Game game, Player player) {
        game.removeStoredValue(stateKey(player));
    }

    private static String stateKey(Player player) {
        return STATE + player.getFaction();
    }
}
