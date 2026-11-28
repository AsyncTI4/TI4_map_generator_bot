package ti4.ai.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;

/**
 * What the seats holding Produce en Masse did about it: one line per holder at each status phase, the docks every seat
 * built over the game, and (over the whole batch) the most production in one system each holder reached. Also what
 * every seat produced: one SEATPROD line per seat per game with the cost and units of its builds in each round, and
 * per-faction averages per round over the batch.
 *
 * <p>A build is read from the seat's produced-units map (unit_tile_holder keys). The bot clears that map when it
 * offers the next production's buttons (or on Reset Build), and the harness presses one button per tick, so the map
 * last seen before it shrinks or empties is the finished build. Its cost is the bot's own unit cost
 * (Helper.calculateCostOfProducedUnits), the amount owed, not how it was paid. A build reset and produced again is
 * counted twice; the AI rarely resets.
 */
final class ProduceTracker {

    private static final String PRODUCE_EN_MASSE = "pem";
    private static final int SCORED = 99;
    private static final Map<String, Integer> BATCH_BEST = new HashMap<>();
    static final ConcurrentLinkedQueue<SeatBuilds> BATCH = new ConcurrentLinkedQueue<>();

    // One finished build: the round it was made in, its cost and the units by type (bot alias: gf, ff, dd, ...).
    record Build(String faction, int round, int cost, Map<String, Integer> units) {}

    // Everything one seat built in one game that lasted rounds rounds.
    record SeatBuilds(String faction, int rounds, List<Build> builds) {}

    private record InProgress(int round, int cost, Map<String, Integer> produced) {}

    private final Map<String, InProgress> producing = new HashMap<>();
    private final List<Build> builds = new ArrayList<>();

    private final Map<String, Integer> docksOnBoard = new HashMap<>();
    private final Map<String, Integer> docksBuilt = new LinkedHashMap<>();
    private final Map<String, Integer> bestProduction = new HashMap<>();

    void observe(Game game) {
        for (Player seat : game.getRealPlayers()) {
            int docks = docks(game, seat);
            Integer before = docksOnBoard.put(seat.getFaction(), docks);
            if (before != null && docks > before) docksBuilt.merge(seat.getFaction(), docks - before, Integer::sum);
            observeBuild(game, seat);
        }
    }

    private void observeBuild(Game game, Player seat) {
        String faction = seat.getFaction();
        Map<String, Integer> now = new TreeMap<>();
        seat.getCurrentProducedUnits().forEach((key, count) -> {
            if (count != null && count > 0) now.put(key, count);
        });
        InProgress previous = producing.get(faction);
        if (previous != null && previous.produced().equals(now)) return;
        if (previous != null && !grew(previous.produced(), now)) {
            builds.add(new Build(faction, previous.round(), previous.cost(), byUnitType(previous.produced())));
            producing.remove(faction);
        }
        if (now.isEmpty()) return;
        producing.put(faction, new InProgress(game.getRound(), costOf(game, seat), now));
    }

    // The log must never break a game: an odd produced-units key the cost helper cannot read counts as free.
    private static int costOf(Game game, Player seat) {
        try {
            return Helper.calculateCostOfProducedUnits(seat, game, true);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    List<Build> builds() {
        return List.copyOf(builds);
    }

    // True when now is the same build with more placed: every unit already placed is still there.
    static boolean grew(Map<String, Integer> before, Map<String, Integer> now) {
        if (now.isEmpty()) return false;
        for (Map.Entry<String, Integer> entry : before.entrySet()) {
            if (now.getOrDefault(entry.getKey(), 0) < entry.getValue()) return false;
        }
        return true;
    }

    static Map<String, Integer> byUnitType(Map<String, Integer> produced) {
        Map<String, Integer> units = new TreeMap<>();
        produced.forEach((key, count) -> units.merge(key.split("_")[0], count, Integer::sum));
        return units;
    }

    // One SEATPROD line per seat: each round it built in, with the cost and units, then the game total.
    static List<String> seatLines(String game, List<SeatBuilds> seats) {
        List<String> lines = new ArrayList<>();
        for (SeatBuilds seat : seats) {
            Map<Integer, int[]> costByRound = new TreeMap<>();
            Map<Integer, Map<String, Integer>> unitsByRound = new TreeMap<>();
            for (Build build : seat.builds()) {
                costByRound.computeIfAbsent(build.round(), r -> new int[1])[0] += build.cost();
                Map<String, Integer> units = unitsByRound.computeIfAbsent(build.round(), r -> new TreeMap<>());
                build.units().forEach((unit, count) -> units.merge(unit, count, Integer::sum));
            }
            List<String> rounds = new ArrayList<>();
            costByRound.forEach(
                    (round, cost) -> rounds.add("r" + round + " cost " + cost[0] + " " + unitsByRound.get(round)));
            int total = seat.builds().stream().mapToInt(Build::cost).sum();
            lines.add("SEATPROD " + game + " " + seat.faction() + ": "
                    + (rounds.isEmpty() ? "nothing built" : String.join("; ", rounds)) + "; total cost " + total
                    + " in " + seat.builds().size() + " builds over " + seat.rounds() + " rounds");
        }
        return lines;
    }

    // Per faction over the batch: average production cost and units of each type per seat per round played.
    static List<String> batchLines(List<SeatBuilds> seats) {
        Map<String, int[]> roundsAndCost = new TreeMap<>();
        Map<String, Map<String, Integer>> units = new TreeMap<>();
        for (SeatBuilds seat : seats) {
            int[] totals = roundsAndCost.computeIfAbsent(seat.faction(), f -> new int[3]);
            totals[0] += seat.rounds();
            totals[2]++;
            Map<String, Integer> factionUnits = units.computeIfAbsent(seat.faction(), f -> new TreeMap<>());
            for (Build build : seat.builds()) {
                totals[1] += build.cost();
                build.units().forEach((unit, count) -> factionUnits.merge(unit, count, Integer::sum));
            }
        }
        List<String> lines = new ArrayList<>();
        lines.add("Production per seat per round by faction (cost owed, units by type):");
        roundsAndCost.forEach((faction, totals) -> {
            double rounds = Math.max(1, totals[0]);
            List<String> perUnit = new ArrayList<>();
            units.get(faction)
                    .forEach((unit, count) ->
                            perUnit.add(unit + " " + String.format(java.util.Locale.ROOT, "%.1f", count / rounds)));
            lines.add(String.format(
                    java.util.Locale.ROOT,
                    "  %s: cost %.1f, units %s (%d seats, %d seat rounds)",
                    faction,
                    totals[1] / rounds,
                    perUnit,
                    totals[2],
                    totals[0]));
        });
        return lines;
    }

    void record(Game game) {
        for (Player seat : game.getRealPlayers()) {
            boolean scored = seat.getSecretsScored().containsKey(PRODUCE_EN_MASSE);
            if (!scored && !seat.getSecretsUnscored().containsKey(PRODUCE_EN_MASSE)) continue;
            Tile best = null;
            int production = 0;
            for (Tile tile : game.getTileMap().values()) {
                int here = Helper.getProductionValue(seat, game, tile, false);
                if (here > production) {
                    production = here;
                    best = tile;
                }
            }
            bestProduction.merge(seat.getFaction(), scored ? SCORED : production, Math::max);
            System.out.printf(
                    "Produce holder %s %s r%d: best system %s production %d, docks %d, Space Dock II %s%s%n",
                    game.getName(),
                    seat.getFaction(),
                    game.getRound(),
                    best == null ? "none" : best.getPosition(),
                    production,
                    docks(game, seat),
                    seat.hasTech("sd2"),
                    scored ? ", scored" : "");
        }
    }

    void finish(Game game) {
        System.out.println("Docks built " + game.getName() + ": " + docksBuilt);
        producing.forEach((faction, build) ->
                builds.add(new Build(faction, build.round(), build.cost(), byUnitType(build.produced()))));
        producing.clear();
        List<SeatBuilds> seats = new ArrayList<>();
        for (Player seat : game.getRealPlayers()) {
            String faction = seat.getFaction();
            seats.add(new SeatBuilds(
                    faction,
                    game.getRound(),
                    builds.stream()
                            .filter(build -> build.faction().equals(faction))
                            .toList()));
        }
        seatLines(game.getName(), seats).forEach(System.out::println);
        BATCH.addAll(seats);
        synchronized (BATCH_BEST) {
            bestProduction.forEach(
                    (faction, best) -> BATCH_BEST.merge(game.getName() + "|" + faction, best, Math::max));
        }
    }

    static void printBatch() {
        Map<Integer, Integer> counts = new TreeMap<>();
        synchronized (BATCH_BEST) {
            BATCH_BEST.values().forEach(best -> counts.merge(best, 1, Integer::sum));
            BATCH_BEST.clear();
        }
        System.out.println("Best production in one system per Produce en Masse holder (99 is scored): " + counts);
        List<SeatBuilds> seats = new ArrayList<>(BATCH);
        BATCH.clear();
        batchLines(seats).forEach(System.out::println);
    }

    private static int docks(Game game, Player seat) {
        int docks = 0;
        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder holder : tile.getUnitHolders().values()) {
                docks += ti4.ai.eval.BoardView.count(holder, seat, UnitType.Spacedock);
            }
        }
        return docks;
    }
}
