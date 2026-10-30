package ti4.ai.trade;

import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.GameHorizon;
import ti4.game.Game;
import ti4.game.Planet;

@UtilityClass
class TradePlanets {

    private static final double UNGARRISONED_SHARE = 0.5;
    private static final int MAX_ROUNDS_HELD = 3;

    static double receiveValue(Game game, DealItem item) {
        return UNGARRISONED_SHARE * worth(game, item.detail());
    }

    static double worth(Game game, String planetName) {
        Planet planet = game.getPlanetsInfo().get(planetName);
        if (planet == null) return 0;
        return BoardView.planetValue(planet) * Math.min(MAX_ROUNDS_HELD, GameHorizon.roundsLeft(game));
    }
}
