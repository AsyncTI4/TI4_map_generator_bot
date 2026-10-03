package ti4.service.tactical.movement;

import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Tile;

@UtilityClass
public class RealityFieldImpactorService {
    private static final String NULLIFIED_ANOMALY = "realityFieldImpactorAnomaly";

    public static boolean hasBeenUsed(Game game) {
        return game != null && !game.getStoredValue(NULLIFIED_ANOMALY).isBlank();
    }

    public static boolean nullifies(Game game, Tile tile) {
        return tile != null && tile.getPosition().equalsIgnoreCase(game.getStoredValue(NULLIFIED_ANOMALY));
    }

    public static void nullify(Game game, Tile tile) {
        if (game != null && tile != null) {
            game.setStoredValue(NULLIFIED_ANOMALY, tile.getPosition());
        }
    }

    public static void clear(Game game) {
        if (game != null) {
            game.removeStoredValue(NULLIFIED_ANOMALY);
        }
    }
}
