package ti4.ai.strategy;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.StrategyCard;
import ti4.ai.scoring.ScoringReserve;
import ti4.ai.scoring.SpendCost;
import ti4.ai.scoring.SpendUnlock;
import ti4.ai.scoring.Wallet;
import ti4.ai.tactical.ProductionPlanner;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;

@UtilityClass
class DiplomacyUse {

    static final int PLANETS_TO_READY = 2;
    private static final int PAIR_CANDIDATES = 8;
    private static final int MIN_DIPLOMACY_USE = 3;
    private static final int TOKENS_TO_FOLLOW = 1;
    private static final int MAX_CACHED = 64;
    private static final Map<String, Cached> CACHE = new ConcurrentHashMap<>();

    record Choice(List<String> planets, int use, boolean unlocksScore) {}

    private record Cached(WeakReference<Game> game, long modified, boolean worth) {

        boolean isFor(Game current) {
            return game.get() == current && modified == current.getLastModifiedDate();
        }
    }

    private record Split(List<String> forResources, List<String> forInfluence) {}

    static boolean worthFollowing(Game game, Player seat) {
        String key = game.getName() + "|" + seat.getUserID();
        Cached cached = CACHE.get(key);
        if (cached != null && cached.isFor(game)) return cached.worth();
        boolean worth = bestToFollow(game, seat)
                .filter(choice -> choice.unlocksScore() || choice.use() >= MIN_DIPLOMACY_USE)
                .isPresent();
        if (CACHE.size() > MAX_CACHED) CACHE.clear();
        CACHE.put(key, new Cached(new WeakReference<>(game), game.getLastModifiedDate(), worth));
        return worth;
    }

    static Optional<Choice> bestToFollow(Game game, Player seat) {
        return best(game, seat, seat.getExhaustedPlanets(), PLANETS_TO_READY, TOKENS_TO_FOLLOW);
    }

    static Optional<Choice> best(Game game, Player seat, Collection<String> candidates) {
        return best(game, seat, candidates, PLANETS_TO_READY);
    }

    static Optional<Choice> best(Game game, Player seat, Collection<String> candidates, int count) {
        return best(game, seat, candidates, count, 0);
    }

    private static Optional<Choice> best(
            Game game, Player seat, Collection<String> candidates, int count, int diplomacyTokens) {
        List<String> ranked = candidates.stream()
                .distinct()
                .sorted(Comparator.comparingInt((String planet) -> -higherValue(game, planet))
                        .thenComparing(Comparator.naturalOrder()))
                .limit(PAIR_CANDIDATES)
                .toList();
        int size = Math.min(Math.min(count, PLANETS_TO_READY), ranked.size());
        if (size < 1) return Optional.empty();
        Demand demand = new Demand(game, seat, diplomacyTokens);
        Comparator<Choice> ranking = Comparator.comparing(Choice::unlocksScore)
                .thenComparingInt(Choice::use)
                .thenComparingInt(choice -> totalValue(game, choice.planets()));
        return combinations(ranked, size).stream()
                .map(planets -> new Choice(planets, demand.use(planets), SpendUnlock.unlocks(game, seat, planets, 0)))
                .max(ranking);
    }

    private static List<List<String>> combinations(List<String> planets, int size) {
        if (size == 1) return planets.stream().map(List::of).toList();
        List<List<String>> pairs = new ArrayList<>();
        for (int first = 0; first < planets.size(); first++) {
            for (int second = first + 1; second < planets.size(); second++) {
                pairs.add(List.of(planets.get(first), planets.get(second)));
            }
        }
        return pairs;
    }

    private static List<Split> splits(List<String> planets) {
        List<Split> splits = new ArrayList<>();
        splits.add(new Split(List.of(), List.of()));
        for (String planet : planets) {
            List<Split> extended = new ArrayList<>();
            for (Split split : splits) {
                extended.add(new Split(append(split.forResources(), planet), split.forInfluence()));
                extended.add(new Split(split.forResources(), append(split.forInfluence(), planet)));
            }
            splits = extended;
        }
        return splits;
    }

    private static List<String> append(List<String> planets, String planet) {
        List<String> appended = new ArrayList<>(planets);
        appended.add(planet);
        return List.copyOf(appended);
    }

    private static int higherValue(Game game, String planet) {
        return Math.max(Helper.getPlanetResources(planet, game), Helper.getPlanetInfluence(planet, game));
    }

    private static int totalValue(Game game, List<String> planets) {
        return planets.stream()
                .mapToInt(planet -> Helper.getPlanetResources(planet, game) + Helper.getPlanetInfluence(planet, game))
                .sum();
    }

    private static boolean holdsUnplayed(Game game, Player holder, StrategyCard card) {
        return holder.getSCs().stream()
                .anyMatch(initiative ->
                        !game.getPlayedSCs().contains(initiative) && StrategyCard.of(game, initiative) == card);
    }

    private static boolean anotherHoldsUnplayed(Game game, Player seat, StrategyCard card) {
        return game.getRealPlayers().stream()
                .filter(player -> !player.getUserID().equals(seat.getUserID()))
                .anyMatch(player -> holdsUnplayed(game, player, card));
    }

    private static final class Demand {
        private final Game game;
        private final Player seat;
        private final boolean active;
        private final Wallet wallet;
        private final SpendCost reserve;
        private final List<Tile> docks;
        private final int researchCost;
        private final boolean leadership;
        private final int reinforcements;
        private final int tokensNow;
        private final boolean custodians;
        private final Map<String, Double> productionCosts = new HashMap<>();
        private final Map<List<String>, Integer> purchasedTokens = new HashMap<>();

        Demand(Game game, Player seat, int diplomacyTokens) {
            this.game = game;
            this.seat = seat;
            this.active = !seat.isPassed();
            this.wallet = Wallet.of(game, seat);
            this.reserve = active ? ScoringReserve.of(game, seat) : SpendCost.NONE;
            boolean hasTacticToken = seat.getTacticalCC() >= 1;
            this.docks = active && hasTacticToken ? activatableDocks() : List.of();
            this.researchCost = active ? researchCost(diplomacyTokens) : 0;
            this.leadership = active
                    && (holdsUnplayed(game, seat, StrategyCard.LEADERSHIP)
                            || anotherHoldsUnplayed(game, seat, StrategyCard.LEADERSHIP));
            this.reinforcements = leadership ? leadershipReinforcements() : 0;
            this.tokensNow = leadership ? tokensBought(wallet) : 0;
            this.custodians = active
                    && hasTacticToken
                    && !affords(wallet, SpendCost.influence(TokenPurchase.CUSTODIANS_COST))
                    && TokenPurchase.custodiansWithinReach(game, seat);
        }

        int use(List<String> planets) {
            if (!active) return 0;
            return splits(planets).stream().mapToInt(this::use).max().orElse(0);
        }

        private int use(Split split) {
            int resources = split.forResources().stream()
                    .mapToInt(planet -> Helper.getPlanetResources(planet, game))
                    .sum();
            int influence = split.forInfluence().stream()
                    .mapToInt(planet -> Helper.getPlanetInfluence(planet, game))
                    .sum();
            int resourceUse = Math.min(resources, productionUse(resources) + researchUse(split.forResources()));
            int influenceUse =
                    Math.min(influence, leadershipUse(split.forInfluence()) + custodiansUse(split.forInfluence()));
            return resourceUse + influenceUse;
        }

        private int productionUse(int resources) {
            if (resources <= 0) return 0;
            int best = 0;
            for (Tile dock : docks) {
                double extra = productionCost(dock, resources) - productionCost(dock, 0);
                best = Math.max(best, (int) Math.ceil(extra));
            }
            return best;
        }

        private double productionCost(Tile dock, int extraResources) {
            return productionCosts.computeIfAbsent(
                    dock.getPosition() + "|" + extraResources,
                    ignored -> ProductionPlanner.plan(game, seat, dock, extraResources)
                            .totalCost());
        }

        private int researchUse(List<String> forResources) {
            if (researchCost == 0 || forResources.isEmpty()) return 0;
            boolean affordable = affords(wallet.withPlanets(game, forResources), SpendCost.resources(researchCost));
            return affordable ? researchCost : 0;
        }

        private int researchCost(int diplomacyTokens) {
            if (anotherHoldsUnplayed(game, seat, StrategyCard.TECHNOLOGY) && keepsTokenToFollow(diplomacyTokens)) {
                return costWorthFunding(StrategyCardRules.FOLLOW_TECH_COST, ResearchPolicy.WORTH_FOLLOWING_FOR);
            }
            if (holdsUnplayed(game, seat, StrategyCard.TECHNOLOGY) && !StrategyCardRules.cannotResearch(seat)) {
                return costWorthFunding(StrategyCardRules.SECOND_TECH_COST, ResearchPolicy.WORTH_PAYING_FOR);
            }
            return 0;
        }

        private boolean keepsTokenToFollow(int diplomacyTokens) {
            int strategyTokens = seat.getStrategicCC() - diplomacyTokens;
            return strategyTokens >= 1 && strategyTokens + seat.getTacticalCC() - 1 >= reserve.tokens();
        }

        private int costWorthFunding(int cost, double worth) {
            if (affords(wallet, SpendCost.resources(cost))) return 0;
            boolean wanted = StrategyCardRules.cannotResearch(seat)
                    ? StrategyCardRules.propagationWorthFollowing(game, seat)
                    : StrategyCardRules.worthResearching(game, seat, worth);
            return wanted ? cost : 0;
        }

        private int leadershipReinforcements() {
            int reinforcements = StrategyCardRules.reinforcements(game, seat);
            if (anotherHoldsUnplayed(game, seat, StrategyCard.LEADERSHIP)) return reinforcements;
            return Math.max(0, reinforcements - StrategyCardRules.LEADERSHIP_TOKENS);
        }

        private int leadershipUse(List<String> forInfluence) {
            if (!leadership || forInfluence.isEmpty()) return 0;
            int tokens = purchasedTokens.computeIfAbsent(
                    forInfluence, planets -> tokensBought(wallet.withPlanets(game, planets)));
            return TokenPurchase.INFLUENCE_PER_TOKEN * Math.max(0, tokens - tokensNow);
        }

        private int custodiansUse(List<String> forInfluence) {
            if (!custodians || forInfluence.isEmpty()) return 0;
            SpendCost cost = SpendCost.influence(TokenPurchase.CUSTODIANS_COST);
            return affords(wallet.withPlanets(game, forInfluence), cost) ? TokenPurchase.CUSTODIANS_COST : 0;
        }

        private int tokensBought(Wallet funds) {
            return TokenPurchase.best(game, seat, reinforcements, funds)
                    .map(TokenPurchase.Purchase::tokens)
                    .orElse(0);
        }

        private boolean affords(Wallet funds, SpendCost cost) {
            return ScoringReserve.planAfterReserve(funds, reserve, cost).isPresent();
        }

        private List<Tile> activatableDocks() {
            return ButtonHelper.getTilesOfPlayersSpecificUnits(game, seat, UnitType.Spacedock).stream()
                    .filter(tile -> !tile.hasPlayerCC(seat))
                    .toList();
        }
    }
}
