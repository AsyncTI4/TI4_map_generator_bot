package ti4.ai.expedition;

import ti4.ai.strategy.SpendOutlook;
import ti4.game.Game;
import ti4.game.Player;

enum SpendHorizon {
    AHEAD(1.0, 2.0 / 3.0),
    IDLE(0.4, 0.4 * 2.0 / 3.0),
    PASSING(0.1, 0.1);

    private final double perResource;
    private final double perInfluence;

    SpendHorizon(double perResource, double perInfluence) {
        this.perResource = perResource;
        this.perInfluence = perInfluence;
    }

    double perResource() {
        return perResource;
    }

    double perInfluence() {
        return perInfluence;
    }

    static SpendHorizon of(Game game, Player seat, Holdings holdings) {
        if (holdings.passing()) return PASSING;
        return SpendOutlook.spendingAhead(game, seat) ? AHEAD : IDLE;
    }
}
