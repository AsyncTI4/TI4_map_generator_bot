package ti4.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import ti4.ai.AiSettings;
import ti4.game.Game;
import ti4.game.persistence.GameManager;
import ti4.testUtils.BaseTi4Test;

// Six AI seats play against each other in-process: a fake Discord stands in for the server, the AI runtime is
// ticked on a virtual clock, and every press runs the bot's real button handlers. A run ends at its stop
// condition, at the end of the game, or when nothing has changed for two virtual hours; the outcome carries a
// report of the game state, the AI's recent decisions and presses, and what the bot logged.
class AiSelfPlayTest extends BaseTi4Test {

    private static final List<String> FACTIONS = AiSettings.SUPPORTED_FACTIONS.subList(0, 6);
    private static final long VIRTUAL_DAY = 24 * 60 * 60 * 1000L;
    private static final Set<SelfPlayGame.Ending> FINISHED =
            EnumSet.of(SelfPlayGame.Ending.VICTORY_POINTS, SelfPlayGame.Ending.OBJECTIVES_EXHAUSTED);

    @Test
    void sixAiSeatsPlayTheirFirstRound() throws Exception {
        SelfPlayGame.Outcome outcome = play(game -> game.getRound() >= 2, VIRTUAL_DAY, 5000);

        assertThat(outcome.ending()).as(outcome.report()).isEqualTo(SelfPlayGame.Ending.STOP_CONDITION);
    }

    // Whole games take most of a minute each and dice and decks are random, so they only run when asked for:
    // -Dai.selfplay.full=true, optionally with -Dai.selfplay.games=N to play several and report every outcome.
    @Test
    @EnabledIfSystemProperty(named = "ai.selfplay.full", matches = "true")
    void sixAiSeatsPlayFullGames() throws Exception {
        int games = Integer.getInteger("ai.selfplay.games", 1);
        List<SelfPlayGame.Outcome> unfinished = new ArrayList<>();
        List<String> summary = new ArrayList<>();
        int leaderPoints = 0;
        int finalRounds = 0;
        List<SelfPlayGame.Outcome> outcomes = new ArrayList<>();
        int totalPoints = 0;
        for (int i = 0; i < games; i++) {
            SelfPlayGame.Outcome outcome = play(game -> false, 14 * VIRTUAL_DAY, 100_000);
            String line = "Game " + (i + 1) + "/" + games + ": " + outcome.ending() + " in round " + outcome.round()
                    + ", victory points " + outcome.victoryPoints();
            System.out.println(line);
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

    // -Dai.selfplay.factions=sol,jolnar,xxcha,yin,nekro,sardakk seats those factions instead of the first six supported
    // ones.
    private static List<String> factions() {
        String chosen = System.getProperty("ai.selfplay.factions", "");
        if (chosen.isBlank()) return FACTIONS;
        return List.of(chosen.split(",")).stream()
                .map(String::trim)
                .filter(faction -> !faction.isEmpty())
                .toList();
    }

    private static SelfPlayGame.Outcome play(Predicate<Game> stopWhen, long maxVirtualMillis, int maxTicks)
            throws Exception {
        try (SelfPlayEnvironment env = new SelfPlayEnvironment(System.currentTimeMillis())) {
            SelfPlayGame game = new SelfPlayGame(
                    env, "aiselfplay" + ThreadLocalRandom.current().nextInt(100_000, 1_000_000));
            try {
                game.setUp(factions());
                SelfPlayGame.Outcome outcome = game.run(stopWhen, maxVirtualMillis, maxTicks);
                System.out.println(outcome.report());
                return outcome;
            } finally {
                AiRuntime.forget(game.name);
                GameManager.delete(game.name);
            }
        }
    }
}
