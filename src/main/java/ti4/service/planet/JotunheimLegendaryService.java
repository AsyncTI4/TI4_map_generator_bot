package ti4.service.planet;

import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class JotunheimLegendaryService {

    private static final String HRUNGNIRS_HUSK = "jotunheimHrungnirsHusk_";

    public static void activate(Game game, Player player) {
        game.setStoredValue(HRUNGNIRS_HUSK + player.getFaction(), "active");
    }

    public static void clear(Game game, Player player) {
        game.removeStoredValue(HRUNGNIRS_HUSK + player.getFaction());
    }

    public static boolean isActive(Game game, Player player) {
        return player != null && "active".equals(game.getStoredValue(HRUNGNIRS_HUSK + player.getFaction()));
    }
}
