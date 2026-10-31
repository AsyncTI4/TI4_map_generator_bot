package ti4.game;

import lombok.experimental.UtilityClass;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;

@UtilityClass
public class SanityLimits {

    public static final int INFANTRY_AND_FIGHTERS_PER_TILE = 999;
    public static final int PLASTIC_UNITS = 99;
    public static final int COMMAND_TOKENS_PER_POOL = 99;
    public static final int TRADE_GOODS = 999;
    public static final int COMMODITIES = 999;
    public static final int TRADE_GOODS_ON_CARD = 999;
    public static final int DEBT_TOKENS_PER_COLOR = 999;
    public static final int STASIS_UNITS = 999;

    public static int unitsInOneHolder(UnitType unitType) {
        return isTokenBacked(unitType) ? INFANTRY_AND_FIGHTERS_PER_TILE : PLASTIC_UNITS;
    }

    public static int reinforcementLimit(Game game, UnitKey unitKey) {
        Player owner = game.getPlayerByColorID(unitKey.colorID()).orElse(null);
        if (owner == null) return PLASTIC_UNITS;
        int reinforcements = owner.getUnitCap(unitKey.asyncID());
        return cap(reinforcements, PLASTIC_UNITS);
    }

    public static boolean isTokenBacked(UnitType unitType) {
        return unitType == UnitType.Infantry || unitType == UnitType.Fighter;
    }

    public static int cap(int value, int max) {
        return Math.clamp(value, 0, max);
    }

    public static int cappedSum(int current, int added, int max) {
        return Math.clamp((long) current + added, 0, max);
    }
}
