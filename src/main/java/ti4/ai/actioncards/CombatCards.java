package ti4.ai.actioncards;

import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.ai.tactical.CombatForecast;
import ti4.ai.tactical.CombatMoment;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class CombatCards {

    static final String EMERGENCY_REPAIRS = "emergency";
    private static final String REPAIRS_HANDLER = "resolveEmergencyRepairs_";

    static List<AiCard> all() {
        return List.of(AiActionCards.combatRound(EMERGENCY_REPAIRS, REPAIRS_HANDLER));
    }

    static double valueAtRoundStart(Game game, Player seat, CombatMoment moment, String alias) {
        if (EMERGENCY_REPAIRS.equals(alias)) {
            return CombatForecast.repairSwing(game, seat, moment.position(), moment.holder());
        }
        return 0;
    }

    static boolean addsRoundModifier(String alias) {
        return false;
    }

    static String titleOf(CardPlay play) {
        return SabotageWindow.title(play.alias());
    }
}
