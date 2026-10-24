package ti4.ai.tactical;

import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.helpers.Units.UnitType;

@UtilityClass
class MagenDefenseGrid {

    private static final String OMEGA = "md_c1";
    private static final String OMEGA_OMEGA = "md";

    static int startOfCombatHits(Player owner, Planet planet) {
        return hasTech(owner) && structures(owner, planet) > 0 ? 1 : 0;
    }

    static int placedInfantry(Player owner, Planet planet) {
        return owner.hasTech(OMEGA_OMEGA) ? structures(owner, planet) : 0;
    }

    private static boolean hasTech(Player owner) {
        return owner.hasTech(OMEGA) || owner.hasTech(OMEGA_OMEGA);
    }

    private static int structures(Player owner, Planet planet) {
        return BoardView.count(planet, owner, UnitType.Pds) + BoardView.count(planet, owner, UnitType.Spacedock);
    }
}
