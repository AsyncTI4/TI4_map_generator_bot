package ti4.ai.scoring;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.StrategyCard;
import ti4.ai.eval.CustodiansRace;
import ti4.ai.trace.Thinking;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Helper;

@UtilityClass
public class ScoringReserve {

    private static final double CUSTODIANS_RACE_CHANCE = 0.5;

    public record Reserved(String objectiveId, SpendCost cost, int expectedTradeGoods) {}

    public static SpendCost of(Game game, Player seat) {
        if (!isActionPhase(game)) return SpendCost.NONE;
        try (Thinking.Span ignored = Thinking.enter("scoring.reserve")) {
            SpendCost reserve = reserved(game, seat).map(Reserved::cost).orElse(SpendCost.NONE);
            SpendCost custodians = timed("scoring.custodians", () -> custodiansReserve(game, seat));
            SpendCost promised = timed("scoring.promised", () -> SpendCost.tradeGoods(PromisedTradeGoods.of(seat)));
            return custodians.plus(reserve).plus(promised);
        }
    }

    private static <T> T timed(String span, Supplier<T> work) {
        try (Thinking.Span ignored = Thinking.enter(span)) {
            return work.get();
        }
    }

    public static double reservedWorth(Game game, Player seat) {
        return reserved(game, seat)
                .map(spend -> ScoringStakes.scoreValue(game, seat, ObjectivePolicy.victoryPoints(spend.objectiveId())))
                .orElse(0.0);
    }

    public static Optional<Reserved> reserved(Game game, Player seat) {
        if (!isActionPhase(game) || !Helper.canPlayerScorePOs(game, seat)) {
            return Optional.empty();
        }
        if (!timed("scoring.winRace", () -> WinRace.winnersBefore(game, seat)).isEmpty()) return Optional.empty();
        Wallet wallet = timed(
                "scoring.wallet",
                () -> custodiansClaim(game, seat)
                        .map(Wallet.of(game, seat)::without)
                        .orElseGet(() -> Wallet.of(game, seat)));
        Optional<Reserved> spend = timed("scoring.affordable", () -> bestAffordableSpend(game, seat, wallet))
                .or(() -> timed("scoring.projected", () -> bestProjectedSpend(game, seat, wallet)));
        if (spend.isEmpty()) return Optional.empty();
        int spendPoints = ObjectivePolicy.victoryPoints(spend.get().objectiveId());
        long freeScoresWorthAsMuch = timed(
                "scoring.scorable",
                () -> ObjectivePolicy.scorablePublics(game, seat).stream()
                        .filter(id -> !ObjectiveCatalog.isSpend(id))
                        .filter(id -> ObjectivePolicy.victoryPoints(id) >= spendPoints)
                        .count());
        return freeScoresWorthAsMuch >= scoringChances(game, seat) ? Optional.empty() : spend;
    }

    public static SpendCost custodiansReserve(Game game, Player seat) {
        return custodiansClaim(game, seat).isPresent() ? SpendCost.influence(CustodiansRace.COST) : SpendCost.NONE;
    }

    public static Optional<Wallet.Payment> custodiansClaim(Game game, Player seat) {
        if (!isActionPhase(game) || !CustodiansRace.open(game)) return Optional.empty();
        CustodiansRace.Snapshot race = CustodiansRace.of(game, seat);
        if (!race.canLandThisRound(seat)) return Optional.empty();
        if (!holdsUnplayedImperial(game, seat) && !winsTheCustodiansRace(race, seat)) return Optional.empty();
        return planForCustodians(game, seat);
    }

    public static Optional<Wallet.Payment> planForCustodians(Game game, Player seat) {
        SpendCost promised = SpendCost.tradeGoods(PromisedTradeGoods.of(seat));
        return planAfterReserve(Wallet.of(game, seat), promised, SpendCost.influence(CustodiansRace.COST));
    }

    private static boolean winsTheCustodiansRace(CustodiansRace.Snapshot race, Player seat) {
        return race.arrival(seat).stream()
                .anyMatch(
                        arrival -> race.winChance(seat, arrival, CustodiansRace.Order.KNOWN) >= CUSTODIANS_RACE_CHANCE);
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
        return holdsUnplayedImperial(game, seat) ? 2 : 1;
    }

    private static boolean holdsUnplayedImperial(Game game, Player seat) {
        return seat.getSCs().stream()
                .anyMatch(card -> StrategyCard.of(game, card) == StrategyCard.IMPERIAL
                        && !game.getPlayedSCs().contains(card));
    }

    private static Optional<Reserved> bestAffordableSpend(Game game, Player seat, Wallet wallet) {
        return game.getRevealedPublicObjectives().keySet().stream()
                .filter(id -> !ObjectivePolicy.hasScored(game, seat, id))
                .map(id -> ObjectiveCatalog.spendCost(id).map(cost -> new Reserved(id, cost, 0)))
                .flatMap(Optional::stream)
                .filter(candidate -> wallet.canPay(candidate.cost()))
                .max(Comparator.comparingInt(
                                (Reserved candidate) -> ObjectivePolicy.victoryPoints(candidate.objectiveId()))
                        .thenComparingInt(candidate -> -weight(candidate.cost())));
    }

    public static int expectedTradeGoodsCounted(Game game, Player seat) {
        return reserved(game, seat).map(Reserved::expectedTradeGoods).orElse(0);
    }

    private static Optional<Reserved> bestProjectedSpend(Game game, Player seat, Wallet wallet) {
        int expected = timed("scoring.expectedTradeGoods", () -> ExpectedTradeGoods.beforeStatus(game, seat));
        List<Wallet.PlanetValue> readied =
                timed("scoring.expectedReadying", () -> ExpectedReadying.beforeStatus(game, seat));
        if (expected <= 0 && readied.isEmpty()) return Optional.empty();
        Wallet projected = new Wallet(
                concat(wallet.planets(), readied),
                wallet.tradeGoods() + expected,
                wallet.tacticTokens(),
                wallet.strategyTokens(),
                wallet.tradeGoodValue(),
                wallet.pooling());
        return bestAffordableSpend(game, seat, projected)
                .flatMap(spend -> netOfIncome(spend, expected, readied, wallet));
    }

    private static Optional<Reserved> netOfIncome(
            Reserved spend, int expected, List<Wallet.PlanetValue> readied, Wallet wallet) {
        int tradeGoodValue = wallet.tradeGoodValue();
        SpendCost afterReadying = ExpectedReadying.netOf(spend.cost(), readied, wallet.pooling());
        return ExpectedTradeGoods.netOf(afterReadying, expected, tradeGoodValue, wallet::canPay)
                .map(net -> new Reserved(
                        spend.objectiveId(),
                        net,
                        ExpectedTradeGoods.tradeGoodsUsed(afterReadying, net, expected, tradeGoodValue)));
    }

    private static List<Wallet.PlanetValue> concat(List<Wallet.PlanetValue> first, List<Wallet.PlanetValue> second) {
        List<Wallet.PlanetValue> all = new ArrayList<>(first);
        second.stream()
                .filter(planet -> all.stream().noneMatch(held -> held.name().equals(planet.name())))
                .forEach(all::add);
        return all;
    }

    private static int weight(SpendCost cost) {
        return cost.tradeGoods() + cost.tokens();
    }

    private static boolean isActionPhase(Game game) {
        return "action".equalsIgnoreCase(game.getPhaseOfGame());
    }
}
