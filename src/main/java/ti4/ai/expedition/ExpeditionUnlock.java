package ti4.ai.expedition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.ai.scoring.Wallet;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class ExpeditionUnlock {

    public static final int TRADE_GOODS_FROM_TRADE = 3;
    public static final int PLANETS_READIED = 2;
    static final double FIRST_CLAIM_PER_CONTENDER = 0.25;
    static final double FIRST_CLAIM_CAP = 0.75;
    static final double LOW_SECRET_COST = 1.0;
    private static final int PAIR_CANDIDATES = 6;

    public record Readying(List<String> planets, double gain) {}

    public static double worth(Game game, Player seat) {
        return worth(game, seat, Holdings.of(game, seat));
    }

    public static double worth(Game game, Player seat, Holdings holdings) {
        return ExpeditionOutlook.best(game, seat, holdings)
                .map(ExpeditionOutlook.Choice::net)
                .orElse(0.0);
    }

    public static double gain(Game game, Player seat, Holdings after) {
        return Math.max(0, worth(game, seat, after) - worth(game, seat));
    }

    public static double afterTradeGoods(Game game, Player seat, int tradeGoodsGained) {
        if (!ExpeditionOutlook.isOpen(game, seat) || tradeGoodsGained <= 0) return 0;
        Holdings holdings = Holdings.of(game, seat);
        Wallet richer = holdings.wallet().withTradeGoods(seat.getTg() + tradeGoodsGained);
        return gain(game, seat, holdings.withWallet(richer));
    }

    public static double afterDrawing(Game game, Player seat, int cardsDrawn) {
        if (!ExpeditionOutlook.isOpen(game, seat) || cardsDrawn <= 0) return 0;
        return gain(game, seat, Holdings.of(game, seat).withActionCards(seat.getAcCount() + cardsDrawn));
    }

    public static double gainFromReadying(Game game, Player seat, Collection<String> planets) {
        if (planets.isEmpty() || !ExpeditionOutlook.isOpen(game, seat)) return 0;
        Holdings holdings = Holdings.of(game, seat);
        return gain(game, seat, holdings.withWallet(holdings.wallet().withPlanets(game, planets)));
    }

    public static Optional<Readying> afterReadying(Game game, Player seat, Collection<String> exhausted) {
        if (!ExpeditionOutlook.isOpen(game, seat)) return Optional.empty();
        Holdings holdings = Holdings.of(game, seat);
        double base = worth(game, seat, holdings);
        return pairs(game, exhausted).stream()
                .map(planets -> new Readying(
                        planets,
                        worth(game, seat, holdings.withWallet(holdings.wallet().withPlanets(game, planets))) - base))
                .filter(readying -> readying.gain() > 0)
                .max(Comparator.comparingDouble(Readying::gain)
                        .thenComparingInt(readying -> totalSize(game, readying.planets())));
    }

    public static double refundOfFirstClaim(Game game, Player seat) {
        Optional<ExpeditionOutlook.Choice> choice = ExpeditionOutlook.best(game, seat);
        if (choice.isEmpty()) return 0;
        int planets = choice.get().payment().planetCount();
        if (planets == 0) return 0;
        return choice.get().cost() * Math.min(1.0, (double) PLANETS_READIED / planets);
    }

    public static double claimingFirst(Game game, Player seat) {
        List<ExpeditionOutlook.Choice> options = ExpeditionOutlook.options(game, seat, Holdings.of(game, seat)).stream()
                .filter(choice -> choice.net() > 0)
                .toList();
        if (options.isEmpty()) return 0;
        ExpeditionOutlook.Choice best = options.getFirst();
        if (best.kind() == ExpeditionKind.SECRET && best.cost() > LOW_SECRET_COST) return 0;
        double lostChance = Math.min(FIRST_CLAIM_CAP, FIRST_CLAIM_PER_CONTENDER * contenders(game, seat, best.kind()));
        double fallback = options.size() > 1 ? options.get(1).net() : 0;
        return lostChance * (best.net() - fallback);
    }

    public static List<String> plannedPlanets(Game game, Player seat) {
        return ExpeditionOutlook.best(game, seat).stream()
                .flatMap(choice -> java.util.stream.Stream.concat(
                        choice.payment().forResources().stream(), choice.payment().forInfluence().stream()))
                .distinct()
                .toList();
    }

    private static long contenders(Game game, Player seat, ExpeditionKind kind) {
        return game.getRealPlayers().stream()
                .filter(rival -> rival != seat)
                .filter(BreakthroughValue::isLocked)
                .filter(rival -> RaceUrgency.couldClaim(game, rival, kind))
                .count();
    }

    private static List<List<String>> pairs(Game game, Collection<String> exhausted) {
        List<String> ranked = exhausted.stream()
                .distinct()
                .sorted(Comparator.comparingInt((String planet) -> -size(game, planet))
                        .thenComparing(Comparator.naturalOrder()))
                .limit(PAIR_CANDIDATES)
                .toList();
        if (ranked.size() <= 1) return ranked.stream().map(List::of).toList();
        List<List<String>> pairs = new ArrayList<>();
        for (int first = 0; first < ranked.size(); first++) {
            for (int second = first + 1; second < ranked.size(); second++) {
                pairs.add(List.of(ranked.get(first), ranked.get(second)));
            }
        }
        return pairs;
    }

    private static int totalSize(Game game, List<String> planets) {
        return planets.stream().mapToInt(planet -> size(game, planet)).sum();
    }

    private static int size(Game game, String planet) {
        return BoardView.planetResources(game, planet) + BoardView.planetInfluence(game, planet);
    }
}
