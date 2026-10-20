package ti4.ai.explore;

import ti4.ai.brain.AiTurnContext;
import ti4.ai.eval.GameHorizon;
import ti4.ai.trade.ComponentValues;
import ti4.game.Game;
import ti4.game.Player;

class DynamisCoreFlow implements RelicFlow {

    private static final String RELIC = "dynamiscore";
    private static final int COMMODITY_BONUS = 2;
    private static final int ROUNDS_COUNTED = 3;

    @Override
    public String name() {
        return "Dynamis Core";
    }

    @Override
    public String relicId() {
        return RELIC;
    }

    @Override
    public double netValue(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        double gain = seat.getCommoditiesTotal();
        int rounds = Math.min(ROUNDS_COUNTED, GameHorizon.roundsLeft(game));
        double kept = COMMODITY_BONUS * rounds * ComponentValues.ownCommodity(game, seat);
        return gain - kept;
    }
}
