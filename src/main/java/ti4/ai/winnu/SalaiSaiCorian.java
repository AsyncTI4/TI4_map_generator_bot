package ti4.ai.winnu;

import java.util.Map;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.CombatModifiers.Side;
import ti4.game.Player;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

@UtilityClass
public class SalaiSaiCorian {

    public static final String FLAGSHIP = "winnu_flagship";

    public static int dice(UnitModel model, int dice, Side opponent) {
        if (!FLAGSHIP.equals(model.getId()) || dice > 0) return dice;
        return nonFighterShips(opponent);
    }

    static int nonFighterShips(Side opponent) {
        Player player = opponent.player();
        int ships = 0;
        for (Map.Entry<UnitType, Integer> entry : opponent.units().entrySet()) {
            if (entry.getKey() == UnitType.Fighter) continue;
            UnitModel unit = player.getUnitByType(entry.getKey());
            if (unit != null && unit.getIsShip()) ships += entry.getValue();
        }
        return ships;
    }
}
