package ti4.ai.strategy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import ti4.ai.AiExperiments;
import ti4.ai.actioncards.ActionCardRules;
import ti4.ai.brain.Reentry;
import ti4.ai.brain.StrategyCard;
import ti4.ai.eval.CustodiansRace;
import ti4.ai.expedition.ExpeditionUnlock;
import ti4.ai.scoring.ExpectedReadying;
import ti4.ai.scoring.Pooling;
import ti4.ai.scoring.ScoringReserve;
import ti4.ai.scoring.SpendCost;
import ti4.ai.scoring.Wallet;
import ti4.ai.tactical.ProductionPlanner;
import ti4.ai.tactical.ProductionPlanner.BuildOrder;
import ti4.ai.tactical.ProductionPlanner.BuildPlan;
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
    private static final String ESTIMATING_PLANS_GUARD = "strategy.estimatingPlans";

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
        SpendCost claimNow = ScoringReserve.custodiansReserve(game, seat);
        SpendCost followNow = technologyFollowToKeep(game, seat, wallet);
        for (int tokens = Math.min(maxTokens(game, seat), reinforcements); tokens > 0; tokens--) {
            Optional<Wallet.Payment> payment = influencePlanets
                    .plan(SpendCost.influence(INFLUENCE_PER_TOKEN * tokens))
                    .filter(plan -> ExpectedReadying.afterPaying(game, seat, wallet, plan)
                            .canPay(keep))
                    .filter(plan -> wallet.without(plan).canPay(claimNow.plus(followNow)));
            if (payment.isPresent()) return Optional.of(new Purchase(tokens, payment.get()));
        }
        return Optional.empty();
    }

    static boolean estimatingPlans() {
        return Reentry.cutsShort(ESTIMATING_PLANS_GUARD, ESTIMATING_PLANS.get());
    }

    static SpendCost technologyFollowToKeep(Game game, Player seat, Wallet wallet) {
        if (!AiExperiments.enabled(seat, AiExperiments.Experiment.TECH_FOLLOW_KEEP)) return SpendCost.NONE;
        if (estimatingPlans()) return SpendCost.NONE;
        ESTIMATING_PLANS.set(true);
        Reentry.enter(ESTIMATING_PLANS_GUARD);
        try {
            SpendCost follow = TechnologyFollowReserve.of(game, seat);
            return wallet.canPay(follow) ? follow : SpendCost.NONE;
        } finally {
            ESTIMATING_PLANS.set(false);
            Reentry.exit(ESTIMATING_PLANS_GUARD);
        }
    }

    private static SpendCost kept(Game game, Player seat, Wallet wallet) {
        SpendCost scoring = ScoringReserve.of(game, seat).plus(custodiansReserve(game, seat));
        if (estimatingPlans()) return scoring;
        ESTIMATING_PLANS.set(true);
        Reentry.enter(ESTIMATING_PLANS_GUARD);
        try {
            return withPlans(game, seat, wallet, scoring);
        } finally {
            ESTIMATING_PLANS.set(false);
            Reentry.exit(ESTIMATING_PLANS_GUARD);
        }
    }

    private static SpendCost withPlans(Game game, Player seat, Wallet wallet, SpendCost scoring) {
        SpendCost research =
                SpendCost.resources(extraResearchCost(game, seat)).plus(TechnologyFollowReserve.of(game, seat));
        return keep(wallet, scoring, dockPlans(game, seat), research);
    }

    static SpendCost keep(Wallet wallet, SpendCost scoring, List<BuildPlan> builds, SpendCost research) {
        List<BuildPlan> worthBuilding =
                builds.stream().filter(TokenPurchase::worthAnActivation).toList();
        Stream<SpendCost> fullBuild = worthBuilding.stream()
                .max(Comparator.comparingDouble(BuildPlan::totalCost))
                .map(TokenPurchase::resourcesFor)
                .stream()
                .flatMap(build -> Stream.of(scoring.plus(build).plus(research), scoring.plus(build)));
        Stream<SpendCost> partialBuild =
                largestAffordablePart(wallet, scoring, worthBuilding)
                        .map(TokenPurchase::resourcesFor)
                        .map(scoring::plus)
                        .stream();
        return Stream.of(fullBuild, partialBuild, Stream.of(scoring.plus(research)))
                .flatMap(candidates -> candidates)
                .filter(wallet::canPay)
                .findFirst()
                .orElse(scoring);
    }

    static boolean worthAnActivation(BuildPlan plan) {
        if (plan.isEmpty()) return false;
        return plan.scoring()
                || plan.defense()
                || plan.units() >= ProductionPlanner.MIN_UNITS
                || plan.totalCost() >= ProductionPlanner.MIN_BUILD_RESOURCES;
    }

    static BuildPlan affordablePart(BuildPlan plan, int resources) {
        List<BuildOrder> kept = new ArrayList<>();
        double spent = 0;
        for (BuildOrder order : plan.orders()) {
            if (spent + order.cost() > resources) continue;
            kept.add(order);
            spent += order.cost();
        }
        return new BuildPlan(kept);
    }

    private static Optional<BuildPlan> largestAffordablePart(
            Wallet wallet, SpendCost scoring, List<BuildPlan> worthBuilding) {
        int mostCost = worthBuilding.stream()
                .mapToInt(plan -> resourcesFor(plan).resources())
                .max()
                .orElse(0);
        int room = resourcesAfter(wallet, scoring, mostCost);
        if (room <= 0) return Optional.empty();
        return worthBuilding.stream()
                .map(plan -> affordablePart(plan, room))
                .filter(TokenPurchase::worthAnActivation)
                .max(Comparator.comparingDouble(BuildPlan::value));
    }

    private static int resourcesAfter(Wallet wallet, SpendCost scoring, int most) {
        for (int resources = most; resources > 0; resources--) {
            if (wallet.canPay(scoring.plus(SpendCost.resources(resources)))) return resources;
        }
        return 0;
    }

    private static SpendCost resourcesFor(BuildPlan plan) {
        return SpendCost.resources((int) Math.ceil(plan.totalCost()));
    }

    private static List<BuildPlan> dockPlans(Game game, Player seat) {
        return ButtonHelper.getTilesOfPlayersSpecificUnits(game, seat, UnitType.Spacedock).stream()
                .filter(dock -> !dock.hasPlayerCC(seat))
                .map(dock -> ProductionPlanner.plan(game, seat, dock))
                .toList();
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
        if (!ScoringReserve.contendsForCustodians(game, seat)) return SpendCost.NONE;
        return SpendCost.influence(ScoringReserve.custodiansInfluenceHeld(CustodiansRace.of(game, seat), seat));
    }

    static boolean custodiansWithinReach(Game game, Player seat) {
        return CustodiansRace.open(game) && CustodiansRace.of(game, seat).canLandThisRound(seat);
    }
}
