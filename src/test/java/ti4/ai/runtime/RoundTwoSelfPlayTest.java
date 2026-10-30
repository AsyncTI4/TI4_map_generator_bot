package ti4.ai.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import ti4.ai.AiSettings;
import ti4.ai.brain.StrategyCard;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.model.Mapper;
import ti4.model.PublicObjectiveModel;
import ti4.service.game.StartPhaseService;
import ti4.service.player.RefreshCardsService;
import ti4.testUtils.BaseTi4Test;
import ti4.testUtils.discord.FakeMessage;

// Plays the first two rounds of a game and measures how well the seats score. Every agenda phase is skipped: when
// the game enters one, its prompts are deleted, cards are readied as the end of the agenda phase would, and the next
// strategy phase starts. The game stops once the round-two status phase is over. The headline number is the average
// victory points per seat after round two, with its standard error over games (seats in one game are correlated, so
// the error counts games, not seats).
//
// Run with -Dai.selfplay.roundtwo=true -Dai.selfplay.games=N -Dai.selfplay.threads=T. Add
// -Dai.selfplay.roundtwo.lines=true for one line per seat and -Dai.selfplay.scorediag.lines=true for the per-seat
// scoring diagnostic; both name the game, so the lines can be matched to traces. -Dai.selfplay.objectiveseed=N fixes
// the stage one objectives of each game slot, so two batches with the same seed can be compared game by game.
class RoundTwoSelfPlayTest extends BaseTi4Test {

    private static final List<String> FACTIONS = factions();
    private static final long VIRTUAL_DAY = 24 * 60 * 60 * 1000L;
    private static final int LAST_ROUND = 2;

    // -Dai.selfplay.roundtwo.factions=xxcha,hacan,... seats those factions instead of the built-out default factions.
    private static List<String> factions() {
        String chosen = System.getProperty("ai.selfplay.roundtwo.factions", "");
        if (chosen.isBlank()) return AiSettings.DEFAULT_FACTIONS;
        return List.of(chosen.split(","));
    }

    record SeatResult(
            String faction,
            int pointsAfterRoundOne,
            int points,
            int publicPoints,
            int secretPoints,
            int otherPoints,
            List<String> cardsByRound,
            List<String> scoredPublics,
            List<String> scoredSecrets,
            List<String> heldSecrets,
            double ownAreaTaken,
            double ownAreaAvailable,
            int planetsGained) {}

    record GameResult(
            SelfPlayGame.Ending ending,
            int agendasSkipped,
            List<String> revealedPublics,
            List<SeatResult> seats,
            List<ScoringDiagnostic.SeatRound> scoring,
            String name) {

        double meanPoints() {
            return seats.stream().mapToInt(SeatResult::points).average().orElse(0);
        }

        double meanPointsAfterRoundOne() {
            return seats.stream()
                    .mapToInt(SeatResult::pointsAfterRoundOne)
                    .average()
                    .orElse(0);
        }

        int leaderPoints() {
            return seats.stream().mapToInt(SeatResult::points).max().orElse(0);
        }

        int tablePoints() {
            return seats.stream().mapToInt(SeatResult::points).sum();
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "ai.selfplay.roundtwo", matches = "true")
    void playTwoRounds() throws Exception {
        int games = Integer.getInteger("ai.selfplay.games", 6);
        int threads = Math.clamp(Integer.getInteger("ai.selfplay.threads", 6), 1, games);
        List<GameResult> results = playAll(games, threads);
        report(results);
        System.out.printf(
                "ROUND2 SARDAKK LEADERS commander pulls %d, hero plays %d over %d games%n",
                COMMANDER_PULLS.get(), HERO_PLAYS.get(), results.size());
        List<ScoringDiagnostic.SeatRound> scoring = new ArrayList<>();
        for (GameResult result : results) {
            scoring.addAll(result.scoring());
            if (Boolean.getBoolean("ai.selfplay.scorediag.lines"))
                result.scoring()
                        .forEach(seat -> System.out.println(
                                ScoringDiagnostic.line(seat).replaceFirst(" g ", " " + result.name() + " ")));
        }
        ScoringDiagnostic.report(scoring);
    }

    private static final AtomicInteger COMMANDER_PULLS = new AtomicInteger();
    private static final AtomicInteger HERO_PLAYS = new AtomicInteger();

    // -Dai.selfplay.roundtwo.sardakkleaders=true unlocks Sardakk's commander and hero before round 1 (neither unlocks
    // naturally within two rounds), so the smoke run exercises their rules; the report counts how often each fired.
    private static void unlockSardakkLeaders(Game game) {
        for (Player seat : game.getRealPlayers()) {
            if (!"sardakk".equals(seat.getFaction())) continue;
            for (String leader : List.of("sardakkcommander", "sardakkhero")) {
                seat.getLeader(leader).ifPresent(found -> found.setLocked(false));
            }
        }
    }

    private static void countSardakkLeaders(ti4.ai.brain.AiDecision decision) {
        if (!(decision instanceof ti4.ai.brain.AiDecision.Press press)) return;
        if (press.reason().contains("G'hom Sek'kus")) COMMANDER_PULLS.incrementAndGet();
        if (press.reason().contains("Sh'val")) HERO_PLAYS.incrementAndGet();
    }

    private static List<GameResult> playAll(int games, int threads) throws Exception {
        long clockStart = System.currentTimeMillis();
        AtomicInteger names = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(
                threads, task -> new Thread(task, "ai-round-two-" + names.incrementAndGet()));
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
            String name = "aiselfplay" + ThreadLocalRandom.current().nextInt(100_000, 1_000_000);
            SelfPlayGame selfPlay = new SelfPlayGame(env, name, slot);
            List<String> seating = new ArrayList<>(FACTIONS);
            Collections.rotate(seating, slot);
            Game game = selfPlay.setUp(seating);
            if (Boolean.getBoolean("ai.selfplay.roundtwo.sardakkleaders")) unlockSardakkLeaders(game);
            if (Boolean.getBoolean("ai.selfplay.roundtwo.forceagenda"))
                selfPlay.force(game, RoundTwoSelfPlayTest::takeCustodians);
            Map<String, Map<String, Double>> ownArea = new HashMap<>();
            Map<String, Integer> planetsAtStart = new HashMap<>();
            for (Player seat : game.getRealPlayers()) {
                ownArea.put(seat.getFaction(), RoundOneSelfPlayTest.ownAreaWeights(game, seat));
                planetsAtStart.put(seat.getFaction(), seat.getPlanets().size());
            }
            Map<String, List<String>> cards = new HashMap<>();
            Map<String, Integer> pointsAfterRoundOne = new HashMap<>();
            int[] cardsRecordedForRound = {0};
            int[] agendasSkipped = {0};
            AtomicReference<Game> live = new AtomicReference<>(game);
            String[] probeState = {"", "", "", ""};
            ScoringDiagnostic diagnostic = new ScoringDiagnostic();
            env.decisionListener = (context, decision) -> {
                diagnostic.onDecision(context, decision);
                countSardakkLeaders(decision);
            };
            SelfPlayGame.Outcome outcome = selfPlay.run(
                    current -> {
                        live.set(current);
                        diagnostic.observe(current);
                        if (PICK_PROBE) pickProbe(name, current, probeState);
                        int round = current.getRound();
                        if (round > 1 && pointsAfterRoundOne.isEmpty()) {
                            for (Player seat : current.getRealPlayers())
                                pointsAfterRoundOne.put(seat.getFaction(), seat.getTotalVictoryPoints());
                        }
                        if (inActionPhase(current) && cardsRecordedForRound[0] < round) {
                            cardsRecordedForRound[0] = round;
                            recordCards(current, cards);
                        }
                        if (round > LAST_ROUND) return true;
                        if (!inAgendaPhase(current)) return false;
                        if (round >= LAST_ROUND) return true;
                        skipAgenda(selfPlay, env, current);
                        agendasSkipped[0]++;
                        return false;
                    },
                    (2 + LAST_ROUND) * VIRTUAL_DAY,
                    40_000 * LAST_ROUND);
            if (Boolean.getBoolean("ai.selfplay.report") && slot == 0) System.out.println(outcome.report());
            Game end = live.get();
            diagnostic.finish(end);
            List<SeatResult> seats = new ArrayList<>();
            for (Player seat : end.getRealPlayers()) {
                String faction = seat.getFaction();
                Map<String, Double> weights = ownArea.get(faction);
                List<String> publics = scoredPublics(end, seat);
                int publicPoints = publics.stream()
                        .mapToInt(RoundTwoSelfPlayTest::publicPoints)
                        .sum();
                int secretPoints = seat.getSecretsScored().size();
                int points = seat.getTotalVictoryPoints();
                seats.add(new SeatResult(
                        faction,
                        pointsAfterRoundOne.getOrDefault(faction, points),
                        points,
                        publicPoints,
                        secretPoints,
                        points - publicPoints - secretPoints,
                        cards.getOrDefault(faction, List.of()),
                        publics,
                        new ArrayList<>(seat.getSecretsScored().keySet()),
                        new ArrayList<>(seat.getSecretsUnscored().keySet()),
                        weights.entrySet().stream()
                                .filter(entry -> seat.getPlanets().contains(entry.getKey()))
                                .mapToDouble(Map.Entry::getValue)
                                .sum(),
                        weights.values().stream()
                                .mapToDouble(Double::doubleValue)
                                .sum(),
                        seat.getPlanets().size() - planetsAtStart.get(faction)));
            }
            return new GameResult(
                    outcome.ending(),
                    agendasSkipped[0],
                    new ArrayList<>(end.getRevealedPublicObjectives().keySet()),
                    seats,
                    diagnostic.results,
                    name);
        }
    }

    // -Dai.selfplay.roundtwo.picks=true prints every seat's pick values when it picks; each strategy card play with
    // the player's points, scored and scorable publics as it is played (before its primary resolves); the round the
    // custodians are taken and by whom; and, at the start of round two's action phase, each seat's custodians arrival,
    // whether it can land this round and its ready influence. All lines start with PROBE and the game name.
    private static final boolean PICK_PROBE = Boolean.getBoolean("ai.selfplay.roundtwo.picks");

    private static void pickProbe(String name, Game game, String[] state) {
        Player active = game.getActivePlayer();
        String phase = game.getPhaseOfGame();
        boolean custodiansOpen = ti4.ai.eval.CustodiansRace.open(game);
        if (state[2].isEmpty() && custodiansOpen) state[2] = "open";
        if ("open".equals(state[2]) && !custodiansOpen) {
            state[2] = "taken";
            Player mecatol = game.getRealPlayers().stream()
                    .filter(player -> player.controlsMecatol(true))
                    .findFirst()
                    .orElse(null);
            System.out.println("PROBE CUSTODIANS " + name + " r" + game.getRound() + " " + phase + " by "
                    + (mecatol == null ? "-" : mecatol.getFaction()));
        }
        if ("strategy".equalsIgnoreCase(phase) && active != null) {
            String key = game.getRound() + "|" + active.getFaction() + "|" + active.getSCs();
            if (key.equals(state[0])) return;
            state[0] = key;
            System.out.println("PROBE PICK " + name + " r" + game.getRound() + " " + active.getFaction() + " vp "
                    + active.getTotalVictoryPoints() + " scorable "
                    + ti4.ai.scoring.ObjectivePolicy.scorablePublics(game, active) + " mecatol "
                    + active.controlsMecatol(true) + " | " + ti4.ai.strategy.OpeningProbe.pickValues(game, active));
            return;
        }
        if (!inActionPhase(game)) return;
        if (game.getRound() == LAST_ROUND && !"r2".equals(state[3]) && custodiansOpen) {
            state[3] = "r2";
            var race = ti4.ai.eval.CustodiansRace.of(game, game.getRealPlayers().getFirst());
            StringBuilder outlook = new StringBuilder();
            for (Player seat : game.getRealPlayers()) {
                var wallet = ti4.ai.scoring.Wallet.of(game, seat);
                outlook.append(String.format(
                        " %s[arr %s land %b inf %d tg %d t%d]",
                        seat.getFaction(),
                        race.arrival(seat).isPresent() ? race.arrival(seat).getAsInt() : "-",
                        race.canLandThisRound(seat),
                        wallet.influence(),
                        seat.getTg(),
                        seat.getTacticalCC()));
            }
            System.out.println("PROBE CUSTODIANS-R2 " + name + outlook);
        }
        String played = game.getRound() + "|" + game.getPlayedSCs();
        if (played.equals(state[1])) return;
        state[1] = played;
        for (int initiative : game.getPlayedSCs()) {
            Player holder = game.getRealPlayers().stream()
                    .filter(player -> player.getSCs().contains(initiative))
                    .findFirst()
                    .orElse(null);
            if (holder == null) continue;
            String card = StrategyCard.of(game, initiative).name().toLowerCase(Locale.ROOT);
            String line = "PROBE PLAY " + name + " r" + game.getRound() + " " + holder.getFaction() + " " + card;
            if (!playedLines.add(line)) continue;
            System.out.println(line + " vp " + holder.getTotalVictoryPoints() + " scored "
                    + scoredPublics(game, holder) + " scorable "
                    + ti4.ai.scoring.ObjectivePolicy.scorablePublics(game, holder) + " secrets "
                    + holder.getSecretsUnscored().size() + "+" + holder.getSoScored() + " strat "
                    + holder.getStrategicCC() + " mecatol " + holder.controlsMecatol(true) + " active "
                    + (game.getActivePlayer() == null
                            ? "-"
                            : game.getActivePlayer().getFaction()));
        }
    }

    private static final java.util.Set<String> playedLines = java.util.concurrent.ConcurrentHashMap.newKeySet();

    // -Dai.selfplay.roundtwo.forceagenda=true credits the first seat with the custodians before round 1, so every
    // status phase leads into an agenda phase. It exists to check the agenda skip, not to measure play.
    private static void takeCustodians(Game game) {
        Integer custodians = game.getRevealedPublicObjectives().get(Constants.CUSTODIAN);
        if (custodians != null)
            game.scorePublicObjective(game.getRealPlayers().getFirst().getUserID(), custodians);
    }

    private static boolean inActionPhase(Game game) {
        return "action".equalsIgnoreCase(game.getPhaseOfGame());
    }

    private static boolean inAgendaPhase(Game game) {
        String phase = game.getPhaseOfGame();
        return phase != null && phase.toLowerCase(Locale.ROOT).startsWith("agenda");
    }

    // The referee flips the agenda in the tick that starts the agenda phase, so by the time the harness sees the phase
    // its reaction windows are out. Status-phase homework is over by then, so every live prompt belongs to the agenda:
    // they are all deleted, cards are readied the way the end of the agenda phase readies them, and the next strategy
    // phase starts.
    private static void skipAgenda(SelfPlayGame selfPlay, SelfPlayEnvironment env, Game game) {
        for (FakeMessage message : env.discord.allMessages()) {
            if (message.isDeleted() || message.buttons().isEmpty()) continue;
            message.proxy().delete().complete();
        }
        selfPlay.force(game, current -> {
            for (Player player : current.getRealPlayers())
                RefreshCardsService.refreshPlayerCards(current, player, false);
            StartPhaseService.startStrategyPhase(selfPlay.ownerEvent(), current);
        });
    }

    private static void recordCards(Game game, Map<String, List<String>> cards) {
        for (Player seat : game.getRealPlayers()) {
            String picked = String.join(
                    "+",
                    seat.getSCs().stream()
                            .map(initiative ->
                                    StrategyCard.of(game, initiative).name().toLowerCase(Locale.ROOT))
                            .toList());
            cards.computeIfAbsent(seat.getFaction(), key -> new ArrayList<>())
                    .add("r" + game.getRound() + " " + picked);
        }
    }

    private static List<String> scoredPublics(Game game, Player seat) {
        List<String> scored = new ArrayList<>();
        game.getScoredPublicObjectives().forEach((objective, players) -> {
            if (players.contains(seat.getUserID())) scored.add(objective);
        });
        return scored;
    }

    private static int publicPoints(String objective) {
        PublicObjectiveModel model = Mapper.getPublicObjective(objective);
        return model == null || model.getPoints() == null ? 0 : model.getPoints();
    }

    private static void report(List<GameResult> results) {
        int games = results.size();
        List<SeatResult> seats =
                results.stream().flatMap(result -> result.seats().stream()).toList();
        int seatCount = Math.max(1, seats.size());
        long incomplete = results.stream()
                .filter(result -> result.ending() != SelfPlayGame.Ending.STOP_CONDITION)
                .count();
        double[] perGame = results.stream().mapToDouble(GameResult::meanPoints).toArray();
        double[] perGameRoundOne = results.stream()
                .mapToDouble(GameResult::meanPointsAfterRoundOne)
                .toArray();
        System.out.printf(
                "ROUND2 SUMMARY over %d games (%d did not finish round 2, %d agenda phases skipped):"
                        + " points %.2f +- %.2f per seat after round 2 (%.2f +- %.2f after round 1),"
                        + " leader %.2f, table %.2f%n",
                games,
                incomplete,
                results.stream().mapToInt(GameResult::agendasSkipped).sum(),
                mean(perGame),
                standardError(perGame),
                mean(perGameRoundOne),
                standardError(perGameRoundOne),
                results.stream().mapToInt(GameResult::leaderPoints).average().orElse(0),
                results.stream().mapToInt(GameResult::tablePoints).average().orElse(0));
        System.out.printf(
                "ROUND2 POINTS BY SOURCE per seat: stage 1/2 public %.2f, secret %.2f,"
                        + " other (custodians, Imperial, promissory notes) %.2f%n",
                seats.stream().mapToInt(SeatResult::publicPoints).sum() / (double) seatCount,
                seats.stream().mapToInt(SeatResult::secretPoints).sum() / (double) seatCount,
                seats.stream().mapToInt(SeatResult::otherPoints).sum() / (double) seatCount);
        Map<Integer, Integer> distribution = new TreeMap<>();
        seats.forEach(seat -> distribution.merge(seat.points(), 1, Integer::sum));
        StringBuilder spread = new StringBuilder();
        distribution.forEach(
                (points, count) -> spread.append(String.format(" %d VP %.0f%%", points, 100.0 * count / seatCount)));
        System.out.println("ROUND2 DISTRIBUTION" + spread);
        System.out.printf(
                "ROUND2 TERRITORY per seat: own area %.2f of %.2f, planets gained %.2f%n",
                seats.stream().mapToDouble(SeatResult::ownAreaTaken).sum() / seatCount,
                seats.stream().mapToDouble(SeatResult::ownAreaAvailable).sum() / seatCount,
                seats.stream().mapToInt(SeatResult::planetsGained).sum() / (double) seatCount);
        reportFactions(seats);
        reportObjectives(results);
        reportCards(seats);
        if (Boolean.getBoolean("ai.selfplay.roundtwo.lines")) {
            for (GameResult result : results) {
                System.out.println("ROUND2 game " + result.ending() + " publics " + result.revealedPublics() + " "
                        + result.name());
                for (SeatResult seat : result.seats()) {
                    System.out.printf(
                            "ROUND2   %-8s %d -> %d VP, publics %s, secrets %s (holding %s), cards %s%n",
                            seat.faction(),
                            seat.pointsAfterRoundOne(),
                            seat.points(),
                            seat.scoredPublics(),
                            seat.scoredSecrets(),
                            seat.heldSecrets(),
                            seat.cardsByRound());
                }
            }
        }
    }

    private static void reportFactions(List<SeatResult> seats) {
        Map<String, double[]> byFaction = new TreeMap<>();
        for (SeatResult seat : seats) {
            double[] values = byFaction.computeIfAbsent(seat.faction(), key -> new double[4]);
            values[0] += seat.points();
            values[1] += seat.pointsAfterRoundOne();
            values[2] += seat.secretPoints();
            values[3]++;
        }
        byFaction.forEach((faction, values) -> System.out.printf(
                "ROUND2 FACTION %-8s points %.2f (round 1 %.2f), secrets %.2f%n",
                faction, values[0] / values[3], values[1] / values[3], values[2] / values[3]));
    }

    // For each public objective, how often it was out by the end of round 2 and what share of the seats that saw it
    // scored it. A low share on a common objective points at a scoring gap.
    private static void reportObjectives(List<GameResult> results) {
        Map<String, int[]> byObjective = new TreeMap<>();
        for (GameResult result : results) {
            for (String objective : result.revealedPublics()) {
                int[] values = byObjective.computeIfAbsent(objective, key -> new int[3]);
                values[0]++;
                values[1] += result.seats().size();
                values[2] += (int) result.seats().stream()
                        .filter(seat -> seat.scoredPublics().contains(objective))
                        .count();
            }
        }
        byObjective.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]))
                .forEach(entry -> System.out.printf(
                        "ROUND2 OBJECTIVE %-28s out in %3d games, scored by %3.0f%% of seats%n",
                        entry.getKey(), entry.getValue()[0], 100.0 * entry.getValue()[2] / entry.getValue()[1]));
        Map<String, int[]> bySecret = new TreeMap<>();
        for (GameResult result : results) {
            for (SeatResult seat : result.seats()) {
                seat.scoredSecrets().forEach(secret -> bySecret.computeIfAbsent(secret, key -> new int[2])[0]++);
                seat.heldSecrets().forEach(secret -> bySecret.computeIfAbsent(secret, key -> new int[2])[1]++);
            }
        }
        bySecret.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue()[0] + b.getValue()[1], a.getValue()[0] + a.getValue()[1]))
                .forEach(entry -> System.out.printf(
                        "ROUND2 SECRET %-28s scored %3d, still held %3d%n",
                        entry.getKey(), entry.getValue()[0], entry.getValue()[1]));
    }

    private static void reportCards(List<SeatResult> seats) {
        Map<String, double[]> byPick = new TreeMap<>();
        for (SeatResult seat : seats) {
            for (String pick : seat.cardsByRound()) {
                double[] values = byPick.computeIfAbsent(pick, key -> new double[2]);
                values[0]++;
                values[1] += seat.points();
            }
        }
        byPick.forEach((pick, values) -> System.out.printf(
                "ROUND2 PICK %-36s %3.0f seats, %.2f points by end of round 2%n",
                pick, values[0], values[1] / values[0]));
    }

    private static double mean(double[] values) {
        double sum = 0;
        for (double value : values) sum += value;
        return values.length == 0 ? 0 : sum / values.length;
    }

    private static double standardError(double[] values) {
        if (values.length < 2) return 0;
        double mean = mean(values);
        double squares = 0;
        for (double value : values) squares += (value - mean) * (value - mean);
        return Math.sqrt(squares / (values.length - 1) / values.length);
    }
}
