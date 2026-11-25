package ti4.ai.scoring;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.StrategyCard;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class ExpectedReadying {

    public static final int DIPLOMACY_READIES = 2;
    public static final int XXCHA_AGENT_READIES = 1;
    public static final int XXEKIR_GROM_READIES = 4;
    static final String XXCHA_AGENT = "xxchaagent";
    static final String XXEKIR_GROM = "xxchahero-te";

    private static final Comparator<Wallet.PlanetValue> LARGEST_FIRST = Comparator.comparingInt(
                    (Wallet.PlanetValue planet) -> -(planet.resources() + planet.influence()))
            .thenComparing(Wallet.PlanetValue::name);

    public static List<Wallet.PlanetValue> beforeStatus(Game game, Player seat) {
        int readies = readiesBeforeStatus(game, seat);
        if (readies <= 0) return List.of();
        List<String> exhausted = seat.getExhaustedPlanets().stream()
                .filter(seat.getPlanets()::contains)
                .toList();
        return new Wallet(List.of(), 0, 0, 0, 1, Pooling.of(seat))
                .withPlanets(game, exhausted).planets().stream()
                        .sorted(LARGEST_FIRST)
                        .limit(readies)
                        .toList();
    }

    public static int readiesBeforeStatus(Game game, Player seat) {
        return (expectsDiplomacy(game, seat) ? DIPLOMACY_READIES : 0) + leaderReadies(seat);
    }

    public static int ownReadies(Game game, Player seat) {
        return (holdsUnplayedDiplomacy(game, seat) ? DIPLOMACY_READIES : 0) + leaderReadies(seat);
    }

    public static int spareReadies(Game game, Player seat) {
        return Math.max(0, ownReadies(game, seat) - seat.getExhaustedPlanets().size());
    }

    public static Wallet afterPaying(Game game, Player seat, Wallet wallet, Wallet.Payment payment) {
        Wallet left = wallet.without(payment);
        List<Wallet.PlanetValue> readied = readiedAgain(game, seat, wallet, payment);
        if (readied.isEmpty()) return left;
        return left.withPlanetValues(
                Stream.concat(left.planets().stream(), readied.stream()).toList());
    }

    public static List<Wallet.PlanetValue> readiedAgain(Game game, Player seat, Wallet wallet, Wallet.Payment payment) {
        int spare = spareReadies(game, seat);
        if (spare <= 0) return List.of();
        return wallet.planets().stream()
                .filter(planet -> payment.forInfluence().contains(planet.name())
                        || payment.forResources().contains(planet.name()))
                .sorted(LARGEST_FIRST)
                .limit(spare)
                .toList();
    }

    private static int leaderReadies(Player seat) {
        if (seat.isPassed()) return 0;
        int agent = seat.hasUnexhaustedLeader(XXCHA_AGENT) ? XXCHA_AGENT_READIES : 0;
        int hero = seat.hasLeaderUnlocked(XXEKIR_GROM) ? XXEKIR_GROM_READIES : 0;
        return agent + hero;
    }

    private static boolean expectsDiplomacy(Game game, Player seat) {
        for (Player holder : game.getRealPlayers()) {
            for (int initiative : holder.getSCs()) {
                if (StrategyCard.of(game, initiative) != StrategyCard.DIPLOMACY) continue;
                if (game.getPlayedSCs().contains(initiative)) continue;
                if (holder == seat || seat.getStrategicCC() > 0) return true;
            }
        }
        return false;
    }

    private static boolean holdsUnplayedDiplomacy(Game game, Player seat) {
        return seat.getSCs().stream()
                .anyMatch(card -> StrategyCard.of(game, card) == StrategyCard.DIPLOMACY
                        && !game.getPlayedSCs().contains(card));
    }

    public static SpendCost netOf(SpendCost cost, List<Wallet.PlanetValue> readied, Pooling pooling) {
        int resources = cost.resources();
        int influence = cost.influence();
        List<Wallet.PlanetValue> largestFirst = readied.stream()
                .sorted(Comparator.comparingInt(
                        (Wallet.PlanetValue planet) -> -Math.max(planet.resources(), planet.influence())))
                .toList();
        for (Wallet.PlanetValue planet : largestFirst) {
            if (pooling.paysBothKinds()) {
                resources = Math.max(0, resources - planet.resources());
                influence = Math.max(0, influence - planet.influence());
                continue;
            }
            boolean forResources = resources > 0 && (influence == 0 || planet.resources() >= planet.influence());
            if (forResources) {
                resources = Math.max(0, resources - planet.resources());
            } else if (influence > 0) {
                influence = Math.max(0, influence - planet.influence());
            }
        }
        return new SpendCost(resources, influence, cost.tradeGoods(), cost.tokens());
    }
}
