package ti4.ai.agenda;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.scoring.ObjectiveCatalog;
import ti4.ai.scoring.ObjectivePolicy;
import ti4.ai.scoring.Wallet;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class GilaVotes {

    static final String BUTTON = "exhaustForVotes_hacanCommanderTg";
    private static final String COMMANDER = "hacancommander";
    private static final int VOTES_PER_TRADE_GOOD = 2;
    private static final double TRADE_GOOD = 1.0;
    private static final double MIN_GAIN = 0.5;
    private static final double TIE_TOLERANCE = 1e-9;

    static int tradeGoodsToSpend(Game game, Player seat, String outcome, int planetVotes) {
        int most = spendable(game, seat);
        if (most <= 0 || !game.playerHasLeaderUnlockedOrAlliance(seat, COMMANDER)) return 0;
        AgendaForecast forecast = AgendaForecast.of(game, seat);
        if (forecast.isEmpty()) return 0;
        double base = forecast.withVotes(seat, outcome, planetVotes).expectedUtility(seat);
        int best = 0;
        double bestGain = 0;
        for (int tradeGoods = 1; tradeGoods <= most; tradeGoods++) {
            int votes = planetVotes + VOTES_PER_TRADE_GOOD * tradeGoods;
            double gain =
                    forecast.withVotes(seat, outcome, votes).expectedUtility(seat) - base - TRADE_GOOD * tradeGoods;
            if (gain > bestGain + TIE_TOLERANCE) {
                best = tradeGoods;
                bestGain = gain;
            }
        }
        return bestGain >= MIN_GAIN ? best : 0;
    }

    static int spendable(Game game, Player seat) {
        return Math.max(0, seat.getTg() - spendObjectiveReserve(game, seat));
    }

    private static int spendObjectiveReserve(Game game, Player seat) {
        Wallet nextRound = Wallet.of(game, seat).withPlanets(game, seat.getPlanets());
        Wallet withoutTradeGoods = nextRound.withTradeGoods(0);
        return game.getRevealedPublicObjectives().keySet().stream()
                .filter(objective -> !ObjectivePolicy.hasScored(game, seat, objective))
                .map(ObjectiveCatalog::spendCost)
                .flatMap(Optional::stream)
                .filter(nextRound::canPay)
                .mapToInt(cost -> withoutTradeGoods.shortfall(cost, seat.getTg() + 1))
                .max()
                .orElse(0);
    }
}
