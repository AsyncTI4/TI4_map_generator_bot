package ti4.ai.strategy;

import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class CommandTokenValue {

    public static double tactic(Game game, Player seat, int gained) {
        return TokenValue.tacticTokens(PlanInputs.of(game, seat, PlanMoment.ACTION), gained);
    }
}
