package ti4.ai.explore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.scoring.SpendCost;
import ti4.ai.scoring.Wallet;
import ti4.ai.scoring.Wallet.PlanetValue;
import ti4.ai.tactical.ProductionPlanner;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class FreelancersPayment {

    public record Quote(Wallet.Payment payment, double cost) {}

    private static final double INFLUENCE_OPPORTUNITY = 0.25;
    private static final int MAX_PLANETS_CONSIDERED = 12;

    public static int capacity(Game game, Player seat) {
        int planets = Wallet.of(game, seat).planets().stream()
                .mapToInt(FreelancersPayment::capacityOf)
                .sum();
        return planets + seat.getTg() * Wallet.tradeGoodValue(seat);
    }

    public static Optional<Quote> cheapest(ExploreOutlook outlook, int need) {
        Game game = outlook.game();
        Player seat = outlook.seat();
        Wallet wallet = Wallet.of(game, seat);
        SpendCost reserve = ProductionPlanner.reserve(game, seat);
        double perResource = ProductionPlanner.fillerValuePerResource(game)
                * (outlook.willSpendMore() ? 1 : ExploreValues.UNSPENT_READY_SHARE);
        List<PlanetValue> planets = wallet.planets().stream()
                .filter(planet -> capacityOf(planet) > 0)
                .sorted(Comparator.comparingDouble(planet -> opportunity(planet, perResource) / capacityOf(planet)))
                .limit(MAX_PLANETS_CONSIDERED)
                .toList();
        Quote best = null;
        for (int tradeGoods = 0; tradeGoods <= Math.min(seat.getTg(), need); tradeGoods++) {
            int stillNeeded = need - tradeGoods * Wallet.tradeGoodValue(seat);
            Quote quote = cheapestPlanets(wallet, reserve, planets, perResource, stillNeeded, tradeGoods);
            if (quote != null && (best == null || quote.cost() < best.cost())) best = quote;
        }
        return Optional.ofNullable(best);
    }

    private static Quote cheapestPlanets(
            Wallet wallet,
            SpendCost reserve,
            List<PlanetValue> planets,
            double perResource,
            int stillNeeded,
            int tradeGoods) {
        double tradeGoodCost = tradeGoods * ExploreValues.TRADE_GOOD;
        Quote best = null;
        for (int mask = 0; mask < 1 << planets.size(); mask++) {
            int capacity = 0;
            double cost = tradeGoodCost;
            List<String> chosen = new ArrayList<>();
            for (int index = 0; index < planets.size(); index++) {
                if ((mask & 1 << index) == 0) continue;
                PlanetValue planet = planets.get(index);
                capacity += capacityOf(planet);
                cost += opportunity(planet, perResource);
                chosen.add(planet.name());
            }
            if (capacity < stillNeeded || (best != null && cost >= best.cost())) continue;
            Wallet.Payment payment = new Wallet.Payment(chosen, List.of(), tradeGoods, 0);
            if (wallet.without(payment).canPay(reserve)) best = new Quote(payment, cost);
        }
        return best;
    }

    private static int capacityOf(PlanetValue planet) {
        return Math.max(planet.resources(), planet.influence());
    }

    private static double opportunity(PlanetValue planet, double perResource) {
        return perResource * planet.resources() + INFLUENCE_OPPORTUNITY * planet.influence();
    }
}
