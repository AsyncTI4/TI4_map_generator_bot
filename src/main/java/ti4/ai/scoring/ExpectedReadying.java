package ti4.ai.scoring;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.brain.StrategyCard;
import ti4.ai.xxcha.XxekirGromPlan;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class ExpectedReadying {

    public static final int DIPLOMACY_READIES = 2;
    public static final int XXCHA_AGENT_READIES = 1;
    static final String XXCHA_AGENT = "xxchaagent";

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
        return (expectsDiplomacy(game, seat) ? DIPLOMACY_READIES : 0) + leaderReadies(game, seat);
    }

    public static int ownReadies(Game game, Player seat) {
        return ownDiplomacyReadies(game, seat) + leaderReadies(game, seat);
    }

    public static int plannedReadies(Game game, Player seat) {
        int diplomacy = AiExperiments.enabled(seat, Experiment.DIPLOMACY_READIED_PLANNING)
                ? ownDiplomacyReadies(game, seat)
                : 0;
        return diplomacy + leaderReadies(game, seat);
    }

    public static int spareReadies(Game game, Player seat) {
        return spare(seat, ownReadies(game, seat));
    }

    public static int plannedSpareReadies(Game game, Player seat) {
        return spare(seat, plannedReadies(game, seat));
    }

    private static int spare(Player seat, int readies) {
        return Math.max(0, readies - seat.getExhaustedPlanets().size());
    }

    public static Wallet afterPaying(Game game, Player seat, Wallet wallet, Wallet.Payment payment) {
        return afterPaying(wallet, payment, spareReadies(game, seat));
    }

    public static Wallet plannedAfterPaying(Game game, Player seat, Wallet wallet, Wallet.Payment payment) {
        return afterPaying(wallet, payment, plannedSpareReadies(game, seat));
    }

    private static Wallet afterPaying(Wallet wallet, Wallet.Payment payment, int spare) {
        Wallet left = wallet.without(payment);
        List<Wallet.PlanetValue> readied = readiedAgain(wallet, payment, spare);
        if (readied.isEmpty()) return left;
        return left.withPlanetValues(
                Stream.concat(left.planets().stream(), readied.stream()).toList());
    }

    public static List<Wallet.PlanetValue> plannedReadiedAgain(
            Game game, Player seat, Wallet wallet, Wallet.Payment payment) {
        return readiedAgain(wallet, payment, plannedSpareReadies(game, seat));
    }

    private static List<Wallet.PlanetValue> readiedAgain(Wallet wallet, Wallet.Payment payment, int spare) {
        if (spare <= 0) return List.of();
        return wallet.planets().stream()
                .filter(planet -> payment.forInfluence().contains(planet.name())
                        || payment.forResources().contains(planet.name()))
                .sorted(LARGEST_FIRST)
                .limit(spare)
                .toList();
    }

    private static int leaderReadies(Game game, Player seat) {
        if (!AiExperiments.enabled(seat, Experiment.XXCHA_LEADER_READIES)) return 0;
        if (seat.isPassed()) return 0;
        int agent = seat.hasUnexhaustedLeader(XXCHA_AGENT) ? XXCHA_AGENT_READIES : 0;
        return agent + XxekirGromPlan.readiesThisRound(game, seat);
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

    private static int ownDiplomacyReadies(Game game, Player seat) {
        return holdsUnplayedDiplomacy(game, seat) ? DIPLOMACY_READIES : 0;
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
