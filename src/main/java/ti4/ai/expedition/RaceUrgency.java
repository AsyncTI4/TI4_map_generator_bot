package ti4.ai.expedition;

import lombok.experimental.UtilityClass;
import ti4.ai.scoring.SpendCost;
import ti4.ai.scoring.Wallet;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;

@UtilityClass
class RaceUrgency {

    static final double PER_RIVAL = 0.25;
    static final double CAP = 1.0;

    static double of(Game game, Player seat, ExpeditionKind kind) {
        return Math.min(CAP, PER_RIVAL * racingRivals(game, seat, kind));
    }

    static double stillOpen(Game game, Player seat, ExpeditionKind kind) {
        return Math.pow(1 - PER_RIVAL, racingRivals(game, seat, kind));
    }

    private static long racingRivals(Game game, Player seat, ExpeditionKind kind) {
        boolean lastSlice = game.getExpeditions().getRemainingExpeditionCount() == 1;
        return game.getRealPlayers().stream()
                .filter(rival -> rival != seat)
                .filter(rival -> BreakthroughValue.isLocked(rival)
                        || (lastSlice && ThundersEdgeRace.gainFromClaiming(game, rival) > 0))
                .filter(rival -> couldClaim(game, rival, kind))
                .count();
    }

    static boolean couldClaim(Game game, Player rival, ExpeditionKind kind) {
        return switch (kind) {
            case TECH_SKIP ->
                rival.getReadiedPlanets().stream().anyMatch(planet -> ButtonHelper.checkForTechSkips(game, planet));
            case TRADE_GOODS -> rival.getTg() >= KindPricing.TRADE_GOODS_COST;
            case FIVE_RES -> Wallet.of(game, rival).canPay(SpendCost.resources(KindPricing.RESOURCES_COST));
            case FIVE_INF -> Wallet.of(game, rival).canPay(SpendCost.influence(KindPricing.INFLUENCE_COST));
            case SECRET -> !rival.getSecretsUnscored().isEmpty();
            case ACTION_CARDS -> rival.getAcCount() >= KindPricing.ACTION_CARDS_COST;
        };
    }
}
