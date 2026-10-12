package ti4.ai.actioncards;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.scoring.ScoringReserve;
import ti4.ai.scoring.SpendCost;
import ti4.ai.scoring.Wallet;
import ti4.ai.tactical.ProductionPlanner;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class InfluencePayments {

    static final String EXHAUST_DONE = "deleteButtons_spitItOut";
    private static final double TRADE_GOOD = 1.0;
    private static final double INFLUENCE_OPPORTUNITY = 0.25;
    private static final double UNSPENT_SHARE = 0.3;

    static Optional<Wallet.Payment> payment(Game game, Player seat, int influence) {
        SpendCost cost = SpendCost.influence(influence);
        return ScoringReserve.planAfterReserve(game, seat, cost)
                .or(() -> Wallet.of(game, seat).plan(cost));
    }

    static double cost(Game game, Wallet.Payment payment) {
        double perResource = ProductionPlanner.fillerValuePerResource(game) * UNSPENT_SHARE;
        double planets = 0;
        for (String name : payment.forInfluence()) {
            planets += INFLUENCE_OPPORTUNITY * game.getPlanetsInfo().get(name).getInfluence()
                    + perResource * game.getPlanetsInfo().get(name).getResources();
        }
        return planets + payment.tradeGoods() * TRADE_GOOD;
    }
}
