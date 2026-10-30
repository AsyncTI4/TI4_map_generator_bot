package ti4.ai.strategy;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import ti4.ai.actioncards.ActionCardRules;
import ti4.ai.brain.StrategyCard;
import ti4.ai.eval.CustodiansRace;
import ti4.ai.expedition.ExpeditionUnlock;
import ti4.ai.scoring.Pooling;
import ti4.ai.scoring.ScoringReserve;
import ti4.ai.scoring.SpendCost;
import ti4.ai.scoring.Wallet;
import ti4.ai.tactical.ProductionPlanner;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitType;

@UtilityClass
public class TokenPurchase {

    static final int INFLUENCE_PER_TOKEN = 3;
    static final int MAX_TOKENS = 3;
    static final int CUSTODIANS_COST = 6;
    private static final ThreadLocal<Boolean> ESTIMATING_PLANS = ThreadLocal.withInitial(() -> false);

    record Purchase(int tokens, Wallet.Payment payment) {}

    public static SpendCost influenceToKeep(Game game, Player seat, SpendCost scoringReserve) {
        return custodiansToKeep(game, seat, scoringReserve)
                .plus(pendingLeadershipPurchase(game, seat, Wallet.of(game, seat)));
    }

    public static SpendCost custodiansToKeep(Game game, Player seat, SpendCost scoringReserve) {
        SpendCost custodians = custodiansReserve(game, seat);
        return Wallet.of(game, seat).canPay(scoringReserve.plus(custodians)) ? custodians : SpendCost.NONE;
    }

    private static SpendCost pendingLeadershipPurchase(Game game, Player seat, Wallet wallet) {
        Optional<Integer> leadership = game.getRealPlayers().stream()
                .flatMap(player -> player.getSCs().stream())
                .filter(card -> StrategyCard.of(game, card) == StrategyCard.LEADERSHIP)
                .filter(card -> !game.getPlayedSCs().contains(card) || !seat.hasFollowedSC(card))
                .findFirst();
        if (leadership.isEmpty()) return SpendCost.NONE;
        int reinforcements = StrategyCardRules.reinforcements(game, seat);
        boolean holdsIt = seat.getSCs().contains(leadership.get());
        if (holdsIt && game.getPlayedSCs().contains(leadership.get())) return SpendCost.NONE;
        int free = holdsIt ? Math.min(StrategyCardRules.LEADERSHIP_TOKENS, reinforcements) : 0;
        return best(game, seat, reinforcements - free, wallet)
                .map(purchase -> SpendCost.influence(INFLUENCE_PER_TOKEN * purchase.tokens()))
                .orElse(SpendCost.NONE);
    }

    static Optional<Purchase> best(Game game, Player seat, int reinforcements) {
        return best(game, seat, reinforcements, Wallet.of(game, seat));
    }

    static Optional<Purchase> best(Game game, Player seat, int reinforcements, Wallet wallet) {
        Optional<Purchase> spendingAll = purchase(game, seat, reinforcements, wallet);
        if (spendingAll.isEmpty()) return spendingAll;
        Optional<ExpeditionUnlock.Plan> expedition = ExpeditionUnlock.planWith(game, seat, wallet);
        if (expedition.isEmpty()) return spendingAll;
        Optional<Purchase> keeping = purchase(
                game, seat, reinforcements, wallet.without(expedition.get().payment()));
        int tokensGivenUp =
                spendingAll.get().tokens() - keeping.map(Purchase::tokens).orElse(0);
        return tokensGivenUp * CardValueScale.HELD_TOKEN < expedition.get().worth() ? keeping : spendingAll;
    }

    private static Optional<Purchase> purchase(Game game, Player seat, int reinforcements, Wallet wallet) {
        Wallet influencePlanets = new Wallet(
                influenceLeaning(wallet), wallet.tradeGoods(), 0, 0, wallet.tradeGoodValue(), wallet.pooling());
        SpendCost keep = kept(game, seat, wallet);
        for (int tokens = Math.min(maxTokens(game, seat), reinforcements); tokens > 0; tokens--) {
            Optional<Wallet.Payment> payment = influencePlanets
                    .plan(SpendCost.influence(INFLUENCE_PER_TOKEN * tokens))
                    .filter(plan -> wallet.without(plan).canPay(keep));
            if (payment.isPresent()) return Optional.of(new Purchase(tokens, payment.get()));
        }
        return Optional.empty();
    }

    private static SpendCost kept(Game game, Player seat, Wallet wallet) {
        SpendCost scoring = ScoringReserve.of(game, seat).plus(custodiansReserve(game, seat));
        if (ESTIMATING_PLANS.get()) return scoring;
        ESTIMATING_PLANS.set(true);
        try {
            return withPlans(game, seat, wallet, scoring);
        } finally {
            ESTIMATING_PLANS.set(false);
        }
    }

    private static SpendCost withPlans(Game game, Player seat, Wallet wallet, SpendCost scoring) {
        SpendCost build = SpendCost.resources(homeBuildCost(game, seat));
        SpendCost research =
                SpendCost.resources(extraResearchCost(game, seat)).plus(TechnologyFollowReserve.of(game, seat));
        return Stream.of(scoring.plus(build).plus(research), scoring.plus(build), scoring.plus(research))
                .filter(wallet::canPay)
                .findFirst()
                .orElse(scoring);
    }

    private static int homeBuildCost(Game game, Player seat) {
        return ButtonHelper.getTilesOfPlayersSpecificUnits(game, seat, UnitType.Spacedock).stream()
                .filter(dock -> !dock.hasPlayerCC(seat))
                .map(dock -> ProductionPlanner.plan(game, seat, dock))
                .filter(plan -> !plan.isEmpty())
                .mapToInt(plan -> (int) Math.ceil(plan.totalCost()))
                .max()
                .orElse(0);
    }

    private static int extraResearchCost(Game game, Player seat) {
        boolean wanted = TechnologyTiming.holdsUnplayed(game, seat, StrategyCard.TECHNOLOGY)
                && !StrategyCardRules.cannotResearch(seat)
                && StrategyCardRules.worthResearching(game, seat, ResearchPolicy.WORTH_PAYING_FOR);
        return wanted ? StrategyCardRules.SECOND_TECH_COST : 0;
    }

    private static int maxTokens(Game game, Player seat) {
        boolean strategizeLacksAToken =
                ActionCardRules.holdsPlayableStrategize(game, seat) && seat.getStrategicCC() < 1;
        return strategizeLacksAToken ? MAX_TOKENS + 1 : MAX_TOKENS;
    }

    private static List<Wallet.PlanetValue> influenceLeaning(Wallet wallet) {
        if (wallet.pooling() != Pooling.SEPARATE) return wallet.planets();
        return wallet.planets().stream()
                .filter(planet -> planet.influence() >= planet.resources())
                .toList();
    }

    private static SpendCost custodiansReserve(Game game, Player seat) {
        if (!ScoringReserve.custodiansReserve(game, seat).isNone()) return SpendCost.NONE;
        return custodiansWithinReach(game, seat) ? SpendCost.influence(CUSTODIANS_COST) : SpendCost.NONE;
    }

    static boolean custodiansWithinReach(Game game, Player seat) {
        return CustodiansRace.open(game) && CustodiansRace.of(game, seat).canLandThisRound(seat);
    }
}
