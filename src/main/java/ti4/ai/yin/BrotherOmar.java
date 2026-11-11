package ti4.ai.yin;

import java.util.Map;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.ValueScale;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperCommanders;
import ti4.helpers.Units.UnitType;
import ti4.model.TechnologyModel;
import ti4.model.TechnologyModel.TechnologyType;

@UtilityClass
public class BrotherOmar {

    static final String COMMANDER = "yincommander";
    static final double UNLOCK_VALUE = 1.0;
    public static final double INFANTRY_VALUE = 0.5 * ValueScale.RESOURCE;

    public static double unlockBonus(Player seat) {
        return seat.hasLeader(COMMANDER) && !seat.hasLeaderUnlocked(COMMANDER) ? UNLOCK_VALUE : 0;
    }

    public static boolean active(Player seat) {
        Game game = seat.getGame();
        return game != null && game.playerHasLeaderUnlockedOrAlliance(seat, COMMANDER);
    }

    public static Map<TechnologyType, Integer> skips(Player seat) {
        return active(seat) ? Map.of(TechnologyType.BIOTIC, 1) : Map.of();
    }

    public static boolean ignoresPrerequisites(Player seat, TechnologyModel tech) {
        if (!active(seat)) return false;
        Game game = seat.getGame();
        return ButtonHelper.getNumberOfUnitsOnTheBoard(game, seat, UnitType.Infantry.plainName(), false) > 0
                && ButtonHelperCommanders.getVeldyrCommanderTechs(seat, game, true)
                        .contains(tech.getAlias());
    }
}
