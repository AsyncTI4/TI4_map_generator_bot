package ti4.ai.strategy;

import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.ai.tactical.TacticalPlan;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;

@UtilityClass
public class CommandTokenValue {

    public static double tactic(Game game, Player seat, int gained) {
        return TokenValue.tacticTokens(PlanInputs.of(game, seat, PlanMoment.ACTION), gained);
    }

    public static double lockLoss(Game game, Player seat, Tile tile, List<TacticalPlan> remembered) {
        if (tile.hasPlayerCC(seat)) return 0;
        return TokenValue.lockedOut(PlanInputs.of(game, seat, PlanMoment.ACTION), tile.getPosition(), remembered);
    }
}
