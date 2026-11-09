package ti4.ai.winnu;

import lombok.experimental.UtilityClass;
import ti4.ai.eval.PlanetStake;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;

@UtilityClass
public class Reclamation {

    public static final String ABILITY = "reclamation";
    static final String SPACE_DOCK = "sd";
    static final String PDS = "pds";

    public static double captureBonus(Game game, Player seat) {
        if (!seat.hasAbility(ABILITY) || seat.controlsMecatol(false)) return 0;
        double bonus = 0;
        if (inReinforcements(game, seat, SPACE_DOCK)) bonus += PlanetStake.SPACE_DOCK;
        if (inReinforcements(game, seat, PDS)) bonus += PlanetStake.PDS;
        return bonus;
    }

    static boolean inReinforcements(Game game, Player seat, String asyncId) {
        int cap = seat.getUnitCap(asyncId);
        return cap <= 0 || ButtonHelper.getNumberOfUnitsOnTheBoard(game, seat, asyncId) < cap;
    }
}
