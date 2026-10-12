package ti4.ai.actioncards;

import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.ai.tactical.CombatMoment;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class CombatCards {

    static List<AiCard> all() {
        return List.of();
    }

    static double valueAtRoundStart(Game game, Player seat, CombatMoment moment, String alias) {
        return 0;
    }

    static boolean addsRoundModifier(String alias) {
        return false;
    }

    static String titleOf(CardPlay play) {
        return SabotageWindow.title(play.alias());
    }
}
