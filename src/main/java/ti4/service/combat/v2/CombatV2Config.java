package ti4.service.combat.v2;

import lombok.experimental.UtilityClass;
import ti4.game.Game;

/** Stores the per-game Combat V2 feature toggle in the existing game save. */
@UtilityClass
public class CombatV2Config {
    public static final String ENABLED_KEY = "combatV2Enabled";

    public static boolean isEnabled(Game game) {
        return "true".equalsIgnoreCase(game.getStoredValue(ENABLED_KEY));
    }

    public static void setEnabled(Game game, boolean enabled) {
        if (enabled) game.setStoredValue(ENABLED_KEY, "true");
        else game.removeStoredValue(ENABLED_KEY);
    }
}
