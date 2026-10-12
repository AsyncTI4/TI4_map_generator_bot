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

    private static final List<String> FACTIONS = AiSettings.SUPPORTED_FACTIONS.subList(0, 6);
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
        printActionCards(outcomes);
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

    // Every game gets its own thread, environment and fake Discord. Their virtual clocks start a millisecond apart and
    // only ever move in whole steps, so no two games can stamp the same message id.
    private static List<SelfPlayGame.Outcome> playAll(int games, int threads) throws Exception {
        long clockStart = System.currentTimeMillis();
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
            SelfPlayGame game = new SelfPlayGame(
                    env, "aiselfplay" + ThreadLocalRandom.current().nextInt(100_000, 1_000_000), slot);
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
