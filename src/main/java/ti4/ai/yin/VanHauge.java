package ti4.ai.yin;

import lombok.experimental.UtilityClass;
import ti4.ai.eval.CombatOdds.Force;
import ti4.game.Player;
import ti4.helpers.Units.UnitType;

@UtilityClass
public class VanHauge {

    static final String FLAGSHIP = "yin_flagship";

    public static Force applyTo(Player player, Force force) {
        return aboard(player, force) ? force.detonating() : force;
    }

    static boolean aboard(Player player, Force force) {
        return player.hasUnit(FLAGSHIP) && force.units().stream().anyMatch(unit -> unit.type() == UnitType.Flagship);
    }
}
