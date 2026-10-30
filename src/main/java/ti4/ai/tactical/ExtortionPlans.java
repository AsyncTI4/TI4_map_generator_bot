package ti4.ai.tactical;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.scoring.WinRace;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;

@UtilityClass
public class ExtortionPlans {

    private static final String SPARED_KEY = "aiExtortionSpared";
    private static final String COMMITTED_KEY = "aiExtortionCommitted";
    private static final String PAYERS_KEY = "aiExtortionPayers";
    static final double PAID_PAYOFF = 0.5;
    static final double PAID_FEAR = 0.5;
    private static final double COULD_WIN = 1.0;
    private static final String ROUND = "@";
    private static final String ENTRY = "+";
    private static final String VALUE = "~";

    public static void spare(Game game, Player seat, String position) {
        write(game, seat, SPARED_KEY, position, 0);
    }

    public static void commit(Game game, Player seat, String position, double credibility) {
        write(game, seat, COMMITTED_KEY, position, credibility);
    }

    public static boolean spared(Game game, Player seat, String position) {
        return read(game, seat, SPARED_KEY).containsKey(position);
    }

    public static double credibility(Game game, Player seat, String position) {
        return read(game, seat, COMMITTED_KEY).getOrDefault(position, 0.0);
    }

    public static void paidBy(Game game, Player seat, Player payer) {
        write(game, seat, PAYERS_KEY, payer.getFaction(), WinRace.canWinThisRound(game, seat, payer) ? COULD_WIN : 0);
    }

    public static boolean dealOff(Game game, Player seat, Player payer) {
        Double couldWin = read(game, seat, PAYERS_KEY).get(payer.getFaction());
        return couldWin != null && couldWin < COULD_WIN && WinRace.canWinThisRound(game, seat, payer);
    }

    static boolean honoured(Game game, Player seat, TacticalPlan plan) {
        if (!spared(game, seat, plan.target())) return false;
        Tile tile = game.getTileByPosition(plan.target());
        if (tile == null) return false;
        List<Player> opponents = TacticalPlanner.opponentsAt(game, seat, tile);
        return opponents.stream().anyMatch(opponent -> paid(game, seat, opponent))
                && opponents.stream().noneMatch(opponent -> dealOff(game, seat, opponent));
    }

    public static boolean paid(Game game, Player seat, Player payer) {
        return read(game, seat, PAYERS_KEY).containsKey(payer.getFaction());
    }

    public static double fearOf(Game game, Player enemy, Player seat, Tile tile) {
        if (!paid(game, enemy, seat) || dealOff(game, enemy, seat)) return 1.0;
        return spared(game, enemy, tile.getPosition()) ? 0.0 : PAID_FEAR;
    }

    static TacticalPlan adjust(Game game, Player seat, TacticalPlan plan) {
        double credibility = credibility(game, seat, plan.target());
        return credibility > 0 ? plan.withScore(plan.score() + credibility) : plan;
    }

    static List<TacticalPlan> honourPayments(Game game, Player seat, List<TacticalPlan> plans) {
        if (read(game, seat, PAYERS_KEY).isEmpty()) return plans;
        Map<TacticalPlan, Player> payers = new HashMap<>();
        for (TacticalPlan plan : plans) {
            payerAt(game, seat, plan)
                    .filter(payer -> plan.score() > 0)
                    .filter(payer -> !dealOff(game, seat, payer))
                    .ifPresent(payer -> payers.put(plan, payer));
        }
        if (payers.isEmpty()) return plans;
        boolean scoresElsewhere = plans.stream()
                .filter(plan -> payerAt(game, seat, plan).isEmpty())
                .anyMatch(plan -> TacticalPlanner.scores(game, seat, plan));
        return plans.stream()
                .map(plan -> payers.containsKey(plan) && (scoresElsewhere || !TacticalPlanner.scores(game, seat, plan))
                        ? plan.withScore(plan.score() * PAID_PAYOFF)
                        : plan)
                .toList();
    }

    private static Optional<Player> payerAt(Game game, Player seat, TacticalPlan plan) {
        Tile tile = game.getTileByPosition(plan.target());
        if (tile == null) return Optional.empty();
        return TacticalPlanner.opponentsAt(game, seat, tile).stream()
                .filter(opponent -> paid(game, seat, opponent))
                .findFirst();
    }

    private static void write(Game game, Player seat, String key, String position, double value) {
        Map<String, Double> entries = new HashMap<>(read(game, seat, key));
        entries.put(position, value);
        String body = String.join(
                ENTRY,
                entries.entrySet().stream()
                        .map(entry -> entry.getKey() + VALUE + entry.getValue())
                        .sorted()
                        .toList());
        seat.setStoredValue(key, game.getRound() + ROUND + body);
    }

    private static Map<String, Double> read(Game game, Player seat, String key) {
        String stored = StringUtils.defaultString(seat.getStoredValue(key));
        String round = StringUtils.substringBefore(stored, ROUND);
        if (!String.valueOf(game.getRound()).equals(round)) return Map.of();
        Map<String, Double> entries = new HashMap<>();
        Arrays.stream(StringUtils.substringAfter(stored, ROUND).split(Pattern.quote(ENTRY)))
                .filter(entry -> entry.contains(VALUE))
                .forEach(entry -> parse(StringUtils.substringAfter(entry, VALUE))
                        .ifPresent(value -> entries.put(StringUtils.substringBefore(entry, VALUE), value)));
        return entries;
    }

    private static Optional<Double> parse(String value) {
        try {
            return Optional.of(Double.parseDouble(value));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
