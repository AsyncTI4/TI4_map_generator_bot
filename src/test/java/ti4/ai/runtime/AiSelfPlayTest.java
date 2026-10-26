package ti4.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import ti4.ai.AiSettings;
import ti4.ai.actioncards.ActionCardPrior;
import ti4.ai.actioncards.AiActionCards;
import ti4.game.Game;
import ti4.game.persistence.GameManager;
import ti4.testUtils.BaseTi4Test;
import ti4.testUtils.discord.DiscordRouter;
import ti4.testUtils.discord.FakeChannel;

// Six AI seats play against each other in-process: a fake Discord stands in for the server, the AI runtime is
// ticked on a virtual clock, and every press runs the bot's real button handlers. A run ends at its stop
// condition, at the end of the game, or when nothing has changed for two virtual hours; the outcome carries a
// report of the game state, the AI's recent decisions and presses, and what the bot logged.
class AiSelfPlayTest extends BaseTi4Test {

    private static final List<String> FACTIONS = AiSettings.DEFAULT_FACTIONS;
    private static final long VIRTUAL_DAY = 24 * 60 * 60 * 1000L;
    private static final Set<SelfPlayGame.Ending> FINISHED =
            EnumSet.of(SelfPlayGame.Ending.VICTORY_POINTS, SelfPlayGame.Ending.OBJECTIVES_EXHAUSTED);

    @Test
    void sixAiSeatsPlayTheirFirstRound() throws Exception {
        SelfPlayGame.Outcome outcome = play(game -> game.getRound() >= 2, VIRTUAL_DAY, 5000);

        assertThat(outcome.ending()).as(outcome.report()).isEqualTo(SelfPlayGame.Ending.STOP_CONDITION);
    }

    // Whole games take a minute or two each and dice and decks are random, so they only run when asked for:
    // -Dai.selfplay.full=true, optionally with -Dai.selfplay.games=N to play several and report every outcome, and
    // -Dai.selfplay.threads=T to play up to T of them at once, each on its own thread with its own fake Discord
    // (default:
    // half the processors, about one game per physical core, where throughput levels off).
    @Test
    @EnabledIfSystemProperty(named = "ai.selfplay.full", matches = "true")
    void sixAiSeatsPlayFullGames() throws Exception {
        int games = Integer.getInteger("ai.selfplay.games", 1);
        int threads = Math.clamp(
                Integer.getInteger("ai.selfplay.threads", Runtime.getRuntime().availableProcessors() / 2), 1, games);
        long batchStart = System.nanoTime();
        List<SelfPlayGame.Outcome> played = playAll(games, threads);
        long batchSeconds = (System.nanoTime() - batchStart) / 1_000_000_000L;
        List<SelfPlayGame.Outcome> unfinished = new ArrayList<>();
        List<String> summary = new ArrayList<>();
        int leaderPoints = 0;
        int finalRounds = 0;
        List<SelfPlayGame.Outcome> outcomes = new ArrayList<>();
        int totalPoints = 0;
        for (int i = 0; i < games; i++) {
            SelfPlayGame.Outcome outcome = played.get(i);
            String line = "Game " + (i + 1) + "/" + games + ": " + outcome.ending() + " in round " + outcome.round()
                    + ", victory points " + outcome.victoryPoints() + ", wall time " + outcome.wallSeconds() + "s";
            summary.add(line);
            leaderPoints += outcome.leaderVictoryPoints();
            totalPoints += outcome.totalVictoryPoints();
            finalRounds += outcome.round();
            outcomes.add(outcome);
            if (!FINISHED.contains(outcome.ending())) unfinished.add(outcome);
        }
        // The batch summary is the measure of the AI's strength: the leader's victory points, the table's total and how
        // early the games finish.
        summary.forEach(System.out::println);
        System.out.printf(
                "Average leader victory points %.1f, average total %.1f, average final round %.1f over %d games%n",
                (double) leaderPoints / games, (double) totalPoints / games, (double) finalRounds / games, games);
        System.out.printf(
                "Batch wall time %ds for %d games on %d threads (%.1fs per game); %d Discord calls routed from bot"
                        + " threads%n",
                batchSeconds, games, threads, (double) batchSeconds / games, DiscordRouter.unboundCalls());
        System.out.println("Average points scored per round, whole table (games still running that round): "
                + perRound(outcomes.stream()
                        .map(SelfPlayGame.Outcome::tablePointsPerRound)
                        .toList()));
        System.out.println("Average leader victory points after each round: "
                + perRound(outcomes.stream()
                        .map(SelfPlayGame.Outcome::leaderPointsAfterRound)
                        .toList()));
        printTempo(outcomes);

        String reports = String.join(
                System.lineSeparator() + System.lineSeparator(),
                unfinished.stream().map(SelfPlayGame.Outcome::report).toList());
        assertThat(unfinished).as(reports).isEmpty();
    }

    // How much each seat gets done per round, and what it starts each round with: the levers behind scoring speed.
    private static void printTempo(List<SelfPlayGame.Outcome> outcomes) {
        System.out.println("Endings: "
                + outcomes.stream()
                        .collect(java.util.stream.Collectors.groupingBy(
                                SelfPlayGame.Outcome::ending,
                                java.util.TreeMap::new,
                                java.util.stream.Collectors.counting())));
        printTrading(outcomes);
        printPromissoryNotes(outcomes);
        printRelics(outcomes);
        printActionCards(outcomes);
        printSecrets(outcomes);
        printObjectives(outcomes);
        printPlanKinds(outcomes);
        printMecatol(outcomes);
        printExpeditions(outcomes);
        printFracture(outcomes);
        printExpansionZones(outcomes);
        System.out.println("Tactical actions per seat each round: "
                + perRoundDecimal(
                        outcomes.stream()
                                .map(outcome -> outcome.perSeat(SelfPlayEnvironment.TACTICAL_ACTION::equals))
                                .toList(),
                        1));
        System.out.println("Strategy card follows per seat each round: "
                + perRoundDecimal(
                        outcomes.stream()
                                .map(outcome -> outcome.perSeat(kind -> kind.startsWith("follow ")))
                                .toList(),
                        1));
        for (String follow :
                List.of("follow Imperial", "follow Technology", "follow Construction", "follow Leadership")) {
            System.out.println("  " + follow + " per seat each round: "
                    + perRoundDecimal(
                            outcomes.stream()
                                    .map(outcome -> outcome.perSeat(follow::equals))
                                    .toList(),
                            1));
        }
        System.out.println("Trade presses per seat each round: "
                + perRoundDecimal(
                        outcomes.stream()
                                .map(outcome -> outcome.perSeat(SelfPlayEnvironment.TRADE::equals))
                                .toList(),
                        1));
        for (String key : List.of("tactic", "fleet", "strategy", "planets", "techs")) {
            System.out.println("Average " + key + " per seat at the start of each round: "
                    + perRoundDecimal(
                            outcomes.stream()
                                    .map(outcome -> outcome.atRoundStart(key))
                                    .toList(),
                            2));
        }
    }

    // Secret objectives scored per seat by phase, and the average round in which the action secrets were scored (run
    // once with -Dai.secrets.hold=false to compare how patience moves the timing).
    private static void printSecrets(List<SelfPlayGame.Outcome> outcomes) {
        int seats = Math.max(
                1, outcomes.stream().mapToInt(SelfPlayGame.Outcome::seats).sum());
        int action = outcomes.stream()
                .mapToInt(outcome -> outcome.secrets().action())
                .sum();
        int status = outcomes.stream()
                .mapToInt(outcome -> outcome.secrets().status())
                .sum();
        int agenda = outcomes.stream()
                .mapToInt(outcome -> outcome.secrets().agenda())
                .sum();
        int other =
                outcomes.stream().mapToInt(outcome -> outcome.secrets().other()).sum();
        List<Integer> rounds = outcomes.stream()
                .flatMap(outcome -> outcome.secrets().actionSecretRounds().stream())
                .toList();
        System.out.printf(
                "Secrets %s: scored per seat per game action %.2f, status %.2f, agenda %.2f, other %.2f; the %d action secrets were"
                        + " scored in round %.1f on average; average final round %.1f, leader victory points %.1f, table total %.1f%n",
                AiSettings.isSecretHoldEnabled() ? "patient" : "pursue at once",
                (double) action / seats,
                (double) status / seats,
                (double) agenda / seats,
                (double) other / seats,
                rounds.size(),
                rounds.stream().mapToInt(Integer::intValue).average().orElse(0),
                outcomes.stream()
                        .mapToInt(SelfPlayGame.Outcome::round)
                        .average()
                        .orElse(0),
                outcomes.stream()
                        .mapToInt(SelfPlayGame.Outcome::leaderVictoryPoints)
                        .average()
                        .orElse(0),
                outcomes.stream()
                        .mapToInt(SelfPlayGame.Outcome::totalVictoryPoints)
                        .average()
                        .orElse(0));
    }

    // Where the points come from and where they are missed: the leader's points by source, how many seats score in each
    // status phase, and for every public objective how many seats scored it and how far short the others were.
    private static void printObjectives(List<SelfPlayGame.Outcome> outcomes) {
        List<ObjectiveTracker.Summary> summaries =
                outcomes.stream().map(SelfPlayGame.Outcome::objectives).toList();
        printLeaderPath(summaries);
        System.out.println("Seats scoring a public objective in each status phase: "
                + perRoundDecimal(
                        summaries.stream()
                                .map(summary -> summary.scoringShareByRound(true))
                                .toList(),
                        1));
        System.out.println("Seats scoring a secret objective in each status phase: "
                + perRoundDecimal(
                        summaries.stream()
                                .map(summary -> summary.scoringShareByRound(false))
                                .toList(),
                        1));
        printObjectiveRates(summaries);
        printSecretMix(summaries);
        ProduceTracker.printBatch();
    }

    // Every secret scored over the batch, most scored first, with its share of all scored secrets: the mix to hold
    // against how often human players complete each secret.
    private static void printSecretMix(List<ObjectiveTracker.Summary> summaries) {
        Map<String, Long> counts = summaries.stream()
                .flatMap(summary -> summary.secretsScored().stream())
                .collect(java.util.stream.Collectors.groupingBy(id -> id, java.util.stream.Collectors.counting()));
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        String mix = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                .map(entry -> String.format(
                        "%s %d (%.0f%%)",
                        secretName(entry.getKey()), entry.getValue(), 100.0 * entry.getValue() / total))
                .collect(java.util.stream.Collectors.joining(", "));
        System.out.println("Secrets scored over the batch (" + total + "): " + mix);
        printSecretConversion(summaries);
    }

    // How often a seat that held each secret at some point of the game went on to score it, most held first.
    private static void printSecretConversion(List<ObjectiveTracker.Summary> summaries) {
        Map<String, Long> held = summaries.stream()
                .flatMap(summary -> summary.secretsHeld().stream())
                .collect(java.util.stream.Collectors.groupingBy(id -> id, java.util.stream.Collectors.counting()));
        Map<String, Long> scored = summaries.stream()
                .flatMap(summary -> summary.secretsScored().stream())
                .collect(java.util.stream.Collectors.groupingBy(id -> id, java.util.stream.Collectors.counting()));
        String rows = held.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                .map(entry -> String.format(
                        "%s %d/%d",
                        secretName(entry.getKey()), scored.getOrDefault(entry.getKey(), 0L), entry.getValue()))
                .collect(java.util.stream.Collectors.joining(", "));
        System.out.println("Secrets scored/held (seats that held each one at some point): " + rows);
        printGroundProgress(summaries);
    }

    // For each holder of Mechanize the Military (4 mech planets) or Occupy the Fringe (9 ground forces on a planet
    // without a dock): the most progress it had at a status phase, as a count of holders per step; 99 is scored.
    private static void printGroundProgress(List<ObjectiveTracker.Summary> summaries) {
        Map<String, java.util.TreeMap<Integer, Integer>> steps = new java.util.TreeMap<>();
        for (ObjectiveTracker.Summary summary : summaries) {
            summary.groundProgress()
                    .forEach((secret, progress) ->
                            progress.forEach(best -> steps.computeIfAbsent(secret, id -> new java.util.TreeMap<>())
                                    .merge(best, 1, Integer::sum)));
        }
        steps.forEach((secret, counts) -> System.out.println(
                "Best progress per holder at a status phase, " + secretName(secret) + ": " + counts));
        printMechanizeHolders(summaries);
    }

    // Each Mechanize the Military holder at each status phase: mechs on the board / planets with a mech / mechs at
    // home, carriers (at home), mechs built so far and mechs that first reached a planet away from home.
    private static void printMechanizeHolders(List<ObjectiveTracker.Summary> summaries) {
        int index = 0;
        for (ObjectiveTracker.Summary summary : summaries) {
            index++;
            for (String line : summary.secretHolders()) System.out.println("Holder g" + index + " " + line);
        }
        int game = 0;
        for (ObjectiveTracker.Summary summary : summaries) {
            game++;
            for (ObjectiveTracker.MechanizeHolder holder : summary.mechanize()) {
                System.out.printf(
                        "Mechanize holder g%d %s r%d: mechs %d on %d planets (%d at home), carriers %d (%d at home), built %d, arrived away %d, home infantry %d, kept home %d, movable home mechs %d, bare held planets %d%s%n",
                        game,
                        holder.faction(),
                        holder.round(),
                        holder.mechs(),
                        holder.mechPlanets(),
                        holder.mechsHome(),
                        holder.carriers(),
                        holder.carriersHome(),
                        holder.built(),
                        holder.arrivedAway(),
                        holder.homeInfantry(),
                        holder.keptHome(),
                        holder.movableHomeMechs(),
                        holder.bareHeldPlanets(),
                        holder.scored() ? ", scored" : "");
            }
        }
    }

    private static String secretName(String id) {
        ti4.model.SecretObjectiveModel model = ti4.model.Mapper.getSecretObjective(id);
        return model == null ? id : model.getName();
    }

    // The breakdown of the bot's "Winners Path to Victory" statistic for the seat that led at the end, averaged per
    // game,
    // so it can be read next to the same statistic over human games.
    private static void printLeaderPath(List<ObjectiveTracker.Summary> summaries) {
        double games = Math.max(1, summaries.size());
        int stageOne = 0;
        int stageTwo = 0;
        int secrets = 0;
        int supports = 0;
        Map<String, Integer> others = new java.util.TreeMap<>();
        for (ObjectiveTracker.Summary summary : summaries) {
            ti4.service.statistics.game.WinningPathBreakdown path = summary.leaderPath();
            stageOne += path.stage1s();
            stageTwo += path.stage2s();
            secrets += path.secrets();
            supports += path.supports();
            path.otherPoints().forEach((source, points) -> others.merge(source, points, Integer::sum));
        }
        String otherSources = others.entrySet().stream()
                .map(entry -> String.format("%s %.2f", entry.getKey(), entry.getValue() / games))
                .collect(java.util.stream.Collectors.joining(", "));
        System.out.printf(
                "Leader's points by source per game (as in Winners Path to Victory): stage I %.2f, stage II %.2f"
                        + " (%.2f points), secrets %.2f, Support for the Throne %.2f, other %.2f (%s) over %d games%n",
                stageOne / games,
                stageTwo / games,
                2 * stageTwo / games,
                secrets / games,
                supports / games,
                others.values().stream().mapToInt(Integer::intValue).sum() / games,
                otherSources,
                summaries.size());
    }

    // Per public objective, by stage and from the least scored up: the share of seats that scored it out of the seats
    // it
    // was revealed to, and, for the seats that had not scored it when a status phase came, how far short they were
    // ("met" counts seats that qualified, with how many of them scored it there).
    private static void printObjectiveRates(List<ObjectiveTracker.Summary> summaries) {
        Map<String, ObjectiveTally> tallies = new java.util.HashMap<>();
        for (ObjectiveTracker.Summary summary : summaries) {
            for (ObjectiveTracker.Reveal reveal : summary.reveals()) {
                ObjectiveTally tally = tallies.computeIfAbsent(reveal.objective(), _ -> new ObjectiveTally());
                tally.points = reveal.points();
                tally.games++;
                tally.seatReveals += summary.seats();
                tally.scorers += reveal.scorers();
            }
            for (ObjectiveTracker.Chance chance : summary.chances()) {
                ObjectiveTally tally = tallies.get(chance.objective());
                if (tally != null) tally.add(chance);
            }
        }
        for (int stage = 1; stage <= 2; stage++) {
            int points = stage;
            List<Map.Entry<String, ObjectiveTally>> rows = tallies.entrySet().stream()
                    .filter(entry -> entry.getValue().points == points)
                    .sorted(Comparator.comparingDouble((Map.Entry<String, ObjectiveTally> entry) ->
                                    entry.getValue().scoredShare())
                            .thenComparing(Map.Entry::getKey))
                    .toList();
            int scorers =
                    rows.stream().mapToInt(entry -> entry.getValue().scorers).sum();
            int seatReveals = rows.stream()
                    .mapToInt(entry -> entry.getValue().seatReveals)
                    .sum();
            System.out.printf(
                    "Stage %s public objectives: scored by %.0f%% of the seats they were revealed to (%d of %d)%n",
                    stage == 1 ? "I" : "II",
                    seatReveals == 0 ? 0 : 100.0 * scorers / seatReveals,
                    scorers,
                    seatReveals);
            for (Map.Entry<String, ObjectiveTally> row : rows) {
                System.out.println("  " + objectiveName(row.getKey()) + ": " + row.getValue());
            }
        }
    }

    private static String objectiveName(String id) {
        ti4.model.PublicObjectiveModel model = ti4.model.Mapper.getPublicObjective(id);
        return model == null ? id : model.getName();
    }

    private static final class ObjectiveTally {
        int points;
        int games;
        int seatReveals;
        int scorers;
        int unscoredAtStatus;
        int met;
        int metAndScored;
        int shortByOne;
        int shortByTwo;
        int shortByMore;
        int unmodelled;

        void add(ObjectiveTracker.Chance chance) {
            unscoredAtStatus++;
            switch (chance.shortBy()) {
                case ObjectiveTracker.UNMODELLED -> unmodelled++;
                case 0 -> {
                    met++;
                    if (chance.scored()) metAndScored++;
                }
                case 1 -> shortByOne++;
                case 2 -> shortByTwo++;
                default -> shortByMore++;
            }
        }

        double scoredShare() {
            return seatReveals == 0 ? 0 : (double) scorers / seatReveals;
        }

        @Override
        public String toString() {
            String shortfall = unmodelled == unscoredAtStatus && unscoredAtStatus > 0
                    ? "not measured"
                    : String.format(
                            "met %d (scored %d), short by 1: %d, 2: %d, 3+: %d%s",
                            met,
                            metAndScored,
                            shortByOne,
                            shortByTwo,
                            shortByMore,
                            unmodelled == 0 ? "" : ", not measured " + unmodelled);
            return String.format(
                    "%.0f%% of seats (%d of %d, %d games); unscored before a status phase %d: %s",
                    100 * scoredShare(), scorers, seatReveals, games, unscoredAtStatus, shortfall);
        }
    }

    // Tactical actions by the kind of plan behind them, per game over the batch: attacks, attacks on another player's
    // home (and those that landed), attacks on Mecatol Rex (and those that landed, or stood against an Imperial
    // holder), and positioning moves.
    private static void printPlanKinds(List<SelfPlayGame.Outcome> outcomes) {
        int games = Math.max(1, outcomes.size());
        List<String> kinds = List.of(
                "expand",
                "attack",
                "produce",
                "position",
                "defend",
                "stage",
                "garrison",
                "attack on another home",
                "attack landing on a home",
                "attack on Mecatol",
                "attack landing on Mecatol",
                "attack on Mecatol against Imperial",
                "seat stack left by expand",
                "seat stack left by attack",
                "seat stack left by position",
                "seat stack left by stage",
                "seat stack left by pickup",
                "seat stack left by defend",
                "seat stack left by produce");
        List<String> parts = new ArrayList<>();
        for (String kind : kinds) {
            int total = outcomes.stream()
                    .mapToInt(outcome -> outcome.total(SelfPlayEnvironment.PLAN_PREFIX + kind))
                    .sum();
            parts.add(String.format("%s %.2f", kind, (double) total / games));
        }
        System.out.println("Tactical plans per game: " + String.join(", ", parts));
        long samples = SelfPlayEnvironment.RANKED_SAMPLES.sum();
        System.out.printf(
                "TacticalPlanner.ranked on real boards: mean %.1f ms, max %.1f ms over %d samples%n",
                samples == 0 ? 0 : SelfPlayEnvironment.RANKED_NANOS.sum() / 1e6 / samples,
                SelfPlayEnvironment.RANKED_MAX_NANOS.get() / 1e6,
                samples);
        System.out.printf(
                "Stage plans: chosen %.2f per game, present in %d of %d sampled rankings%n",
                (double) outcomes.stream()
                                .mapToInt(outcome -> outcome.total(SelfPlayEnvironment.PLAN_PREFIX + "stage"))
                                .sum()
                        / games,
                SelfPlayEnvironment.RANKED_WITH_STAGE.sum(),
                samples);
    }

    // Where expansions went, by the territory zone of the target, as a share of the expansions in each round band and
    // as a count per game.
    private static void printExpansionZones(List<SelfPlayGame.Outcome> outcomes) {
        int games = Math.max(1, outcomes.size());
        List<String> areas = List.of(
                SelfPlayEnvironment.EXPAND_OWN_AREA,
                SelfPlayEnvironment.EXPAND_BORDER,
                SelfPlayEnvironment.EXPAND_ANOTHERS_AREA,
                SelfPlayEnvironment.EXPAND_MECATOL,
                SelfPlayEnvironment.EXPAND_ELSEWHERE);
        for (String band : SelfPlayEnvironment.ROUND_BANDS) {
            List<Integer> counts = areas.stream()
                    .map(area -> outcomes.stream()
                            .mapToInt(outcome -> outcome.total(SelfPlayEnvironment.PLAN_PREFIX + area + " " + band))
                            .sum())
                    .toList();
            int all = counts.stream().mapToInt(Integer::intValue).sum();
            List<String> parts = new ArrayList<>();
            for (int i = 0; i < areas.size(); i++) {
                parts.add(String.format(
                        "%s %.2f per game (%.0f%%)",
                        areas.get(i).substring("expand ".length()),
                        (double) counts.get(i) / games,
                        all == 0 ? 0 : 100.0 * counts.get(i) / all));
            }
            System.out.println("Expansion targets " + band + ": " + String.join(", ", parts));
        }
    }

    // The Thunder's Edge expedition over the batch: slices claimed per seat by round and by kind, the Fracture rolls
    // and
    // entries, the ingress tokens placed, and who ended up with Thunder's Edge.
    private static void printExpeditions(List<SelfPlayGame.Outcome> outcomes) {
        int games = Math.max(1, outcomes.size());
        int seats = Math.max(
                1, outcomes.stream().mapToInt(SelfPlayGame.Outcome::seats).sum());
        List<ExpeditionTracker.Claim> claims = outcomes.stream()
                .flatMap(outcome -> outcome.expeditions().claims().stream())
                .toList();
        Map<Integer, Long> byRound = claims.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        ExpeditionTracker.Claim::round,
                        java.util.TreeMap::new,
                        java.util.stream.Collectors.counting()));
        Map<String, Long> byKind = claims.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        ExpeditionTracker.Claim::kind, java.util.TreeMap::new, java.util.stream.Collectors.counting()));
        Map<String, Long> byFaction = claims.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        ExpeditionTracker.Claim::faction,
                        java.util.TreeMap::new,
                        java.util.stream.Collectors.counting()));
        long seatsWithAClaim = outcomes.stream()
                .mapToLong(outcome -> outcome.expeditions().claims().stream()
                        .map(ExpeditionTracker.Claim::faction)
                        .distinct()
                        .count())
                .sum();
        System.out.printf(
                "Expeditions: %.2f slices claimed per game, %.0f%% of seats claimed at least one; by round %s; by kind %s; by faction %s%n",
                (double) claims.size() / games, 100.0 * seatsWithAClaim / seats, byRound, byKind, byFaction);
        printCardPicks(outcomes);
        System.out.printf(
                "Fracture: %.2f rolls per game, entered in %d of %d games (rounds %s), %.2f ingress tokens on the board per game, %.2f ingress presses per game%n",
                (double) outcomes.stream()
                                .mapToInt(outcome -> outcome.expeditions().rolls())
                                .sum()
                        / games,
                outcomes.stream()
                        .filter(outcome -> outcome.expeditions().fractureEntered())
                        .count(),
                games,
                outcomes.stream()
                        .filter(outcome -> outcome.expeditions().fractureEntered())
                        .map(outcome -> outcome.expeditions().fractureRound())
                        .toList(),
                (double) outcomes.stream()
                                .mapToInt(outcome -> outcome.expeditions().ingressTokens())
                                .sum()
                        / games,
                (double) outcomes.stream()
                                .mapToInt(outcome -> outcome.expeditions().ingressPresses())
                                .sum()
                        / games);
        System.out.println("Thunder's Edge: placed in "
                + outcomes.stream()
                        .filter(outcome -> outcome.expeditions().thundersEdgePlaced())
                        .count() + " of "
                + games + " games; round, most slices, infantry owner: "
                + outcomes.stream()
                        .filter(outcome -> outcome.expeditions().thundersEdgePlaced())
                        .map(outcome -> "r" + outcome.expeditions().thundersEdgeRound() + " "
                                + outcome.expeditions().mostSlices() + " -> "
                                + outcome.expeditions().infantryOwner())
                        .toList());
    }

    // The strategy cards picked in round one, and how often a pick's plan had an expedition use (any, and as its main
    // use), over the whole game as well.
    private static void printCardPicks(List<SelfPlayGame.Outcome> outcomes) {
        List<ExpeditionTracker.CardPick> picks = outcomes.stream()
                .flatMap(outcome -> outcome.expeditions().picks().stream())
                .toList();
        List<ExpeditionTracker.CardPick> roundOne =
                picks.stream().filter(pick -> pick.round() == 1).toList();
        Map<String, Long> byCard = roundOne.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        ExpeditionTracker.CardPick::card,
                        java.util.TreeMap::new,
                        java.util.stream.Collectors.counting()));
        System.out.printf(
                "Round one strategy card picks (%d): %s; with an expedition use %d (main use %d); all rounds %d picks,"
                        + " expedition use %d (main use %d); expedition use by card %s%n",
                roundOne.size(),
                byCard,
                roundOne.stream()
                        .filter(ExpeditionTracker.CardPick::expeditionUse)
                        .count(),
                roundOne.stream()
                        .filter(ExpeditionTracker.CardPick::expeditionMain)
                        .count(),
                picks.size(),
                picks.stream().filter(ExpeditionTracker.CardPick::expeditionUse).count(),
                picks.stream()
                        .filter(ExpeditionTracker.CardPick::expeditionMain)
                        .count(),
                picks.stream()
                        .filter(ExpeditionTracker.CardPick::expeditionUse)
                        .collect(java.util.stream.Collectors.groupingBy(
                                ExpeditionTracker.CardPick::card,
                                java.util.TreeMap::new,
                                java.util.stream.Collectors.counting())));
        List<ExpeditionTracker.Claim> claims = outcomes.stream()
                .flatMap(outcome -> outcome.expeditions().claims().stream())
                .toList();
        Map<String, Map<Integer, Long>> byKindAndRound = claims.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        ExpeditionTracker.Claim::kind,
                        java.util.TreeMap::new,
                        java.util.stream.Collectors.groupingBy(
                                ExpeditionTracker.Claim::round,
                                java.util.TreeMap::new,
                                java.util.stream.Collectors.counting())));
        System.out.println("Expedition claims by kind and round: " + byKindAndRound);
    }

    // The Fracture over the batch (-Dai.selfplay.fracture=true): for each planet, in how many games it was taken and
    // the rounds and seats that took it, and how often Styx changed hands.
    private static void printFracture(List<SelfPlayGame.Outcome> outcomes) {
        if (!FractureTracker.enabled()) return;
        int games = outcomes.size();
        for (String planet : FractureTracker.PLANETS) {
            List<String> takes = new ArrayList<>();
            for (int index = 0; index < games; index++) {
                String label = "g" + (index + 1);
                outcomes.get(index).fracture().changes().stream()
                        .filter(change ->
                                change.planet().equals(planet) && !change.to().isEmpty())
                        .forEach(change -> takes.add(label + " r" + change.round() + " " + change.to()
                                + (change.from().isEmpty() ? "" : " (from " + change.from() + ")")));
            }
            long gamesTaken = outcomes.stream()
                    .filter(outcome -> outcome.fracture().tookEvery(planet))
                    .count();
            long gamesAttacked = outcomes.stream()
                    .filter(outcome -> outcome.fracture().attacked(planet))
                    .count();
            System.out.println("Fracture " + planet + ": attacked in " + gamesAttacked + "/" + games
                    + " games, taken in " + gamesTaken + "/" + games + " games"
                    + (takes.isEmpty() ? "" : "; " + String.join(", ", takes)));
        }
        System.out.println("Styx changes of hands per game: "
                + outcomes.stream()
                        .map(outcome -> outcome.fracture().styxChanges())
                        .toList());
    }

    // The custodians race over the batch: the round the token went, who took it and whether they went first, the
    // Politics, speaker and Leadership chain, and how often Mecatol Rex changed hands afterwards.
    private static void printMecatol(List<SelfPlayGame.Outcome> outcomes) {
        List<MecatolTracker.Summary> summaries =
                outcomes.stream().map(SelfPlayGame.Outcome::mecatol).toList();
        int lastRound = summaries.stream()
                .mapToInt(MecatolTracker.Summary::custodiansRound)
                .max()
                .orElse(0);
        List<String> byRound = new ArrayList<>();
        for (int round = 1; round <= lastRound; round++) {
            int taken = round;
            long games = summaries.stream()
                    .filter(summary -> summary.custodiansRound() == taken)
                    .count();
            byRound.add("r" + round + " " + games);
        }
        byRound.add("never "
                + summaries.stream().filter(summary -> !summary.taken()).count());
        java.util.Map<String, Long> takers = summaries.stream()
                .filter(summary -> summary.taken() && !summary.taker().isEmpty())
                .collect(java.util.stream.Collectors.groupingBy(
                        MecatolTracker.Summary::taker, java.util.TreeMap::new, java.util.stream.Collectors.counting()));
        System.out.println("Custodians taken by round: " + String.join(", ", byRound) + "; takers " + takers
                + "; taker had initiative <=1 "
                + summaries.stream()
                        .filter(MecatolTracker.Summary::takerWentFirst)
                        .count()
                + ", was speaker "
                + summaries.stream()
                        .filter(summary -> summary.taken() && summary.takerWasSpeaker())
                        .count()
                + ", first action "
                + summaries.stream()
                        .filter(summary -> summary.taken() && summary.takerFirstAction())
                        .count());
        System.out.println("Round 1 Politics then round 2 speaker and Leadership by the same seat: "
                + summaries.stream()
                        .filter(MecatolTracker.Summary::politicsSpeakerLeadershipChain)
                        .count()
                + " games");
        long takenGames =
                summaries.stream().filter(MecatolTracker.Summary::taken).count();
        System.out.printf(
                "Mecatol control changes per game after custodians: %.2f (over %d games that took them)%n",
                takenGames == 0
                        ? 0
                        : (double) summaries.stream()
                                        .mapToInt(MecatolTracker.Summary::controlChanges)
                                        .sum()
                                / takenGames,
                takenGames);
    }

    // What trading did over the batch, next to the strength measures it should move (run once with -Dai.trading=false
    // to compare), and every trade invariant a game broke.
    private static void printTrading(List<SelfPlayGame.Outcome> outcomes) {
        int games = Math.max(1, outcomes.size());
        int trades =
                outcomes.stream().mapToInt(outcome -> outcome.trade().trades()).sum();
        int offers = outcomes.stream()
                .mapToInt(outcome -> outcome.trade().offersSent())
                .sum();
        int gained = outcomes.stream()
                .mapToInt(outcome -> outcome.trade().tradeGoodsGained())
                .sum();
        int seats = outcomes.stream().mapToInt(SelfPlayGame.Outcome::seats).sum();
        int settlementsSent = outcomes.stream()
                .mapToInt(outcome -> outcome.trade().settlementsSent())
                .sum();
        int settlementsAccepted = outcomes.stream()
                .mapToInt(outcome -> outcome.trade().settlementsAccepted())
                .sum();
        System.out.printf(
                "Trading %s: %.1f accepted offers per game, acceptance rate %.0f%% (%d of %d offers sent),"
                        + " %.2f trade goods gained by trading per seat per game, Trade settlements accepted %d of %d;"
                        + " average final round %.1f, leader victory points %.1f, table total %.1f%n",
                AiSettings.isTradingEnabled() ? "on" : "off",
                (double) trades / games,
                offers == 0 ? 0 : 100.0 * trades / offers,
                trades,
                offers,
                seats == 0 ? 0 : (double) gained / seats,
                settlementsAccepted,
                settlementsSent,
                outcomes.stream()
                        .mapToInt(SelfPlayGame.Outcome::round)
                        .average()
                        .orElse(0),
                outcomes.stream()
                        .mapToInt(SelfPlayGame.Outcome::leaderVictoryPoints)
                        .average()
                        .orElse(0),
                outcomes.stream()
                        .mapToInt(SelfPlayGame.Outcome::totalVictoryPoints)
                        .average()
                        .orElse(0));
        for (int game = 0; game < outcomes.size(); game++) {
            for (String violation : outcomes.get(game).trade().violations()) {
                System.out.println("TRADE_INVARIANT game " + (game + 1) + " violated: " + violation);
            }
        }
    }

    // How often promissory notes change hands and get played, or sit unused: over the batch, then note by note (the
    // colour versions of a generic note, such as Support for the Throne, count as one note). "Given" counts each note
    // its owner gave away at least once; "unused" counts the notes still in their owner's hand that never left it.
    private static void printPromissoryNotes(List<SelfPlayGame.Outcome> outcomes) {
        int games = Math.max(1, outcomes.size());
        List<PromissoryTracker.Summary> summaries =
                outcomes.stream().map(SelfPlayGame.Outcome::promissory).toList();
        long owned = summaries.stream().mapToLong(s -> s.ownedNotes().size()).sum();
        long given = summaries.stream()
                .mapToLong(PromissoryTracker.Summary::givenByOwners)
                .sum();
        long transfers = summaries.stream().mapToLong(s -> s.transfers().size()).sum();
        long playedByOthers = summaries.stream()
                .mapToLong(PromissoryTracker.Summary::playedByOthers)
                .sum();
        long playedOwn = summaries.stream().mapToLong(s -> s.plays().size()).sum() - playedByOthers;
        long unused =
                summaries.stream().mapToLong(s -> s.neverLeftOwner().size()).sum();
        long heldUnplayed =
                summaries.stream().mapToLong(s -> s.receivedUnplayed().size()).sum();
        System.out.printf(
                "Promissory notes: %.1f changes of hands per game; %.0f%% of notes given away by their owner (%d of"
                        + " %d), %.0f%% never left their owner; %.1f plays of a received note and %.1f of an own note"
                        + " per game; %.1f received notes still unplayed at the end per game%n",
                (double) transfers / games,
                owned == 0 ? 0 : 100.0 * given / owned,
                given,
                owned,
                owned == 0 ? 0 : 100.0 * unused / owned,
                (double) playedByOthers / games,
                (double) playedOwn / games,
                (double) heldUnplayed / games);

        Map<String, long[]> byNote = new java.util.TreeMap<>();
        for (PromissoryTracker.Summary summary : summaries) {
            summary.ownedNotes().forEach(key -> byNote.computeIfAbsent(noteOf(key), ignored -> new long[5])[0]++);
            summary.transfers().stream()
                    .filter(PromissoryTracker.Transfer::givenByOwner)
                    .map(transfer -> transfer.owner() + "|" + transfer.note())
                    .distinct()
                    .forEach(key -> byNote.computeIfAbsent(noteOf(key), ignored -> new long[5])[1]++);
            summary.plays().forEach(play -> byNote.computeIfAbsent(play.note(), ignored -> new long[5])[2]++);
            summary.neverLeftOwner().forEach(key -> byNote.computeIfAbsent(noteOf(key), ignored -> new long[5])[3]++);
            summary.receivedUnplayed().forEach(key -> byNote.computeIfAbsent(noteOf(key), ignored -> new long[5])[4]++);
        }
        List<String> perNote = byNote.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<String, long[]> entry) -> entry.getValue()[0])
                        .reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(entry -> String.format(
                        "%s: owned %d, given %d, played %d, unused %d, received but unplayed at end %d",
                        entry.getKey(),
                        entry.getValue()[0],
                        entry.getValue()[1],
                        entry.getValue()[2],
                        entry.getValue()[3],
                        entry.getValue()[4]))
                .toList();
        System.out.println("Promissory notes by note over " + outcomes.size() + " games:");
        perNote.forEach(line -> System.out.println("  " + line));
    }

    // Which relics the seats drew and what they did with them over the batch, relic by relic: games in which it was
    // drawn, the average round it was gained, how often it was used (a purge, or an exhaust of an exhaust relic), how
    // often it changed hands and how often it was still held unused at the end; and the victory points of the seats
    // that held one against the table average.
    private static void printRelics(List<SelfPlayGame.Outcome> outcomes) {
        int games = Math.max(1, outcomes.size());
        Map<String, long[]> byRelic = new java.util.TreeMap<>();
        Map<String, List<Integer>> drawRounds = new java.util.TreeMap<>();
        Map<String, List<Integer>> firstUseRounds = new java.util.TreeMap<>();
        Map<String, List<Integer>> holderPoints = new java.util.TreeMap<>();
        long gains = 0;
        double tablePoints = 0;
        int seats = 0;
        for (SelfPlayGame.Outcome outcome : outcomes) {
            RelicTracker.Summary summary = outcome.relics();
            gains += summary.gains().size();
            summary.gains().forEach(gain -> {
                long[] row = byRelic.computeIfAbsent(gain.relic(), ignored -> new long[5]);
                row[gain.fromSeat() == null ? 0 : 3]++;
                if (gain.fromSeat() != null) return;
                drawRounds
                        .computeIfAbsent(gain.relic(), ignored -> new ArrayList<>())
                        .add(gain.round());
                holderPoints
                        .computeIfAbsent(gain.relic(), ignored -> new ArrayList<>())
                        .add(summary.points().getOrDefault(gain.faction(), 0));
            });
            summary.spends().forEach(spend -> byRelic.computeIfAbsent(spend.relic(), ignored -> new long[5])[1]++);
            summary.spends().stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            spend -> spend.relic() + "|" + spend.faction(),
                            java.util.stream.Collectors.minBy(Comparator.comparingInt(RelicTracker.Spend::round))))
                    .values()
                    .forEach(first -> first.ifPresent(spend -> firstUseRounds
                            .computeIfAbsent(spend.relic(), ignored -> new ArrayList<>())
                            .add(spend.round())));
            summary.heldAtEnd().keySet().forEach(relic -> byRelic.computeIfAbsent(relic, ignored -> new long[5])[2]++);
            for (Integer points : summary.points().values()) {
                tablePoints += points;
                seats++;
            }
        }
        double tableAverage = seats == 0 ? 0 : tablePoints / seats;
        System.out.printf(
                "Relics: %.1f gained per game; the table averaged %.2f points%n", (double) gains / games, tableAverage);
        System.out.println("Relics by relic over " + outcomes.size() + " games:");
        byRelic.forEach((relic, row) -> System.out.printf(
                "  %s: drawn %d (average round %.1f), used %d (first use in round %.1f), passed between seats %d, held at the end %d, holders finished on %.2f points (%+.2f)%n",
                relic,
                row[0],
                average(drawRounds.get(relic)),
                row[1],
                average(firstUseRounds.get(relic)),
                row[3],
                row[2],
                average(holderPoints.get(relic)),
                average(holderPoints.get(relic)) - tableAverage));
    }

    private static double average(List<Integer> values) {
        return values == null
                ? 0
                : values.stream().mapToInt(Integer::intValue).average().orElse(0);
    }

    private static String noteOf(String seatAndNote) {
        return seatAndNote.substring(seatAndNote.indexOf('|') + 1);
    }

    // What the AI did with its action cards over the batch: how many it played and had cancelled, each card's rate next
    // to how often human players play it when they draw it, and in an A/B run (-Dai.actioncards.ab=true) how the seats
    // with the statistics-based rules did against the seats with the earlier ones.
    private static void printActionCards(List<SelfPlayGame.Outcome> outcomes) {
        int games = Math.max(1, outcomes.size());
        int seats = outcomes.stream().mapToInt(SelfPlayGame.Outcome::seats).sum();
        List<SelfPlayGame.ActionCardUse> plays = outcomes.stream()
                .flatMap(outcome -> outcome.actionCards().plays().stream())
                .toList();
        long cancelled =
                plays.stream().filter(SelfPlayGame.ActionCardUse::canceled).count();
        long sabotages =
                plays.stream().filter(play -> "Sabotage".equals(play.card())).count();
        System.out.printf(
                "Action cards: %.2f played per seat per game (%d plays, %d seat-games), %.2f cancelled per game (%.0f%% of"
                        + " plays), %.2f Sabotage plays per game%n",
                seats == 0 ? 0 : (double) plays.size() / seats,
                plays.size(),
                seats,
                (double) cancelled / games,
                plays.isEmpty() ? 0 : 100.0 * cancelled / plays.size(),
                (double) sabotages / games);
        Map<String, Long> byCard = plays.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        SelfPlayGame.ActionCardUse::card, java.util.stream.Collectors.counting()));
        List<String> perCard = byCard.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(entry -> String.format(
                        "%s %.3f (%.2f per game; human %.2f)",
                        entry.getKey(),
                        seats == 0 ? 0 : (double) entry.getValue() / seats,
                        (double) entry.getValue() / games,
                        ActionCardPrior.firstPlayRateOfName(entry.getKey())))
                .toList();
        System.out.println("Action card plays per seat (per game; human first-play rate when drawn): "
                + String.join(", ", perCard));
        printActionCardDraws(outcomes);
        printActionCardRounds(outcomes);
        printOverrule(outcomes);
        if (!AiSettings.isActionCardAbTest()) return;
        printActionCardAb(outcomes);
    }

    // How often a seat plays a card it was dealt, to set beside how often human players play it when they draw it. A
    // card the AI plays in under half the human share of draws is flagged as a likely valuation bug. In an A/B run
    // only the seats with the new rules count. A "draw" is a seat holding a copy at any point of the game.
    private static void printActionCardDraws(List<SelfPlayGame.Outcome> outcomes) {
        Map<String, Long> draws = new java.util.HashMap<>();
        Map<String, Long> plays = new java.util.HashMap<>();
        Map<String, Boolean> hasRule = new java.util.HashMap<>();
        for (SelfPlayGame.Outcome outcome : outcomes) {
            java.util.Set<String> legacy = outcome.actionCards().legacySeats();
            for (String held : outcome.actionCards().heldCards()) {
                String faction = held.substring(0, held.indexOf('|'));
                if (legacy.contains(faction)) continue;
                String alias = held.substring(held.indexOf('|') + 1);
                String name = ActionCardPrior.nameOf(alias);
                draws.merge(name, 1L, Long::sum);
                hasRule.merge(name, AiActionCards.usable(alias), (known, now) -> known || now);
            }
            outcome.actionCards().plays().stream()
                    .filter(play -> !legacy.contains(play.faction()))
                    .forEach(play -> plays.merge(play.card(), 1L, Long::sum));
        }
        List<String> ruled = draws.keySet().stream()
                .filter(name -> hasRule.get(name))
                .sorted(Comparator.comparing((String name) -> draws.get(name))
                        .reversed()
                        .thenComparing(name -> name))
                .toList();
        List<String> flagged = new ArrayList<>();
        List<String> perDraw = new ArrayList<>();
        for (String name : ruled) {
            double rate = (double) plays.getOrDefault(name, 0L) / draws.get(name);
            double human = ActionCardPrior.firstPlayRateOfName(name);
            boolean below = human > 0 && rate < human / 2;
            perDraw.add(String.format(
                    "%s %d/%d = %.2f (human %.2f)%s",
                    name, plays.getOrDefault(name, 0L), draws.get(name), rate, human, below ? " FLAGGED" : ""));
            if (below) flagged.add(String.format("%s %.2f vs %.2f", name, rate, human));
        }
        System.out.println("Action card plays per draw (plays/draws, human first-play rate when drawn): "
                + String.join(", ", perDraw));
        System.out.println("Action cards played in under half the human rate: "
                + (flagged.isEmpty() ? "none" : String.join(", ", flagged)));
        String unplayed = draws.keySet().stream()
                .filter(name -> !hasRule.get(name))
                .sorted(Comparator.comparing((String name) -> draws.get(name))
                        .reversed()
                        .thenComparing(name -> name))
                .limit(15)
                .map(name -> name + " " + draws.get(name))
                .collect(java.util.stream.Collectors.joining(", "));
        System.out.println("Action cards drawn that no rule plays (draws): " + unplayed);
    }

    // The rounds the action cards were played in: the whole batch by round, then each card's average round.
    private static void printActionCardRounds(List<SelfPlayGame.Outcome> outcomes) {
        List<SelfPlayGame.ActionCardUse> plays = outcomes.stream()
                .flatMap(outcome -> outcome.actionCards().plays().stream())
                .toList();
        Map<Integer, Long> byRound = plays.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        SelfPlayGame.ActionCardUse::round,
                        java.util.TreeMap::new,
                        java.util.stream.Collectors.counting()));
        System.out.println("Action card plays by round, whole batch: "
                + byRound.entrySet().stream()
                        .map(entry -> "r" + entry.getKey() + " " + entry.getValue())
                        .collect(java.util.stream.Collectors.joining(", ")));
        Map<String, java.util.DoubleSummaryStatistics> byCard = plays.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        SelfPlayGame.ActionCardUse::card,
                        java.util.TreeMap::new,
                        java.util.stream.Collectors.summarizingDouble(SelfPlayGame.ActionCardUse::round)));
        System.out.println("Action card average round of play: "
                + byCard.entrySet().stream()
                        .map(entry -> String.format(
                                "%s r%.1f (%d)",
                                entry.getKey(),
                                entry.getValue().getAverage(),
                                entry.getValue().getCount()))
                        .collect(java.util.stream.Collectors.joining(", ")));
    }

    // Overrule over the batch: how many were played and cancelled, in which rounds, on which strategy card, how many of
    // them raised the seat's victory points before its turn ended, and how many seats still held the card at the end.
    private static void printOverrule(List<SelfPlayGame.Outcome> outcomes) {
        List<SelfPlayGame.ActionCardUse> plays = outcomes.stream()
                .flatMap(outcome -> outcome.actionCards().plays().stream())
                .filter(play -> "Overrule".equals(play.card()))
                .toList();
        List<SelfPlayGame.OverruleUse> picks = outcomes.stream()
                .flatMap(outcome -> outcome.actionCards().overrules().stream())
                .toList();
        long held = outcomes.stream()
                .flatMap(outcome -> outcome.actionCards().heldAtEnd().stream())
                .filter(card -> card.endsWith("|overrule"))
                .count();
        long cancelled =
                plays.stream().filter(SelfPlayGame.ActionCardUse::canceled).count();
        System.out.printf(
                "Overrule: %d plays (%d cancelled), %d strategy cards picked, %d picks raised the seat's points that"
                        + " turn, %d seats ended the game still holding it, over %d seats%n",
                plays.size(),
                cancelled,
                picks.size(),
                picks.stream().filter(SelfPlayGame.OverruleUse::scored).count(),
                held,
                outcomes.stream().mapToInt(SelfPlayGame.Outcome::seats).sum());
        System.out.println("  plays by round: "
                + plays.stream()
                        .collect(java.util.stream.Collectors.groupingBy(
                                SelfPlayGame.ActionCardUse::round,
                                java.util.TreeMap::new,
                                java.util.stream.Collectors.counting())));
        System.out.println("  picks by target (picked/scored): "
                + picks.stream()
                        .collect(java.util.stream.Collectors.groupingBy(
                                SelfPlayGame.OverruleUse::strategyCard,
                                java.util.TreeMap::new,
                                java.util.stream.Collectors.collectingAndThen(
                                        java.util.stream.Collectors.toList(),
                                        list -> list.size() + "/"
                                                + list.stream()
                                                        .filter(SelfPlayGame.OverruleUse::scored)
                                                        .count()))));
        for (int game = 0; game < outcomes.size(); game++) {
            int number = game + 1;
            outcomes.get(game)
                    .actionCards()
                    .overrules()
                    .forEach(pick -> System.out.println("  game " + number + ": "
                            + pick.faction() + " overruled " + pick.strategyCard() + " in round " + pick.round()
                            + (pick.scored() ? " and scored" : " without scoring")));
        }
    }

    private static void printActionCardAb(List<SelfPlayGame.Outcome> outcomes) {
        AbGroup added = new AbGroup();
        AbGroup legacy = new AbGroup();
        for (SelfPlayGame.Outcome outcome : outcomes) {
            int best = outcome.victoryPoints().values().stream()
                    .mapToInt(Integer::intValue)
                    .max()
                    .orElse(0);
            for (Map.Entry<String, Integer> seat : outcome.victoryPoints().entrySet()) {
                boolean isLegacy = outcome.actionCards().legacySeats().contains(seat.getKey());
                AbGroup group = isLegacy ? legacy : added;
                long plays = outcome.actionCards().plays().stream()
                        .filter(play -> seat.getKey().equals(play.faction()))
                        .count();
                group.add(seat.getValue(), plays, seat.getValue() == best);
            }
        }
        System.out.println("Action card A/B: new seats " + added.describe() + ", legacy seats " + legacy.describe());
    }

    // Points, plays and finishing at the top of the table (ties count for every seat that tied) for one side of an A/B.
    private static final class AbGroup {
        private int seats;
        private int points;
        private long plays;
        private int tops;

        void add(int seatPoints, long seatPlays, boolean top) {
            seats++;
            points += seatPoints;
            plays += seatPlays;
            if (top) tops++;
        }

        String describe() {
            return String.format(
                    "average %.2f final victory points, %.2f action cards played per game, %d top finishes, over %d"
                            + " seats",
                    seats == 0 ? 0 : (double) points / seats, seats == 0 ? 0 : (double) plays / seats, tops, seats);
        }
    }

    private static String perRoundDecimal(List<List<Double>> perGame, int firstRound) {
        int rounds = perGame.stream().mapToInt(List::size).max().orElse(0);
        List<String> averages = new ArrayList<>();
        for (int round = 0; round < rounds; round++) {
            int index = round;
            List<Double> values = perGame.stream()
                    .filter(game -> game.size() > index)
                    .map(game -> game.get(index))
                    .toList();
            double average =
                    values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            averages.add(String.format("r%d %.2f (%d)", round + firstRound, average, values.size()));
        }
        return String.join(", ", averages);
    }

    // Averages the n-th value over the games that have one, so later rounds only count the games still running.
    private static String perRound(List<List<Integer>> perGame) {
        int rounds = perGame.stream().mapToInt(List::size).max().orElse(0);
        List<String> averages = new ArrayList<>();
        for (int round = 0; round < rounds; round++) {
            int index = round;
            List<Integer> values = perGame.stream()
                    .filter(values1 -> values1.size() > index)
                    .map(values1 -> values1.get(index))
                    .toList();
            double average =
                    values.stream().mapToInt(Integer::intValue).average().orElse(0);
            averages.add(String.format("r%d %.1f (%d)", round + 1, average, values.size()));
        }
        return String.join(", ", averages);
    }

    // -Dai.selfplay.factions=sol,jolnar,xxcha,yin,nekro,sardakk seats those factions instead of the six built-out
    // default factions (AiSettings.DEFAULT_FACTIONS).
    private static List<String> factions() {
        String chosen = System.getProperty("ai.selfplay.factions", "");
        if (chosen.isBlank()) return FACTIONS;
        return List.of(chosen.split(",")).stream()
                .map(String::trim)
                .filter(faction -> !faction.isEmpty())
                .toList();
    }

    private static final boolean SEEDED_BATCH = System.getProperty("ai.selfplay.seed") != null;
    private static final long SEEDED_CLOCK_START = 1_790_000_000_000L;

    // Every game gets its own thread, environment and fake Discord. Their virtual clocks start a millisecond apart and
    // only ever move in whole steps, so no two games can stamp the same message id. With -Dai.selfplay.seed the clock
    // start and game names are fixed too, since message ids and names feed the AI: the same seed then replays the
    // batch.
    private static List<SelfPlayGame.Outcome> playAll(int games, int threads) throws Exception {
        long clockStart = SEEDED_BATCH ? SEEDED_CLOCK_START : System.currentTimeMillis();
        if (threads == 1) {
            List<SelfPlayGame.Outcome> outcomes = new ArrayList<>();
            for (int i = 0; i < games; i++) outcomes.add(playFullGame(clockStart + i, i, games));
            return outcomes;
        }
        java.util.concurrent.atomic.AtomicInteger names = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(
                threads, task -> new Thread(task, "ai-self-play-" + names.incrementAndGet()));
        try {
            List<java.util.concurrent.Future<SelfPlayGame.Outcome>> futures = new ArrayList<>();
            for (int i = 0; i < games; i++) {
                int slot = i;
                futures.add(pool.submit(() -> playFullGame(clockStart + slot, slot, games)));
            }
            List<SelfPlayGame.Outcome> outcomes = new ArrayList<>();
            for (java.util.concurrent.Future<SelfPlayGame.Outcome> future : futures) outcomes.add(future.get());
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    // CPU time of the game's own thread next to its wall time shows how much of a game is computing and how much is
    // waiting, which is what decides how many games are worth running at once.
    private static SelfPlayGame.Outcome playFullGame(long clockStart, int slot, int games) throws Exception {
        java.lang.management.ThreadMXBean threads = java.lang.management.ManagementFactory.getThreadMXBean();
        long cpuStart = threads.getCurrentThreadCpuTime();
        SelfPlayGame.Outcome outcome = play(game -> false, 14 * VIRTUAL_DAY, 100_000, clockStart, slot);
        long cpuSeconds = (threads.getCurrentThreadCpuTime() - cpuStart) / 1_000_000_000L;
        System.out.println("Game " + (slot + 1) + "/" + games + " finished: " + outcome.ending() + " in round "
                + outcome.round() + ", victory points " + outcome.victoryPoints() + ", wall time "
                + outcome.wallSeconds() + "s, game thread CPU " + cpuSeconds + "s");
        return outcome;
    }

    private static SelfPlayGame.Outcome play(Predicate<Game> stopWhen, long maxVirtualMillis, int maxTicks)
            throws Exception {
        return play(stopWhen, maxVirtualMillis, maxTicks, System.currentTimeMillis(), 0);
    }

    private static SelfPlayGame.Outcome play(
            Predicate<Game> stopWhen, long maxVirtualMillis, int maxTicks, long clockStart, int slot) throws Exception {
        try (SelfPlayEnvironment env = new SelfPlayEnvironment(clockStart)) {
            String name = SEEDED_BATCH
                    ? "aiseed" + Math.floorMod(SelfPlayGame.BASE_SEED + slot, 1_000_000L)
                    : "aiselfplay" + ThreadLocalRandom.current().nextInt(100_000, 1_000_000);
            SelfPlayGame game = new SelfPlayGame(env, name, slot);
            try {
                game.setUp(factions());
                SelfPlayGame.Outcome outcome = game.run(stopWhen, maxVirtualMillis, maxTicks);
                System.out.println(outcome.report());
                // Every channel and thread in this game's fake Discord must be its own; anything else means a call was
                // routed to the wrong game while several games ran at once.
                List<String> foreign = env.discord.channels().stream()
                        .map(FakeChannel::name)
                        .filter(channel -> !channel.contains(game.name))
                        .toList();
                assertThat(foreign)
                        .as("channels from another game in " + game.name)
                        .isEmpty();
                return outcome;
            } finally {
                AiRuntime.forget(game.name);
                GameManager.delete(game.name);
            }
        }
    }
}
