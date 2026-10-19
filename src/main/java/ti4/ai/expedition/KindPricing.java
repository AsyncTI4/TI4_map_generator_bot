package ti4.ai.expedition;

import java.lang.ref.WeakReference;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import ti4.ai.actioncards.ActionCardPrior;
import ti4.ai.actioncards.ActionCardValue;
import ti4.ai.eval.BoardView;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.scoring.ScoringReserve;
import ti4.ai.scoring.SpendCost;
import ti4.ai.scoring.SpendUnlock;
import ti4.ai.scoring.Wallet;
import ti4.ai.secrets.SecretValue;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;

@UtilityClass
class KindPricing {

    record Price(Wallet.Payment payment, double cost) {}

    static final int TRADE_GOODS_COST = 3;
    static final int RESOURCES_COST = 5;
    static final int INFLUENCE_COST = 5;
    static final int ACTION_CARDS_COST = 2;
    static final double SECRET_CARD_SHARE = 0.5;
    private static final int MAX_CACHED = 64;
    private static final Map<String, CachedKeeps> KEEPS = new ConcurrentHashMap<>();
    private static final String BIO_STIMS = "bs";
    private static final Wallet.Payment NO_PAYMENT = new Wallet.Payment(List.of(), List.of(), 0, 0);

    static Optional<Price> of(Game game, Player seat, ExpeditionKind kind, Holdings holdings, SpendHorizon horizon) {
        SpendCost reserve = ScoringReserve.of(game, seat);
        return switch (kind) {
            case TECH_SKIP -> techSkip(game, seat, holdings.wallet(), reserve, horizon);
            case TRADE_GOODS -> tradeGoods(holdings.wallet(), reserve);
            case FIVE_RES -> planetsFor(game, holdings.wallet(), reserve, SpendCost.resources(RESOURCES_COST), horizon);
            case FIVE_INF -> planetsFor(game, holdings.wallet(), reserve, SpendCost.influence(INFLUENCE_COST), horizon);
            case SECRET -> secret(game, seat);
            case ACTION_CARDS -> actionCards(game, seat, holdings);
        };
    }

    private static Optional<Price> techSkip(
            Game game, Player seat, Wallet wallet, SpendCost reserve, SpendHorizon horizon) {
        boolean bioStims = seat.hasTechReady(BIO_STIMS);
        boolean nothingExhausted = bioStims && exhaustedSkipPlanets(game, seat).isEmpty();
        return wallet.planets().stream()
                .filter(planet -> ButtonHelper.checkForTechSkips(game, planet.name()))
                .filter(planet -> bioStims || keepsReserve(wallet, reserve, planet))
                .map(planet -> new Price(
                        new Wallet.Payment(List.of(planet.name()), List.of(), 0, 0),
                        nothingExhausted ? 0 : techSkipCost(game, seat, planet, bioStims, horizon)))
                .min(Comparator.comparingDouble(Price::cost));
    }

    private static double techSkipCost(
            Game game, Player seat, Wallet.PlanetValue planet, boolean bioStims, SpendHorizon horizon) {
        double cost = exhaustCost(planet, horizon) + exhaustedPoints(game, seat, planet);
        if (!bioStims) return cost;
        double readiedInstead = exhaustedSkipPlanets(game, seat).stream()
                .mapToDouble(name -> exhaustCost(game, name, horizon))
                .max()
                .orElse(cost);
        return Math.min(cost, readiedInstead);
    }

    private static List<String> exhaustedSkipPlanets(Game game, Player seat) {
        return seat.getExhaustedPlanets().stream()
                .filter(name -> ButtonHelper.checkForTechSkips(game, name))
                .toList();
    }

    private static double exhaustedPoints(Game game, Player seat, Wallet.PlanetValue planet) {
        return ObjectiveValue.VICTORY_POINT_VALUE * SpendUnlock.pointsLostByExhausting(game, seat, planet.name());
    }

    private static boolean keepsReserve(Wallet wallet, SpendCost reserve, Wallet.PlanetValue planet) {
        Wallet.Payment payment = new Wallet.Payment(List.of(planet.name()), List.of(), 0, 0);
        return wallet.without(payment).canPay(reserve);
    }

    private static Optional<Price> tradeGoods(Wallet wallet, SpendCost reserve) {
        if (wallet.tradeGoods() < TRADE_GOODS_COST) return Optional.empty();
        Wallet.Payment payment = new Wallet.Payment(List.of(), List.of(), TRADE_GOODS_COST, 0);
        if (!wallet.without(payment).canPay(reserve)) return Optional.empty();
        return Optional.of(new Price(payment, TRADE_GOODS_COST));
    }

    private static Optional<Price> planetsFor(
            Game game, Wallet wallet, SpendCost reserve, SpendCost cost, SpendHorizon horizon) {
        return ScoringReserve.planAfterReserve(wallet, reserve, cost)
                .map(payment -> new Price(payment, paymentCost(wallet, payment, horizon)));
    }

    static double paymentCost(Wallet wallet, Wallet.Payment payment, SpendHorizon horizon) {
        double planets = Stream.concat(
                        payment.forResources().stream()
                                .map(name -> horizon.perResource()
                                        * planetOf(wallet, name).resources()),
                        payment.forInfluence().stream()
                                .map(name -> horizon.perInfluence()
                                        * planetOf(wallet, name).influence()))
                .mapToDouble(Double::doubleValue)
                .sum();
        return planets + payment.tradeGoods();
    }

    private static Wallet.PlanetValue planetOf(Wallet wallet, String name) {
        return wallet.planets().stream()
                .filter(planet -> planet.name().equals(name))
                .findFirst()
                .orElse(new Wallet.PlanetValue(name, 0, 0));
    }

    private static double exhaustCost(Wallet.PlanetValue planet, SpendHorizon horizon) {
        return Math.max(horizon.perResource() * planet.resources(), horizon.perInfluence() * planet.influence());
    }

    private static double exhaustCost(Game game, String planet, SpendHorizon horizon) {
        return exhaustCost(
                new Wallet.PlanetValue(
                        planet, BoardView.planetResources(game, planet), BoardView.planetInfluence(game, planet)),
                horizon);
    }

    private static Optional<Price> secret(Game game, Player seat) {
        Optional<String> worst = SecretValue.lowestValueSecret(game, seat);
        if (worst.isEmpty()) return Optional.empty();
        if (hasExcessSecret(seat)) return Optional.of(new Price(NO_PAYMENT, 0));
        double keep = Math.clamp(SecretValue.keepValue(game, seat, worst.get()), 0, 1);
        return Optional.of(new Price(NO_PAYMENT, keep * ObjectiveValue.VICTORY_POINT_VALUE * SECRET_CARD_SHARE));
    }

    private static boolean hasExcessSecret(Player seat) {
        return seat.getSecretsUnscored().size() > Math.max(0, seat.getMaxSOCount() - seat.getSoScored());
    }

    private record CachedKeeps(WeakReference<Game> game, long modified, List<Double> keeps) {

        boolean isFor(Game current) {
            return game.get() == current && modified == current.getLastModifiedDate();
        }
    }

    private static List<Double> keepValues(Game game, Player seat) {
        String key = game.getName() + "|" + seat.getUserID();
        CachedKeeps cached = KEEPS.get(key);
        if (cached != null && cached.isFor(game)) return cached.keeps();
        List<Double> keeps = ActionCardValue.discardOrder(game, seat).stream()
                .map(alias -> ActionCardValue.keepValue(game, seat, alias))
                .toList();
        if (KEEPS.size() > MAX_CACHED) KEEPS.clear();
        KEEPS.put(key, new CachedKeeps(new WeakReference<>(game), game.getLastModifiedDate(), keeps));
        return keeps;
    }

    private static Optional<Price> actionCards(Game game, Player seat, Holdings holdings) {
        if (holdings.actionCards() < ACTION_CARDS_COST) return Optional.empty();
        List<Double> keeps = keepValues(game, seat);
        int extra = Math.max(0, holdings.actionCards() - keeps.size());
        double cost = Stream.concat(
                        keeps.stream(),
                        Stream.generate(() -> ActionCardPrior.AVERAGE_CARD).limit(extra))
                .sorted()
                .limit(ACTION_CARDS_COST)
                .mapToDouble(Double::doubleValue)
                .sum();
        return Optional.of(new Price(NO_PAYMENT, cost));
    }
}
