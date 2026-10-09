package ti4.ai.scoring;

import java.util.Comparator;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.StrategyCard;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Helper;

@UtilityClass
public class ScoringReserve {

    public record Reserved(String objectiveId, SpendCost cost) {}

    public static SpendCost of(Game game, Player seat) {
        if (!isActionPhase(game)) return SpendCost.NONE;
        SpendCost reserve = reserved(game, seat).map(Reserved::cost).orElse(SpendCost.NONE);
        return reserve.plus(SpendCost.tradeGoods(PromisedTradeGoods.of(seat)));
    }

    public static Optional<Reserved> reserved(Game game, Player seat) {
        if (!isActionPhase(game) || !Helper.canPlayerScorePOs(game, seat)) {
            return Optional.empty();
        }
        Optional<Reserved> spend = bestAffordableSpend(game, seat, Wallet.of(game, seat));
        if (spend.isEmpty()) return Optional.empty();
        int spendPoints = ObjectivePolicy.victoryPoints(spend.get().objectiveId());
        long freeScoresWorthAsMuch = ObjectivePolicy.scorablePublics(game, seat).stream()
                .filter(id -> !ObjectiveCatalog.isSpend(id))
                .filter(id -> ObjectivePolicy.victoryPoints(id) >= spendPoints)
                .count();
        return freeScoresWorthAsMuch >= scoringChances(game, seat) ? Optional.empty() : spend;
    }

    public static Optional<Wallet.Payment> planAfterReserve(Game game, Player seat, SpendCost cost) {
        return planAfterReserve(Wallet.of(game, seat), of(game, seat), cost);
    }

    public static Optional<Wallet.Payment> planAfterReserve(Wallet wallet, SpendCost reserve, SpendCost cost) {
        Optional<Wallet.Payment> direct =
                wallet.plan(cost).filter(payment -> wallet.without(payment).canPay(reserve));
        if (direct.isPresent()) return direct;
        return wallet.plan(reserve).flatMap(kept -> wallet.without(kept).plan(cost));
    }

    public static int scoringChances(Game game, Player seat) {
        boolean holdsUnplayedImperial = seat.getSCs().stream()
                .anyMatch(card -> StrategyCard.of(game, card) == StrategyCard.IMPERIAL
                        && !game.getPlayedSCs().contains(card));
        return holdsUnplayedImperial ? 2 : 1;
    }

    private static Optional<Reserved> bestAffordableSpend(Game game, Player seat, Wallet wallet) {
        return game.getRevealedPublicObjectives().keySet().stream()
                .filter(id -> !ObjectivePolicy.hasScored(game, seat, id))
                .map(id -> ObjectiveCatalog.spendCost(id).map(cost -> new Reserved(id, cost)))
                .flatMap(Optional::stream)
                .filter(candidate -> wallet.canPay(candidate.cost()))
                .max(Comparator.comparingInt(
                                (Reserved candidate) -> ObjectivePolicy.victoryPoints(candidate.objectiveId()))
                        .thenComparingInt(candidate -> -weight(candidate.cost())));
    }

    private static int weight(SpendCost cost) {
        return cost.tradeGoods() + cost.tokens();
    }

    private static boolean isActionPhase(Game game) {
        return "action".equalsIgnoreCase(game.getPhaseOfGame());
    }
}
