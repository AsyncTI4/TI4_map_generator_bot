package ti4.discord.interactions.buttons.handlers.faction.homebrew.arvaxi;

import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.image.Mapper;
import ti4.model.UnitModel;

@UtilityClass
public class MobilizationEngineHandler {

    static final String STORED_KEY = "arvaxiMobilizationEngine";

    public static boolean hasEngineAttached(Game game) {
        return !game.getStoredValue(STORED_KEY).isEmpty();
    }

    private static boolean isAttachedToUnit(Game game, Player player, UnitModel unit) {
        String stored = game.getStoredValue(STORED_KEY);
        if (stored.isEmpty()) return false;
        int firstSep = stored.indexOf('_');
        int lastSep = stored.lastIndexOf('_');
        if (firstSep < 0 || firstSep == lastSep) return false;
        if (!player.getFaction().equals(stored.substring(0, firstSep))) return false;
        String techID = stored.substring(firstSep + 1, lastSep);
        UnitModel engineUnit = Mapper.getUnitModelByTechUpgrade(techID);
        return engineUnit != null && engineUnit.getAsyncId().equals(unit.getAsyncId());
    }

    private static boolean isBoon(Game game) {
        return game.getStoredValue(STORED_KEY).endsWith("_boon");
    }

    public static int getCombatMod(Game game, Player player, UnitModel unit) {
        return getMoveMod(game, player, unit);
    }

    public static int getMoveMod(Game game, Player player, UnitModel unit) {
        if (!isAttachedToUnit(game, player, unit)) return 0;
        return isBoon(game) ? 1 : -1;
    }

    public static int getCostMod(Game game, Player player, UnitModel unit) {
        if (!isAttachedToUnit(game, player, unit)) return 0;
        return isBoon(game) ? -1 : 1;
    }

    public static int getCapacityMod(Game game, Player player, UnitModel unit) {
        return getMoveMod(game, player, unit);
    }
}
