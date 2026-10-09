package ti4.ai.runtime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.ai.AiSettings;
import ti4.ai.selfplay.SelfPlaySetup;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedGame;
import ti4.image.Mapper;
import ti4.model.PublicObjectiveModel;
import ti4.testUtils.discord.FakeChannel;
import ti4.testUtils.discord.FakeMessage;

/**
 * Sets up a game whose seats are all AI players and runs it on the virtual clock, ticking the AI runtime the way
 * its poll would, until a stop condition, the end of the game, or a stall.
 */
final class SelfPlayGame {

    static final long STEP_MILLIS = 10_000L;
    static final long STALL_MILLIS = 2 * 60 * 60 * 1000L;
    private static final String OWNER_ID = "300000000000000001";
    private static final String AGENDA_FLIP_STAMP = "lastAgendaReactTime";
    private static final long AGENDA_FLIP_AGE_MILLIS = 10 * 60 * 1000L;

    enum Ending {
        STOP_CONDITION,
        VICTORY_POINTS,
        OBJECTIVES_EXHAUSTED,
        STALLED,
        LOOPED,
        LIMIT,
        LOST_GAME
    }

    record Outcome(
            Ending ending,
            int round,
            String phase,
            int ticks,
            long virtualMillis,
            Map<String, Integer> victoryPoints,
            List<Map<String, Integer>> pointsAfterRound,
            Map<Integer, Map<String, Integer>> actionsByRound,
            List<Map<String, Double>> stateAtRoundStart,
            int seats,
            TradeSummary trade,
            String report) {

        // Per seat, for each round the game reached: how many presses of this kind the AI made.
        List<Double> perSeat(java.util.function.Predicate<String> kinds) {
            List<Double> values = new ArrayList<>();
            for (int r = 1; r <= round; r++) {
                int count = actionsByRound.getOrDefault(r, Map.of()).entrySet().stream()
                        .filter(entry -> kinds.test(entry.getKey()))
                        .mapToInt(Map.Entry::getValue)
                        .sum();
                values.add(seats == 0 ? 0 : (double) count / seats);
            }
            return values;
        }

        // The table's average of one seat value at the start of round 2, 3 and so on.
        List<Double> atRoundStart(String key) {
            return stateAtRoundStart.stream()
                    .map(state -> state.getOrDefault(key, 0.0))
                    .toList();
        }

        // Victory points each round added, for the whole table: the rounds that finished, then the round the game
        // ended in.
        List<Integer> tablePointsPerRound() {
            List<Integer> gained = new ArrayList<>();
            int before = 0;
            List<Map<String, Integer>> snapshots = new ArrayList<>(pointsAfterRound);
            snapshots.add(victoryPoints);
            for (Map<String, Integer> snapshot : snapshots) {
                int total =
                        snapshot.values().stream().mapToInt(Integer::intValue).sum();
                gained.add(total - before);
                before = total;
            }
            return gained;
        }

        List<Integer> leaderPointsAfterRound() {
            List<Map<String, Integer>> snapshots = new ArrayList<>(pointsAfterRound);
            snapshots.add(victoryPoints);
            return snapshots.stream()
                    .map(snapshot -> snapshot.values().stream()
                            .mapToInt(Integer::intValue)
                            .max()
                            .orElse(0))
                    .toList();
        }

        int leaderVictoryPoints() {
            return victoryPoints.values().stream()
                    .mapToInt(Integer::intValue)
                    .max()
                    .orElse(0);
        }

        int totalVictoryPoints() {
            return victoryPoints.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    // What trading did in one game: accepted offers, offers sent, trade goods gained across the table, Trade
    // settlements
    // sent and accepted, and the trade invariants that did not hold at the end.
    record TradeSummary(
            int trades,
            int offersSent,
            int tradeGoodsGained,
            int settlementsSent,
            int settlementsAccepted,
            List<String> violations) {}

    private static final String TRADE_GOODS = "TGs";
    private static final String COMMODITIES = "Comms";
    private static final int AI_DEBT_CAP = 4;
    private static final java.util.regex.Pattern ITEM =
            java.util.regex.Pattern.compile("^sending([^_]+)_receiving([^_]+)_([A-Za-z]+)_(\\d{1,6})$");

    private final SelfPlayEnvironment env;
    final String name;
    private TextChannel actions;
    private long issuedTurnStamp = -1;
    private final List<String> victoryPointsByRound = new ArrayList<>();
    private final List<Map<String, Integer>> pointsAfterRound = new ArrayList<>();
    private final List<Map<String, Double>> stateAtRoundStart = new ArrayList<>();
    private int recordedRound;

    SelfPlayGame(SelfPlayEnvironment env, String name) {
        this.env = env;
        this.name = name;
    }

    Game setUp(List<String> factions) throws Exception {
        actions = env.discord.createTextChannel(name + "-actions");
        TextChannel tableTalk = env.discord.createTextChannel(name + "-table-talk");
        ThreadChannel mapUpdates =
                actions.createThreadChannel(name + "-bot-map-updates").complete();

        // The same setup /ai watch uses, so a batch also exercises the production path; this harness drives its own
        // lane, so the runtime's registration is dropped again.
        Game game = SelfPlaySetup.setUp(
                name,
                owner(),
                new SelfPlaySetup.Channels(actions, tableTalk, mapUpdates),
                factions,
                false,
                setupEvent());
        AiRuntime.forget(name);
        env.game = () -> {
            ManagedGame managed = GameManager.getManagedGame(name);
            return managed == null ? null : managed.getGame();
        };
        game.setShowBanners(false);
        // -Dai.selfplay.give=summit,mining_initiative deals those action cards to every seat, to exercise a card flow.
        for (String alias : System.getProperty("ai.selfplay.give", "").split(",")) {
            if (alias.isBlank()) continue;
            for (Player seat : game.getRealPlayers()) {
                if (game.getActionCards().contains(alias.trim())) {
                    game.drawSpecificActionCard(alias.trim(), seat.getUserID());
                } else {
                    seat.setActionCard(alias.trim());
                }
            }
        }
        GameManager.save(game, "AI self-play setup");
        if (!GameManager.isValid(name) || GameManager.getManagedGame(name) == null) {
            throw new IllegalStateException(
                    "The game was not registered after setup: valid=" + GameManager.isValid(name));
        }
        return game;
    }

    Outcome run(Predicate<Game> stopWhen, long maxVirtualMillis, int maxTicks) {
        env.markStart();
        AiLane lane = new AiLane(name);
        long lastProgressAt = env.now();
        String lastProgress = "";
        int ticks = 0;
        while (true) {
            long now = env.now();
            ManagedGame managed = GameManager.getManagedGame(name);
            Game game = managed == null ? null : managed.getGame();
            if (game == null) return outcome(Ending.LOST_GAME, null, ticks);
            recordVictoryPoints(game);
            if (stopWhen.test(game)) return outcome(Ending.STOP_CONDITION, game, ticks);
            if (AiTickRunner.reachedVictoryPoints(game)) return outcome(Ending.VICTORY_POINTS, game, ticks);
            if (announcedGameOver()) return outcome(Ending.OBJECTIVES_EXHAUSTED, game, ticks);
            if (announcedLoop()) return outcome(Ending.LOOPED, game, ticks);
            if (env.elapsedMillis() > maxVirtualMillis || ticks >= maxTicks) return outcome(Ending.LIMIT, game, ticks);

            String progress = progressOf(game, managed);
            if (!progress.equals(lastProgress)) {
                lastProgress = progress;
                lastProgressAt = now;
            } else if (now - lastProgressAt > STALL_MILLIS) {
                return outcome(Ending.STALLED, game, ticks);
            }

            if (lane.observeModified(managed.getLastModifiedDate())) {
                lane.markDirty(now + AiSettings.DEBOUNCE.toMillis());
            }
            if (lane.watchdogDue(now, AiSettings.WATCHDOG_PERIOD.toMillis())) lane.markDirty(now);
            if (lane.isDue(now) && lane.tryStart()) {
                try {
                    AiTickRunner.tick(lane, now);
                } finally {
                    lane.finish();
                }
                ticks++;
                stampTurnStart(now);
                ageAgendaFlipGuard();
            }
            env.advance(STEP_MILLIS);
        }
    }

    // The AI posts this notice once every objective has been revealed and the referee will not end the game itself.
    private boolean announcedGameOver() {
        return recentNoticeStartsWith("🏁");
    }

    // A seat that keeps falling back on the same unsure choice stops and says so; nothing can move the game on.
    private boolean announcedLoop() {
        return recentNoticeStartsWith(ti4.ai.fallback.AiConfusionService.LOOP_NOTICE_MARK);
    }

    private boolean recentNoticeStartsWith(String mark) {
        List<FakeMessage> messages = env.discord.channel(actions.getIdLong()).liveMessages();
        for (int i = messages.size() - 1; i >= Math.max(0, messages.size() - 5); i--) {
            if (messages.get(i).content().startsWith(mark)) return true;
        }
        return false;
    }

    // Changes the game between ticks the way a test scenario needs, and saves it. A turn the change started counts as
    // started now on the virtual clock, like a turn a press started, so its messages belong to it.
    void force(Game game, java.util.function.Consumer<Game> change) {
        Date before = game.getLastActivePlayerChange();
        change.accept(game);
        Date after = game.getLastActivePlayerChange();
        if (after != null && !after.equals(before)) {
            game.setLastActivePlayerChange(new Date(env.now()));
            issuedTurnStamp = env.now();
        }
        GameManager.save(game, "AI self-play scenario");
    }

    // An interaction from the game's owner in the actions channel, for engine calls a scenario makes itself.
    GenericInteractionCreateEvent ownerEvent() {
        return setupEvent();
    }

    // The engine stamps a new turn with the wall clock; the AI compares it with message times from the virtual
    // clock, so every turn change is re-stamped with the virtual time of the tick that caused it.
    private void stampTurnStart(long tickTime) {
        ManagedGame managed = GameManager.getManagedGame(name);
        Game game = managed == null ? null : managed.getGame();
        if (game == null) return;
        Date changed = game.getLastActivePlayerChange();
        long stamp = changed == null ? -1 : changed.getTime();
        if (stamp == issuedTurnStamp) return;
        game.setLastActivePlayerChange(new Date(tickTime));
        issuedTurnStamp = tickTime;
        GameManager.save(game, "AI self-play clock");
    }

    // One line per round, taken when the next round starts, so the report shows how fast each seat scores.
    private void recordVictoryPoints(Game game) {
        if (game.getRound() <= recordedRound) return;
        if (recordedRound > 0) {
            victoryPointsByRound.add("after round " + recordedRound + ": " + victoryPoints(game));
            pointsAfterRound.add(victoryPoints(game));
            stateAtRoundStart.add(seatState(game));
        }
        recordedRound = game.getRound();
    }

    private static Map<String, Double> seatState(Game game) {
        List<Player> seats = game.getRealPlayers();
        Map<String, Double> state = new LinkedHashMap<>();
        state.put(
                "tactic",
                seats.stream().mapToInt(Player::getTacticalCC).average().orElse(0));
        state.put("fleet", seats.stream().mapToInt(Player::getFleetCC).average().orElse(0));
        state.put(
                "strategy",
                seats.stream().mapToInt(Player::getStrategicCC).average().orElse(0));
        state.put(
                "planets",
                seats.stream()
                        .mapToInt(seat -> seat.getPlanets().size())
                        .average()
                        .orElse(0));
        state.put(
                "techs",
                seats.stream()
                        .mapToInt(seat -> seat.getTechs().size())
                        .average()
                        .orElse(0));
        return state;
    }

    private static Map<Integer, Map<String, Integer>> copyOf(Map<Integer, Map<String, Integer>> counts) {
        synchronized (counts) {
            Map<Integer, Map<String, Integer>> copy = new java.util.TreeMap<>();
            counts.forEach((round, kinds) -> copy.put(round, Map.copyOf(kinds)));
            return copy;
        }
    }

    private static Map<String, Integer> victoryPoints(Game game) {
        Map<String, Integer> points = new LinkedHashMap<>();
        for (Player player : game.getRealPlayers()) points.put(player.getFaction(), player.getTotalVictoryPoints());
        return points;
    }

    private static List<String> scoreboard(Game game) {
        List<String> lines = new ArrayList<>();
        Map<String, List<String>> scored = game.getScoredPublicObjectives();
        game.getRevealedPublicObjectives().keySet().forEach(id -> {
            PublicObjectiveModel model = Mapper.getPublicObjective(id);
            String title = model == null ? id : model.getName() + " (" + model.getPoints() + ")";
            List<String> scorers = scored.getOrDefault(id, List.of()).stream()
                    .map(userId -> describe(game, userId))
                    .toList();
            lines.add(title + ": " + scorers);
        });
        for (Player player : game.getRealPlayers()) {
            lines.add(player.getFaction() + " secrets scored "
                    + player.getSecretsScored().keySet() + ", held "
                    + player.getSecretsUnscored().size() + ", techs "
                    + player.getTechs().size() + ", planets "
                    + player.getPlanets().size() + ", TG " + player.getTg() + ", action cards "
                    + player.getAcCount() + ", fragments "
                    + player.getFragments().size() + ", laws in play "
                    + game.getLaws().size());
        }
        // Action cards each seat played, with sabotaged ones marked, so a batch shows whether the AI uses its hand.
        for (Player player : game.getRealPlayers()) {
            String playerId = ti4.game.GameStats.getTrackedPlayerId(player);
            List<String> plays = game.getGameStats().getActionCardPlays().stream()
                    .filter(play -> playerId != null && playerId.equals(play.getPlayerId()))
                    .map(play -> play.getActionCard() + (play.isCanceled() ? " (sabotaged)" : ""))
                    .toList();
            lines.add(player.getFaction() + " action cards played " + plays);
        }
        // Promissory notes: every seat starts with its own faction note and the five generic ones; notes change hands
        // when one is played or traded.
        for (Player player : game.getRealPlayers()) {
            List<String> own = player.getPromissoryNotes().keySet().stream()
                    .filter(player::ownsPromissoryNote)
                    .sorted()
                    .toList();
            List<String> received = player.getPromissoryNotes().keySet().stream()
                    .filter(note -> !player.ownsPromissoryNote(note))
                    .sorted()
                    .toList();
            lines.add(player.getFaction() + " promissory notes: own in hand " + own + ", received " + received
                    + ", in play area " + player.getPromissoryNotesInPlayArea());
        }
        return lines;
    }

    // The engine refuses to flip an agenda within 6 wall-clock seconds of the previous flip. The referee waits 20
    // seconds of game time, which the virtual clock covers in a few real milliseconds, so the stamp is aged here to
    // keep the guard's meaning: in game time, the previous flip was long ago.
    private void ageAgendaFlipGuard() {
        ManagedGame managed = GameManager.getManagedGame(name);
        Game game = managed == null ? null : managed.getGame();
        if (game == null) return;
        String flippedAt = game.getStoredValue(AGENDA_FLIP_STAMP);
        if (flippedAt.isEmpty() || !flippedAt.chars().allMatch(Character::isDigit)) return;
        long aged = System.currentTimeMillis() - AGENDA_FLIP_AGE_MILLIS;
        if (Long.parseLong(flippedAt) <= aged) return;
        game.setStoredValue(AGENDA_FLIP_STAMP, String.valueOf(aged));
        GameManager.save(game, "AI self-play clock");
    }

    private static String progressOf(Game game, ManagedGame managed) {
        return managed.getLastModifiedDate() + "|" + game.getRound() + "|" + game.getPhaseOfGame() + "|"
                + game.getActivePlayerID();
    }

    private Outcome outcome(Ending ending, Game game, int ticks) {
        return new Outcome(
                ending,
                game == null ? 0 : game.getRound(),
                game == null ? "" : game.getPhaseOfGame(),
                ticks,
                env.elapsedMillis(),
                game == null ? Map.of() : victoryPoints(game),
                List.copyOf(pointsAfterRound),
                copyOf(env.actionsByRound),
                List.copyOf(stateAtRoundStart),
                game == null ? 0 : game.getRealPlayers().size(),
                tradeSummary(ending, game),
                report(ending, game, ticks));
    }

    private List<SelfPlayEnvironment.TradeRecord> ledger() {
        synchronized (env.tradeLedger) {
            return List.copyOf(env.tradeLedger);
        }
    }

    private TradeSummary tradeSummary(Ending ending, Game game) {
        List<SelfPlayEnvironment.TradeRecord> ledger = ledger();
        int gained = game == null
                ? 0
                : game.getRealPlayers().stream()
                        .mapToInt(player -> flow(ledger, player.getFaction())[0] - flow(ledger, player.getFaction())[1])
                        .sum();
        return new TradeSummary(
                ledger.size(),
                env.pressedHandlers.getOrDefault("sendOffer", 0),
                gained,
                env.settlementsSent.values().stream()
                        .mapToInt(Integer::intValue)
                        .sum(),
                env.settlementsAccepted.values().stream()
                        .mapToInt(Integer::intValue)
                        .sum(),
                invariantViolations(ending, game));
    }

    // Trade goods received, trade goods given and commodities given by one faction through accepted offers; commodities
    // a seat receives arrive as trade goods.
    private static int[] flow(List<SelfPlayEnvironment.TradeRecord> ledger, String faction) {
        int[] flow = new int[3];
        for (SelfPlayEnvironment.TradeRecord trade : ledger) {
            for (String item : trade.items()) {
                java.util.regex.Matcher matcher = ITEM.matcher(item);
                if (!matcher.matches()) continue;
                String type = matcher.group(3);
                int amount = Integer.parseInt(matcher.group(4));
                boolean goods = TRADE_GOODS.equals(type) || COMMODITIES.equals(type);
                if (goods && faction.equals(matcher.group(2))) flow[0] += amount;
                if (TRADE_GOODS.equals(type) && faction.equals(matcher.group(1))) flow[1] += amount;
                if (COMMODITIES.equals(type) && faction.equals(matcher.group(1))) flow[2] += amount;
            }
        }
        return flow;
    }

    // Per seat: commodities, debt both ways, offers by button family, what moved through accepted offers and the Trade
    // settlements it sent as holder; then the latest accepted offers.
    private List<String> tradeLines(Game game) {
        List<SelfPlayEnvironment.TradeRecord> ledger = ledger();
        List<String> lines = new ArrayList<>();
        for (Player player : game.getRealPlayers()) {
            String faction = player.getFaction();
            Map<String, Integer> owes = new java.util.TreeMap<>();
            Map<String, Integer> owed = new java.util.TreeMap<>();
            for (Player other : game.getRealPlayers()) {
                if (other == player) continue;
                if (other.getDebtTokenCount(player.getColor()) > 0) {
                    owes.put(other.getColor(), other.getDebtTokenCount(player.getColor()));
                }
                if (player.getDebtTokenCount(other.getColor()) > 0) {
                    owed.put(other.getColor(), player.getDebtTokenCount(other.getColor()));
                }
            }
            Map<String, Integer> presses;
            synchronized (env.pressesBySeat) {
                presses = Map.copyOf(env.pressesBySeat.getOrDefault(faction, Map.of()));
            }
            long acceptedOffers = ledger.stream()
                    .filter(trade -> trade.from().equals(faction))
                    .count();
            int[] flow = flow(ledger, faction);
            lines.add(faction + " commodities " + player.getCommodities() + "/" + player.getCommoditiesTotal()
                    + ", owes " + owes + ", is owed " + owed
                    + ", offers sent " + presses.getOrDefault("sendOffer", 0) + " (" + acceptedOffers + " accepted)"
                    + ", accepted " + presses.getOrDefault("acceptOffer", 0)
                    + ", rescinded " + presses.getOrDefault("rescindOffer", 0)
                    + ", countered or reset " + presses.getOrDefault("resetOffer", 0)
                    + ", trade goods in " + flow[0] + " out " + flow[1] + ", commodities out " + flow[2]
                    + ", Trade settlements accepted " + env.settlementsAccepted.getOrDefault(faction, 0) + "/"
                    + env.settlementsSent.getOrDefault(faction, 0));
        }
        ledger.stream()
                .skip(Math.max(0, ledger.size() - 15))
                .forEach(trade -> lines.add(
                        "round " + trade.round() + ": " + trade.from() + " -> " + trade.to() + " " + trade.items()));
        return lines;
    }

    private List<String> invariantViolations(Ending ending, Game game) {
        List<String> violations = new ArrayList<>();
        if (game == null) return violations;
        for (Player player : game.getRealPlayers()) {
            if (player.getTg() < 0 || player.getCommodities() < 0) {
                violations.add("negative stock: " + player.getFaction() + " TG " + player.getTg() + ", commodities "
                        + player.getCommodities());
            }
            if (!player.getTransactionItems().isEmpty()) {
                violations.add("transaction items left: " + player.getFaction() + " " + player.getTransactionItems());
            }
            if (!ti4.ai.AiSeats.isAiSeat(player)) continue;
            for (Player creditor : game.getRealPlayers()) {
                if (creditor.getDebtTokenCount(player.getColor()) > AI_DEBT_CAP) {
                    violations.add("debt over the cap: " + player.getFaction() + " owes " + creditor.getFaction() + " "
                            + creditor.getDebtTokenCount(player.getColor()));
                }
            }
        }
        boolean stuck = ending == Ending.LOOPED || ending == Ending.STALLED;
        if (stuck && env.lastDecisionReason.startsWith(SelfPlayEnvironment.TRADE_PREFIX)) {
            violations.add("ended " + ending + " on a trade decision: " + env.lastDecisionReason);
        }
        return violations;
    }

    private List<String> invariantLines(Ending ending, Game game) {
        List<String> violations = invariantViolations(ending, game);
        if (violations.isEmpty()) return List.of("TRADE_INVARIANT ok");
        return violations.stream()
                .map(violation -> "TRADE_INVARIANT violated: " + violation)
                .toList();
    }

    String report(Ending ending, Game game, int ticks) {
        StringBuilder report = new StringBuilder();
        report.append("=== AI self-play ")
                .append(ending)
                .append(" after ")
                .append(ticks)
                .append(" ticks, ")
                .append(env.elapsedMillis() / 60_000)
                .append(" virtual minutes ===\n");
        if (game == null) {
            report.append("Registered: ")
                    .append(GameManager.isValid(name))
                    .append(", managed: ")
                    .append(GameManager.getManagedGame(name) != null)
                    .append(", file: ")
                    .append(ti4.helpers.Storage.getGameFile(name + ".txt"))
                    .append(" exists ")
                    .append(ti4.helpers.Storage.getGameFile(name + ".txt").exists())
                    .append('\n');
            appendTail(report, "Bot warnings and errors", interesting(env.botProblems), 40);
            return report.toString();
        }
        report.append("Round ")
                .append(game.getRound())
                .append(", phase ")
                .append(game.getPhaseOfGame())
                .append(", active ")
                .append(describe(game, game.getActivePlayerID()))
                .append('\n');
        for (Player player : game.getRealPlayers()) {
            report.append("  ")
                    .append(player.getFaction())
                    .append(" (")
                    .append(player.getColor())
                    .append(") VP ")
                    .append(player.getTotalVictoryPoints())
                    .append(", SCs ")
                    .append(player.getSCs())
                    .append(", tactics ")
                    .append(player.getTacticalCC())
                    .append(", passed ")
                    .append(player.isPassed())
                    .append('\n');
        }
        appendTail(report, "Victory points by round", victoryPointsByRound, 20);
        appendTail(report, "Scoreboard", scoreboard(game), 40);
        appendTail(report, "Trading", tradeLines(game), 30);
        appendTail(report, "Trade invariants", invariantLines(ending, game), 20);
        report.append("Press outcomes: ").append(env.pressOutcomes).append('\n');
        report.append("Pressed handlers: ").append(env.pressedHandlers).append('\n');
        appendTail(
                report,
                "Decisions by reason",
                env.decisionReasons.entrySet().stream()
                        .map(entry -> entry.getValue() + " x " + entry.getKey())
                        .toList(),
                Integer.MAX_VALUE);
        report.append("Missing Spring beans: ").append(env.missingBeans).append('\n');
        appendTail(report, "Circuit breaker", env.circuitBreakerReasons, 10);
        appendTail(report, "Bot warnings and errors", interesting(env.botProblems), 25);
        appendTail(report, "Fake Discord action failures", env.discord.actionFailures(), 15);
        appendTail(report, "Fake Discord callback failures", env.discord.callbackFailures(), 15);
        appendTail(report, "Unsupported JDA calls (distinct)", distinct(env.discord.unsupportedCalls()), 30);
        if (!env.watched.isEmpty()) appendTail(report, "Watched trace", env.watched, 400);
        if (SelfPlayEnvironment.WATCH != null) {
            appendTail(
                    report,
                    "Watched messages",
                    env.discord.allMessages().stream()
                            .map(FakeMessage::toString)
                            .filter(text ->
                                    SelfPlayEnvironment.WATCH.matcher(text).find())
                            .toList(),
                    200);
        }
        appendTail(report, "AI trace", env.traceLines(), 80);
        FakeChannel main = env.discord.channel(actions.getIdLong());
        appendTail(
                report,
                "Main channel",
                main.liveMessages().stream().map(FakeMessage::toString).toList(),
                25);
        List<FakeChannel> combats = main.threads().stream()
                .filter(thread -> thread.name().contains("-vs-"))
                .sorted(java.util.Comparator.comparingLong(FakeChannel::latestMessageId)
                        .reversed())
                .limit(3)
                .toList();
        for (FakeChannel thread : combats) {
            List<FakeMessage> messages = thread.liveMessages();
            if (messages.isEmpty()) continue;
            appendTail(
                    report,
                    "Combat thread " + thread.name(),
                    messages.stream().map(FakeMessage::toString).toList(),
                    20);
        }
        for (Player player : game.getRealPlayers()) {
            FakeChannel thread = env.discord.channel(player.getCardsInfoThreadID());
            if (thread == null) continue;
            appendTail(
                    report,
                    player.getFaction() + " private thread",
                    thread.liveMessages().stream()
                            .filter(message -> !message.buttons().isEmpty())
                            .map(FakeMessage::toString)
                            .toList(),
                    5);
        }
        return report.toString();
    }

    private static String describe(Game game, String userId) {
        Player player = userId == null ? null : game.getPlayer(userId);
        return player == null ? "nobody" : player.getFaction();
    }

    private static List<String> interesting(List<String> problems) {
        return problems.stream()
                .filter(problem -> !problem.contains("Bad Buttons detected and sanitized"))
                .toList();
    }

    private static List<String> distinct(List<String> lines) {
        return lines.stream().distinct().toList();
    }

    private static void appendTail(StringBuilder report, String title, List<String> lines, int count) {
        report.append("--- ").append(title).append(" (").append(lines.size()).append(") ---\n");
        for (int i = Math.max(0, lines.size() - count); i < lines.size(); i++) {
            report.append("  ").append(lines.get(i)).append('\n');
        }
    }

    private Member owner() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(OWNER_ID);
        when(user.getIdLong()).thenReturn(Long.parseLong(OWNER_ID));
        when(user.getName()).thenReturn("owner");
        when(user.getEffectiveName()).thenReturn("Owner");
        when(user.getAsMention()).thenReturn("<@" + OWNER_ID + ">");
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(OWNER_ID);
        when(member.getIdLong()).thenReturn(Long.parseLong(OWNER_ID));
        when(member.getEffectiveName()).thenReturn("Owner");
        when(member.getUser()).thenReturn(user);
        when(member.getGuild()).thenReturn(env.discord.guild());
        return member;
    }

    private GenericInteractionCreateEvent setupEvent() {
        Member owner = owner();
        User ownerUser = owner.getUser();
        GenericInteractionCreateEvent event = mock(GenericInteractionCreateEvent.class);
        when(event.getMessageChannel()).thenReturn((MessageChannelUnion) actions);
        when(event.getChannel()).thenReturn((net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion) actions);
        when(event.getUser()).thenReturn(ownerUser);
        when(event.getMember()).thenReturn(owner);
        when(event.getGuild()).thenReturn(env.discord.guild());
        when(event.getJDA()).thenReturn(env.discord.jda());
        return event;
    }
}
