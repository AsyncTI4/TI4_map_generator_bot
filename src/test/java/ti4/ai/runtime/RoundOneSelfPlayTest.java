package ti4.ai.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import ti4.ai.AiSettings;
import ti4.ai.brain.StrategyCard;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.Territory;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.testUtils.BaseTi4Test;

// Plays only round 1 and measures how well each seat opened: how much of its own area it took (its lane and slice in
// full, border systems at half weight since a neighbour contests them), the points it scored in round 1, and which
// strategy cards it picked. Run with -Dai.selfplay.roundone=true, -Dai.selfplay.games=N, -Dai.selfplay.threads=T.
class RoundOneSelfPlayTest extends BaseTi4Test {

    private static final List<String> FACTIONS = AiSettings.DEFAULT_FACTIONS;
    private static final long VIRTUAL_DAY = 24 * 60 * 60 * 1000L;
    private static final int ROUNDS = Integer.getInteger("ai.selfplay.rounds", 1);

    record SeatResult(
            String faction,
            double ownAreaAvailable,
            double ownAreaTaken,
            int planetsAtStart,
            int planetsAtEnd,
            int points,
            List<String> cards,
            List<String> scored,
            String endOfActions) {}

    record GameResult(
            SelfPlayGame.Ending ending,
            List<SeatResult> seats,
            List<String> objectives,
            Map<Integer, double[]> byRound,
            List<ScoringDiagnostic.SeatRound> scoring) {}

    @Test
    @EnabledIfSystemProperty(named = "ai.selfplay.roundone", matches = "true")
    void playRoundOne() throws Exception {
        int games = Integer.getInteger("ai.selfplay.games", 6);
        int threads = Math.clamp(Integer.getInteger("ai.selfplay.threads", 6), 1, games);
        List<GameResult> results = playAll(games, threads);
        report(results);
        List<ScoringDiagnostic.SeatRound> scoring = new ArrayList<>();
        for (GameResult result : results) {
            scoring.addAll(result.scoring());
            if (Boolean.getBoolean("ai.selfplay.scorediag.lines"))
                result.scoring().forEach(seat -> System.out.println(ScoringDiagnostic.line(seat)));
        }
        ScoringDiagnostic.report(scoring);
        ScoringDiagnostic.reportCalibration();
    }

    private static void report(List<GameResult> results) {
        double available = 0;
        double taken = 0;
        int seats = 0;
        int points = 0;
        int planetsGained = 0;
        int zeroPointSeats = 0;
        int stalls = 0;
        Map<String, Integer> picks = new TreeMap<>();
        Map<String, double[]> byFaction = new TreeMap<>();
        for (GameResult result : results) {
            if (result.ending() != SelfPlayGame.Ending.STOP_CONDITION) stalls++;
            System.out.println("ROUND1 game " + result.ending() + " objectives " + result.objectives());
            for (SeatResult seat : result.seats()) {
                System.out.printf(
                        "ROUND1   %-8s own area %.1f/%.1f, planets %d -> %d, points %d, cards %s, scored %s | %s%n",
                        seat.faction(),
                        seat.ownAreaTaken(),
                        seat.ownAreaAvailable(),
                        seat.planetsAtStart(),
                        seat.planetsAtEnd(),
                        seat.points(),
                        seat.cards(),
                        seat.scored(),
                        seat.endOfActions());
                available += seat.ownAreaAvailable();
                taken += seat.ownAreaTaken();
                points += seat.points();
                planetsGained += seat.planetsAtEnd() - seat.planetsAtStart();
                seats++;
                if (seat.points() == 0) zeroPointSeats++;
                seat.cards().forEach(card -> picks.merge(card, 1, Integer::sum));
                double[] faction = byFaction.computeIfAbsent(seat.faction(), key -> new double[4]);
                faction[0] += seat.ownAreaTaken();
                faction[1] += seat.ownAreaAvailable();
                faction[2] += seat.points();
                faction[3]++;
            }
        }
        System.out.printf(
                "ROUND1 SUMMARY over %d games (%d did not reach round 2): own area taken %.1f%% (%.2f of %.2f per seat),"
                        + " planets gained %.2f per seat, points %.2f per seat, seats scoring nothing %.0f%%%n",
                results.size(),
                stalls,
                available == 0 ? 0 : 100 * taken / available,
                taken / Math.max(1, seats),
                available / Math.max(1, seats),
                (double) planetsGained / Math.max(1, seats),
                (double) points / Math.max(1, seats),
                100.0 * zeroPointSeats / Math.max(1, seats));
        System.out.println("ROUND1 PICKS " + picks);
        Map<Integer, double[]> rounds = new TreeMap<>();
        for (GameResult result : results) {
            result.byRound().forEach((round, values) -> {
                double[] sum = rounds.computeIfAbsent(round, key -> new double[3]);
                for (int i = 0; i < 3; i++) sum[i] += values[i];
            });
        }
        rounds.forEach((round, values) -> System.out.printf(
                "ROUND1 AFTER ROUND %d: own area taken %.2f per seat, points %.2f per seat%n",
                round, values[0] / Math.max(1, values[2]), values[1] / Math.max(1, values[2])));
        byFaction.forEach((faction, values) -> System.out.printf(
                "ROUND1 FACTION %-8s own area %.0f%%, points %.2f%n",
                faction, values[1] == 0 ? 0 : 100 * values[0] / values[1], values[2] / values[3]));
    }

    private static List<GameResult> playAll(int games, int threads) throws Exception {
        long clockStart = System.currentTimeMillis();
        AtomicInteger names = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(
                threads, task -> new Thread(task, "ai-round-one-" + names.incrementAndGet()));
        try {
            List<Future<GameResult>> futures = new ArrayList<>();
            for (int i = 0; i < games; i++) {
                int slot = i;
                futures.add(pool.submit(() -> playOne(clockStart + slot, slot)));
            }
            List<GameResult> results = new ArrayList<>();
            for (Future<GameResult> future : futures) results.add(future.get());
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private static GameResult playOne(long clockStart, int slot) throws Exception {
        try (SelfPlayEnvironment env = new SelfPlayEnvironment(clockStart)) {
            SelfPlayGame selfPlay = new SelfPlayGame(
                    env, "aiselfplay" + ThreadLocalRandom.current().nextInt(100_000, 1_000_000), slot);
            List<String> seating = new ArrayList<>(FACTIONS);
            java.util.Collections.rotate(seating, slot);
            Game game = selfPlay.setUp(seating);
            Map<String, Map<String, Double>> ownArea = new HashMap<>();
            Map<String, Integer> planetsAtStart = new HashMap<>();
            for (Player seat : game.getRealPlayers()) {
                ownArea.put(seat.getFaction(), ownAreaWeights(game, seat));
                planetsAtStart.put(seat.getFaction(), seat.getPlanets().size());
            }
            Map<String, List<String>> cards = new LinkedHashMap<>();
            Map<String, String> endOfActions = new HashMap<>();
            String[] lastActive = {""};
            int[] turn = {0};
            int[] technologyTurn = {0};
            int[] lastRound = {1};
            Map<Integer, double[]> byRound = new TreeMap<>();
            java.util.concurrent.atomic.AtomicReference<Game> live =
                    new java.util.concurrent.atomic.AtomicReference<>(game);
            String[] probeState = {"", "", ""};
            ScoringDiagnostic diagnostic = new ScoringDiagnostic();
            env.decisionListener = diagnostic::onDecision;
            diagnostic.ledger = () -> {
                synchronized (env.tradeLedger) {
                    return List.copyOf(env.tradeLedger);
                }
            };
            if (PROBE != null) {
                synchronized (env) {
                    env.watched.add("[probe] seating " + seating + ", speaker "
                            + game.getPlayer(game.getSpeakerUserID()).getFaction());
                }
            }
            SelfPlayGame.Outcome outcome = selfPlay.run(
                    current -> {
                        live.set(current);
                        diagnostic.observe(current);
                        if (PROBE != null) probe(env, current, probeState);
                        boolean acting = "action".equalsIgnoreCase(current.getPhaseOfGame());
                        if (acting && !current.getActivePlayerID().equals(lastActive[0])) {
                            lastActive[0] = current.getActivePlayerID();
                            turn[0]++;
                        }
                        if (technologyTurn[0] == 0
                                && current.getPlayedSCs().stream()
                                        .anyMatch(initiative ->
                                                StrategyCard.of(current, initiative) == StrategyCard.TECHNOLOGY)) {
                            technologyTurn[0] = turn[0];
                        }
                        if (!acting && !cards.isEmpty() && endOfActions.isEmpty()) {
                            for (Player seat : current.getRealPlayers())
                                endOfActions.put(seat.getFaction(), snapshot(current, seat));
                        }
                        if ("action".equalsIgnoreCase(current.getPhaseOfGame()) && cards.isEmpty()) {
                            for (Player seat : current.getRealPlayers()) {
                                cards.put(
                                        seat.getFaction(),
                                        seat.getSCs().stream()
                                                .map(initiative -> initiative + " "
                                                        + StrategyCard.of(current, initiative)
                                                                .name()
                                                                .toLowerCase())
                                                .toList());
                            }
                        }
                        if (current.getRound() > lastRound[0]) {
                            byRound.put(lastRound[0], roundTotals(current, ownArea));
                            lastRound[0] = current.getRound();
                        }
                        return current.getRound() > ROUNDS;
                    },
                    (1 + ROUNDS) * VIRTUAL_DAY,
                    40_000 * ROUNDS);
            if (Boolean.getBoolean("ai.selfplay.report") && slot == 0) System.out.println(outcome.report());
            if (PROBE != null) {
                StringBuilder dump = new StringBuilder();
                synchronized (env) {
                    for (String line : env.watched)
                        dump.append("PROBE g")
                                .append(slot)
                                .append(' ')
                                .append(line)
                                .append('\n');
                }
                var mainChannel = live.get().getMainGameChannel();
                if (mainChannel != null) {
                    for (var message :
                            env.discord.channel(mainChannel.getIdLong()).liveMessages()) {
                        if (!message.content().startsWith(ti4.ai.perception.AiPrompt.TABLE_TALK)) continue;
                        dump.append("PROBE g")
                                .append(slot)
                                .append(" [table talk] ")
                                .append(message.content())
                                .append('\n');
                    }
                }
                System.out.print(dump);
            }
            Game end = live.get();
            diagnostic.finish(end);
            List<SeatResult> seats = new ArrayList<>();
            for (Player seat : end.getRealPlayers()) {
                Map<String, Double> weights = ownArea.get(seat.getFaction());
                double takenWeight = weights.entrySet().stream()
                        .filter(entry -> seat.getPlanets().contains(entry.getKey()))
                        .mapToDouble(Map.Entry::getValue)
                        .sum();
                seats.add(new SeatResult(
                        seat.getFaction(),
                        weights.values().stream()
                                .mapToDouble(Double::doubleValue)
                                .sum(),
                        takenWeight,
                        planetsAtStart.get(seat.getFaction()),
                        seat.getPlanets().size(),
                        seat.getTotalVictoryPoints(),
                        cards.getOrDefault(seat.getFaction(), List.of()),
                        scored(end, seat),
                        endOfActions.getOrDefault(seat.getFaction(), "")));
            }
            List<String> objectives =
                    new ArrayList<>(live.get().getRevealedPublicObjectives().keySet());
            objectives.add("technology played on turn " + technologyTurn[0]);
            return new GameResult(outcome.ending(), seats, objectives, byRound, diagnostic.results);
        }
    }

    // -Dai.selfplay.probe=<faction> logs that seat's pick values and Gravity Drive outlook at each of its picks, and
    // its
    // board state whenever the active seat, played cards, follows, tokens or its fleet change. Combine with
    // -Dai.selfplay.watch=<regex> so the seat's decisions land in the same per-game log.
    private static final String PROBE = System.getProperty("ai.selfplay.probe");

    private static void probe(SelfPlayEnvironment env, Game game, String[] state) {
        Player seat = game.getRealPlayers().stream()
                .filter(player -> PROBE.equals(player.getFaction()))
                .findFirst()
                .orElse(null);
        if (seat == null || game.getRound() > ROUNDS) return;
        String phase = game.getPhaseOfGame();
        Player active = game.getActivePlayer();
        String activeFaction = active == null ? "-" : active.getFaction();
        if ("strategy".equalsIgnoreCase(phase) && active == seat) {
            String key = game.getRound() + "|" + seat.getSCs();
            if (!key.equals(state[0])) {
                state[0] = key;
                add(
                        env,
                        "[probe] PICK r" + game.getRound() + " " + ti4.ai.strategy.OpeningProbe.pickValues(game, seat));
                add(env, "[probe] PICK-GD " + ti4.ai.strategy.OpeningProbe.gravityDrive(game, seat));
                add(env, "[probe] GOALS " + ti4.ai.strategy.OpeningProbe.goals(game, seat));
            }
            return;
        }
        if (!"action".equalsIgnoreCase(phase)) return;
        List<String> played = new ArrayList<>();
        for (int initiative : game.getPlayedSCs()) {
            String holder = game.getRealPlayers().stream()
                    .filter(player -> player.getSCs().contains(initiative))
                    .map(Player::getFaction)
                    .findFirst()
                    .orElse("?");
            played.add(initiative + StrategyCard.of(game, initiative).name().substring(0, 4) + "@" + holder
                    + (seat.hasFollowedSC(initiative) ? "+followed" : ""));
        }
        List<String> fleet = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            var space = BoardView.space(tile);
            int carriers = BoardView.count(space, seat, ti4.helpers.Units.UnitType.Carrier);
            int ships = BoardView.nonFighterShips(space, seat);
            if (ships == 0) continue;
            fleet.add(tile.getPosition() + ":cv" + carriers + "/ships" + ships + "/gf"
                    + BoardView.groundForces(space, seat) + (tile.hasPlayerCC(seat) ? "*" : ""));
        }
        String board = "r" + game.getRound() + " active " + activeFaction + " passed " + seat.isPassed() + " played "
                + played + " tech " + seat.getTechs() + " planets "
                + seat.getPlanets().size() + " tg " + seat.getTg()
                + " comm " + seat.getCommodities() + " tokens t" + seat.getTacticalCC() + " s" + seat.getStrategicCC()
                + " ac " + seat.getActionCards().size() + " fleet " + fleet;
        if (board.equals(state[1])) return;
        state[1] = board;
        add(env, "[probe] " + board);
        if (active == seat) {
            add(env, "[probe]   GD " + ti4.ai.strategy.OpeningProbe.gravityDrive(game, seat));
            add(env, "[probe]   HOME " + ti4.ai.strategy.OpeningProbe.homeState(game, seat));
        }
        String turnKey = activeFaction + "|" + seat.getInRoundTurnCount() + "|"
                + game.getPlayedSCs().size();
        if (active == seat && !turnKey.equals(state[2])) {
            state[2] = turnKey;
            add(env, "[probe]   RANKED " + ti4.ai.strategy.OpeningProbe.rankedPlans(game, seat, 5));
        }
    }

    private static void add(SelfPlayEnvironment env, String line) {
        synchronized (env) {
            env.watched.add(line);
        }
    }

    private static double[] roundTotals(Game game, Map<String, Map<String, Double>> ownArea) {
        double[] totals = new double[3];
        for (Player seat : game.getRealPlayers()) {
            Map<String, Double> weights = ownArea.get(seat.getFaction());
            if (weights == null) continue;
            totals[0] += weights.entrySet().stream()
                    .filter(entry -> seat.getPlanets().contains(entry.getKey()))
                    .mapToDouble(Map.Entry::getValue)
                    .sum();
            totals[1] += seat.getTotalVictoryPoints();
            totals[2]++;
        }
        return totals;
    }

    private static String snapshot(Game game, Player seat) {
        Tile home = seat.getHomeSystemTile();
        int carriersHome =
                home == null ? 0 : BoardView.count(BoardView.space(home), seat, ti4.helpers.Units.UnitType.Carrier);
        int carriers = game.getTileMap().values().stream()
                .mapToInt(tile -> BoardView.count(BoardView.space(tile), seat, ti4.helpers.Units.UnitType.Carrier))
                .sum();
        int groundHome = home == null
                ? 0
                : home.getUnitHolders().values().stream()
                        .mapToInt(holder -> BoardView.groundForces(holder, seat))
                        .sum();
        Map<String, Double> open = ownAreaWeights(game, seat);
        String reachable = "";
        if (home != null) {
            for (int move = 1; move <= 2; move++) {
                java.util.Set<String> inReach = ti4.ai.eval.MovementGraph.reach(game, seat, home.getPosition(), move)
                        .keySet();
                long systems = game.getTileMap().values().stream()
                        .filter(tile -> inReach.contains(tile.getPosition()))
                        .filter(tile -> tile.getPlanetUnitHolders().stream()
                                .anyMatch(planet -> open.containsKey(planet.getName())))
                        .count();
                reachable += " open systems within " + move + ": " + systems + ";";
            }
        }
        return reachable + " tokens t" + seat.getTacticalCC() + " s" + seat.getStrategicCC() + ", carriers home "
                + carriersHome
                + "/" + carriers + ", ground home " + groundHome + ", home token "
                + (home != null && home.hasPlayerCC(seat));
    }

    private static List<String> scored(Game game, Player seat) {
        List<String> scored = new ArrayList<>();
        game.getScoredPublicObjectives().forEach((objective, players) -> {
            if (players.contains(seat.getUserID())) scored.add(objective);
        });
        scored.addAll(seat.getSecretsScored().keySet());
        return scored;
    }

    static Map<String, Double> ownAreaWeights(Game game, Player seat) {
        Territory territory = Territory.of(game, seat);
        Map<String, Double> weights = new HashMap<>();
        for (Tile tile : game.getTileMap().values()) {
            double weight =
                    switch (territory.zone(tile)) {
                        case LANE, SLICE -> 1.0;
                        case BORDER -> 0.5;
                        default -> 0;
                    };
            if (weight == 0) continue;
            for (Planet planet : tile.getPlanetUnitHolders()) {
                if (BoardView.controller(game, planet.getName()) != null || BoardView.hasCustodians(planet)) continue;
                weights.put(planet.getName(), weight);
            }
        }
        return weights;
    }
}
