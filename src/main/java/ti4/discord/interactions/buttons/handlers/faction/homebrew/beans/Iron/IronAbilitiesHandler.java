package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.Iron;

import lombok.experimental.UtilityClass;
import ti4.game.Player;
import ti4.game.UnitHolder;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;

@UtilityClass
public class IronAbilitiesHandler {

    private static final String EXO_ATMOSPHERIC = "exo-atmospheric";

    public static boolean hasExoAtmospheric(Player player) {
        return player != null && player.hasAbility(EXO_ATMOSPHERIC);
    }

    public static boolean isExoAtmosphericMech(Player player, UnitType unitType) {
        return hasExoAtmospheric(player) && unitType == UnitType.Mech;
    }

    public static boolean isExoAtmosphericMechInSpace(Player player, UnitType unitType, UnitHolder unitHolder) {
        return isExoAtmosphericMech(player, unitType)
                && unitHolder != null
                && Constants.SPACE.equals(unitHolder.getName());
    }
}
