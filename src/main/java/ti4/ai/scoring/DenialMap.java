package ti4.ai.scoring;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.brain.DecisionMemo;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.DecidingSeat;
import ti4.ai.scoring.WinOdds.Loss;
import ti4.ai.trace.Thinking;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;

public final class DenialMap {

    public static final double MATERIAL_CHANCE = ObjectiveValue.VICTORY_POINT_VALUE / ScoringStakes.WIN_VALUE;

    public record Removal(Loss loss, double value) {}

    private static final DenialMap EMPTY = new DenialMap(null, null, OptionalInt.empty(), Map.of(), Map.of());

    private final Game game;
    private final Player seat;
    private final OptionalInt mine;
    private final Map<Player, Double> chances;
    private final Map<Player, Double> stakes;
    private final Map<Player, Map<Loss, Double>> values = new HashMap<>();
    private final Map<Player, List<Removal>> removals = new HashMap<>();

    private DenialMap(
            Game game, Player seat, OptionalInt mine, Map<Player, Double> chances, Map<Player, Double> stakes) {
        this.game = game;
        this.seat = seat;
        this.mine = mine;
        this.chances = chances;
        this.stakes = stakes;
    }

    public static DenialMap of(Game game, Player seat) {
        if (game.getVp() <= 0 || !AiExperiments.enabled(seat, Experiment.WIN_BREAK_DENIAL)) return EMPTY;
        return DecisionMemo.remember(
                () -> build(game, seat), "denial.map", seat, DecidingSeat.knowsHandOf(seat), game.getRound());
    }

    static DenialMap build(Game game, Player seat) {
        try (Thinking.Span timing = Thinking.measure("denial.build")) {
            OptionalInt mine = WinRace.seatFinish(game, seat);
            Map<Player, Double> first = new LinkedHashMap<>();
            Map<Player, Double> chances = new LinkedHashMap<>();
            for (Player rival : game.getRealPlayers()) {
                if (rival == seat || rival.isEliminated() || rival.isNeutral()) continue;
                double before = WinOdds.finishesBefore(game, seat, rival, mine);
                first.put(rival, before);
                if (WinOdds.reachesGoal(game, seat, rival) >= MATERIAL_CHANCE && before > 0) chances.put(rival, before);
            }
            Map<Player, Double> stakes = new LinkedHashMap<>();
            for (Player rival : chances.keySet())
                stakes.put(rival, ScoringStakes.WIN_VALUE * nobodyElseFirst(first, rival));
            return new DenialMap(game, seat, mine, chances, stakes);
        }
    }

    private static double nobodyElseFirst(Map<Player, Double> first, Player rival) {
        double chance = 1;
        for (Map.Entry<Player, Double> other : first.entrySet()) {
            if (other.getKey() != rival) chance *= 1 - other.getValue();
        }
        return chance;
    }

    public boolean isEmpty() {
        return stakes.isEmpty();
    }

    public boolean covers(Player rival) {
        return stakes.containsKey(rival);
    }

    public Set<Player> contenders() {
        return stakes.keySet();
    }

    public double planet(Player rival, String planet) {
        return value(rival, Loss.planet(planet));
    }

    public double ships(Player rival, String position) {
        return value(rival, Loss.ships(position));
    }

    public double locked(Player rival, String position) {
        return value(rival, Loss.locked(position));
    }

    public double value(Player rival, Loss loss) {
        Double stake = stakes.get(rival);
        if (stake == null || loss.isNone()) return 0;
        Map<Loss, Double> known = values.computeIfAbsent(rival, ignored -> new HashMap<>());
        Double cached = known.get(loss);
        if (cached != null) return cached;
        double after = WinOdds.finishesBeforeWithout(game, seat, rival, mine, loss);
        double value = Math.max(0, chances.get(rival) - after) * stake;
        known.put(loss, value);
        return value;
    }

    public List<Removal> removals(Player rival) {
        if (!covers(rival)) return List.of();
        return removals.computeIfAbsent(rival, this::rank);
    }

    private List<Removal> rank(Player rival) {
        List<Removal> ranked = new ArrayList<>();
        for (String planet : rival.getPlanets()) {
            Loss loss = Loss.planet(planet);
            ranked.add(new Removal(loss, value(rival, loss)));
        }
        for (Tile tile : game.getTileMap().values()) {
            if (!holdsShips(rival, tile)) continue;
            Loss loss = Loss.ships(tile.getPosition());
            ranked.add(new Removal(loss, value(rival, loss)));
        }
        ranked.sort(Comparator.comparingDouble(Removal::value).reversed());
        return List.copyOf(ranked);
    }

    private static boolean holdsShips(Player rival, Tile tile) {
        return BoardView.ships(BoardView.space(tile), rival).keySet().stream()
                .anyMatch(type -> type != UnitType.Fighter);
    }
}
