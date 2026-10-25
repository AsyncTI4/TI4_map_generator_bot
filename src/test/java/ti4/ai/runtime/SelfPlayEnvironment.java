package ti4.ai.runtime;

import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.withSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.mockito.MockSettings;
import org.mockito.MockedStatic;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import ti4.ai.AiSettings;
import ti4.ai.actuation.AiActuator;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.FactionBrain;
import ti4.ai.brain.FactionBrainRegistry;
import ti4.ai.brain.StrategyCard;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.Territory;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalPlanner;
import ti4.ai.tactical.TacticalRules;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.service.CombatReplayService;
import ti4.executors.CircuitBreaker;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Storage;
import ti4.image.map.MapRenderPipeline;
import ti4.logging.BotLogger;
import ti4.model.metadata.AutoPingMetadataManager;
import ti4.model.metadata.TechSummariesMetadataManager;
import ti4.service.persistence.GameDatabaseSyncPipeline;
import ti4.settings.users.UserSettings;
import ti4.settings.users.UserSettingsManager;
import ti4.spring.context.SpringContext;
import ti4.spring.service.deploy.ActiveLeaseService;
import ti4.spring.service.gameevent.GameEventService;
import ti4.spring.service.gamemessage.GameMessageService;
import ti4.spring.service.statistics.overrule.OverruleStatsService;
import ti4.spring.service.title.PlayerTitleService;
import ti4.testUtils.discord.DiscordRouter;
import ti4.testUtils.discord.FakeDiscord;
import ti4.testUtils.discord.InMemoryGameMessages;

/**
 * Everything an in-process game needs around the bot: a fake Discord on a virtual clock, Spring beans for the
 * services the button path reaches, storage in a temporary folder, and no map rendering, database sync or
 * metadata files. All static stubs live on the thread that creates the environment, which must also run the game.
 * Work the bot hands to its own threads runs without them, so beans that would start such work (for example the
 * website notifier, whose pipeline reloads the game from the real storage path and drops it when it is missing)
 * are deliberately left unavailable.
 * It also records what happened (bot errors, AI decisions and presses) for stall reports.
 */
final class SelfPlayEnvironment implements AutoCloseable {

    private static final int TRACE_SIZE = 400;

    final AtomicLong clock;
    final FakeDiscord discord;
    final InMemoryGameMessages gameMessages = new InMemoryGameMessages();
    final Path storage;
    final List<String> botProblems = new ArrayList<>();
    final List<String> fallbacks = new ArrayList<>();
    final List<String> circuitBreakerReasons = new ArrayList<>();
    final Set<String> missingBeans = new LinkedHashSet<>();
    final Deque<String> trace = new ArrayDeque<>();
    // Trace lines matching -Dai.selfplay.watch=<regex> are kept for the whole game, to follow one flow end to end.
    static final java.util.regex.Pattern WATCH = java.util.Optional.ofNullable(System.getProperty("ai.selfplay.watch"))
            .filter(regex -> !regex.isBlank())
            .map(java.util.regex.Pattern::compile)
            .orElse(null);
    final List<String> watched = new ArrayList<>();
    final Map<String, Integer> pressOutcomes = new HashMap<>();
    final Map<String, Integer> pressedHandlers = new java.util.TreeMap<>();
    final Map<String, Integer> decisionReasons = new java.util.TreeMap<>();
    final Map<Integer, Map<String, Integer>> actionsByRound = new java.util.TreeMap<>();
    // The round in which each action secret was first sent to be scored, by "faction|secret".
    final Map<String, Integer> actionSecretRounds = new java.util.concurrent.ConcurrentHashMap<>();
    final MecatolTracker mecatol = new MecatolTracker();
    final ExpeditionTracker expeditions = new ExpeditionTracker();
    final PromissoryTracker promissory = new PromissoryTracker();
    // Trading: every accepted offer (read from the offerer's items just before the Accept press), the presses each seat
    // made by handler family, and the Trade settlements each holder sent and had accepted.
    final List<TradeRecord> tradeLedger = new ArrayList<>();
    final Map<String, Map<String, Integer>> pressesBySeat = new java.util.TreeMap<>();
    final Map<String, Integer> settlementsSent = new java.util.TreeMap<>();
    final Map<String, Integer> settlementsAccepted = new java.util.TreeMap<>();
    private final Map<String, String> lastOfferLabel = new HashMap<>();
    // Every strategy card an Overrule play picked, in order; the game closes each one when the seat's turn is over.
    final List<OverrulePick> overrulePicks = new ArrayList<>();
    volatile String lastDecisionReason = "";
    Supplier<Game> game = () -> null;
    // Sees every decision a seat makes, with the board it made it on; a harness sets it to follow what seats spend.
    volatile java.util.function.BiConsumer<AiTurnContext, AiDecision> decisionListener = (context, decision) -> {};

    record TradeRecord(int round, String from, String to, List<String> items, String label) {}

    // The strategy card (by initiative) a seat overruled, the round it did so in, and the seat's victory points when it
    // did and when its turn ended (-1 while the turn is still running).
    static final class OverrulePick {
        final String faction;
        final int round;
        final int initiative;
        final int pointsBefore;
        int pointsAfter = -1;

        OverrulePick(String faction, int round, int initiative, int pointsBefore) {
            this.faction = faction;
            this.round = round;
            this.initiative = initiative;
            this.pointsBefore = pointsBefore;
        }
    }

    static final String SEND_PREFIX = "trade: send the ";
    static final String SETTLEMENT_LABEL = "Trade settlement";
    private static final String ACCEPT_FAMILY = "acceptOffer";
    private final Map<String, UserSettings> userSettings = new HashMap<>();
    private final List<MockedStatic<?>> mocks = new ArrayList<>();

    SelfPlayEnvironment(long startMillis) throws IOException {
        clock = new AtomicLong(startMillis);
        discord = new FakeDiscord(clock::get);
        storage = Files.createTempDirectory("ai-self-play");
        try {
            install();
        } catch (RuntimeException e) {
            close();
            throw e;
        }
    }

    long now() {
        return clock.get();
    }

    void advance(long millis) {
        clock.addAndGet(millis);
    }

    private void install() {
        DiscordRouter.bind(discord);

        Map<Class<?>, Object> beans = new HashMap<>();
        beans.put(GameMessageService.class, gameMessages.service());
        beans.put(CombatContestSettings.class, mock(CombatContestSettings.class, quiet()));
        beans.put(CombatReplayService.class, mock(CombatReplayService.class, quiet()));
        beans.put(PlayerTitleService.class, mock(PlayerTitleService.class, quiet()));
        beans.put(GameEventService.class, mock(GameEventService.class, quiet(this::gameEvent)));
        beans.put(OverruleStatsService.class, mock(OverruleStatsService.class, quiet()));
        beans.put(ActiveLeaseService.class, mock(ActiveLeaseService.class, quiet(invocation -> {
            Class<?> type = invocation.getMethod().getReturnType();
            if (type == boolean.class)
                return !invocation.getMethod().getName().toLowerCase().contains("drain");
            return null;
        })));
        MockedStatic<SpringContext> spring = open(mockStatic(SpringContext.class, quiet()));
        spring.when(() -> SpringContext.getBean(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    Class<?> type = invocation.getArgument(0);
                    Object bean = beans.get(type);
                    if (bean != null) return bean;
                    missingBeans.add(type.getName());
                    throw new IllegalStateException("ApplicationContext not initialized");
                });

        open(mockStatic(
                Storage.class,
                quiet(invocation ->
                        "getStoragePath".equals(invocation.getMethod().getName())
                                ? storage.toString()
                                : invocation.callRealMethod())));
        Storage.init();

        open(mockStatic(MapRenderPipeline.class, quiet()));
        open(mockStatic(GameDatabaseSyncPipeline.class, quiet()));
        open(mockStatic(AutoPingMetadataManager.class, quiet()));
        open(mockStatic(TechSummariesMetadataManager.class, quiet()));
        open(mockStatic(UserSettingsManager.class, quiet(this::userSettings)));

        MockedStatic<AiRuntime> runtime = open(mockStatic(AiRuntime.class, quiet(CALLS_REAL_METHODS)));
        runtime.when(AiRuntime::mayMutate).thenReturn(true);
        runtime.when(AiRuntime::isProcessReady).thenReturn(true);
        MockedStatic<AiSettings> settings = open(mockStatic(AiSettings.class, quiet(CALLS_REAL_METHODS)));
        settings.when(AiSettings::isEnabled).thenReturn(true);
        settings.when(AiSettings::thinkingBudget).thenReturn(Duration.ZERO);

        open(mockStatic(CircuitBreaker.class, quiet(this::circuitBreaker)));
        open(mockStatic(BotLogger.class, quiet(this::botLogger)));
        open(mockStatic(AiActuator.class, quiet(this::actuator)));
        open(mockStatic(FactionBrainRegistry.class, quiet(this::brains)));
    }

    // Stub-only: a whole game makes millions of calls through these mocks, and a recording mock would keep every one
    // of them (with its arguments) until the game ends.
    private static MockSettings quiet() {
        return withSettings().stubOnly();
    }

    private static MockSettings quiet(Answer<?> answer) {
        return withSettings().stubOnly().defaultAnswer(answer);
    }

    private <T> MockedStatic<T> open(MockedStatic<T> mock) {
        mocks.add(mock);
        return mock;
    }

    private synchronized Object userSettings(InvocationOnMock invocation) {
        return switch (invocation.getMethod().getName()) {
            case "get" -> userSettings.computeIfAbsent(invocation.getArgument(0), SelfPlayEnvironment::newUserSettings);
            case "save" -> {
                UserSettings saved = invocation.getArgument(0);
                userSettings.put(saved.getUserId(), saved);
                yield null;
            }
            case "getAllUserSettings" -> List.copyOf(userSettings.values());
            default -> null;
        };
    }

    private static UserSettings newUserSettings(String userId) {
        UserSettings settings = new UserSettings();
        settings.setUserId(userId);
        return settings;
    }

    private Object circuitBreaker(InvocationOnMock invocation) throws Throwable {
        return switch (invocation.getMethod().getName()) {
            case "isOpen", "checkIsOpenAndPostWarningIfTrue" -> false;
            case "incrementThresholdCount" -> {
                circuitBreakerReasons.add(invocation.getArgument(0));
                yield false;
            }
            default -> invocation.callRealMethod();
        };
    }

    private Object botLogger(InvocationOnMock invocation) throws Throwable {
        String name = invocation.getMethod().getName();
        if ("warning".equals(name) || "error".equals(name) || "critical".equals(name)) {
            StringBuilder line = new StringBuilder(name.toUpperCase()).append(": ");
            Throwable failure = null;
            for (Object arg : invocation.getArguments()) {
                if (arg instanceof String text) line.append(text).append(' ');
                if (arg instanceof Throwable throwable) failure = throwable;
            }
            if (failure != null) line.append("| ").append(stackSummary(failure));
            synchronized (botProblems) {
                if (botProblems.size() < 2000) botProblems.add(line.toString().trim());
            }
            return null;
        }
        if ("info".equals(name)
                && invocation.getArguments().length > 0
                && invocation.getArgument(0) instanceof String text
                && text.startsWith(ti4.ai.fallback.FallbackLog.LINE_PREFIX)) {
            synchronized (fallbacks) {
                if (fallbacks.size() < 2000) fallbacks.add(text);
            }
            record(text);
            return null;
        }
        if ("info".equals(name) || name.startsWith("log")) return null;
        return invocation.callRealMethod();
    }

    // Promissory note plays reach the event log; every other event is dropped as before.
    private Object gameEvent(InvocationOnMock invocation) {
        if (!"commitEvent".equals(invocation.getMethod().getName())) return null;
        if (!ti4.spring.service.gameevent.GameEventType.CARD_PLAY_PROMISSORY_NOTE.equals(invocation.getArgument(1)))
            return null;
        Map<String, Object> payload = invocation.getArgument(3);
        Object note = payload == null ? null : payload.get("cardId");
        promissory.played(invocation.getArgument(0), invocation.getArgument(2), note == null ? null : note.toString());
        return null;
    }

    private Object gameManager(InvocationOnMock invocation) throws Throwable {
        Object result = invocation.callRealMethod();
        String name = invocation.getMethod().getName();
        boolean lost = ("get".equals(name) || "reload".equals(name)) && result == null;
        if ("delete".equals(name) || lost) {
            synchronized (botProblems) {
                botProblems.add("GAME MANAGER: " + name + " " + java.util.Arrays.toString(invocation.getArguments())
                        + " -> " + result + " | " + stackSummary(new Throwable("called from")));
            }
        }
        return result;
    }

    private Object actuator(InvocationOnMock invocation) throws Throwable {
        boolean press = "press".equals(invocation.getMethod().getName());
        Optional<TradeRecord> offered =
                press ? offerAboutToBeAccepted(invocation.getArgument(0), invocation.getArgument(1)) : Optional.empty();
        Optional<OverrulePick> overruled = press
                ? overrulePickAboutToBeMade(invocation.getArgument(0), invocation.getArgument(1))
                : Optional.empty();
        Object result = invocation.callRealMethod();
        if (press && result instanceof AiActuator.Result outcome) {
            AiActuator.PressTarget target = invocation.getArgument(1);
            AiActuator.SeatIdentity seat = invocation.getArgument(0);
            pressOutcomes.merge(outcome.outcome().name(), 1, Integer::sum);
            if (outcome.pressed()) {
                String family = handlerFamily(target.customId());
                pressedHandlers.merge(family, 1, Integer::sum);
                synchronized (pressesBySeat) {
                    pressesBySeat
                            .computeIfAbsent(seat.faction(), ignored -> new java.util.TreeMap<>())
                            .merge(family, 1, Integer::sum);
                }
                offered.filter(this::accepted).ifPresent(this::recordTrade);
                overruled.ifPresent(this::recordOverrulePick);
            }
            record(seat.faction() + " pressed " + target.customId() + " -> " + outcome.outcome() + " ("
                    + outcome.detail() + ")");
        }
        return result;
    }

    // The strategy card an Overrule pick press will start, read with the seat's points before the press runs.
    private Optional<OverrulePick> overrulePickAboutToBeMade(
            AiActuator.SeatIdentity seat, AiActuator.PressTarget target) {
        String customId = target.customId();
        if (!"winnuHero".equals(handlerFamily(customId)) || !customId.endsWith("_overrule")) return Optional.empty();
        Game current = game.get();
        Player player = current == null ? null : current.getPlayerFromColorOrFaction(seat.faction());
        if (player == null) return Optional.empty();
        String initiative = org.apache.commons.lang3.StringUtils.substringBefore(
                org.apache.commons.lang3.StringUtils.substringAfter(customId, "winnuHero_"), "_");
        if (initiative.isEmpty() || !initiative.chars().allMatch(Character::isDigit)) return Optional.empty();
        return Optional.of(new OverrulePick(
                seat.faction(), current.getRound(), Integer.parseInt(initiative), player.getTotalVictoryPoints()));
    }

    private void recordOverrulePick(OverrulePick pick) {
        synchronized (overrulePicks) {
            overrulePicks.add(pick);
        }
    }

    // The offer an Accept press would carry out, read from the offerer's items before the press clears them.
    private Optional<TradeRecord> offerAboutToBeAccepted(AiActuator.SeatIdentity seat, AiActuator.PressTarget target) {
        if (!ACCEPT_FAMILY.equals(handlerFamily(target.customId()))) return Optional.empty();
        Game current = game.get();
        if (current == null) return Optional.empty();
        String rest = org.apache.commons.lang3.StringUtils.substringAfter(target.customId(), ACCEPT_FAMILY + "_");
        String color = org.apache.commons.lang3.StringUtils.substringBefore(rest, "_");
        Player offerer = current.getPlayerFromColorOrFaction(color);
        Player accepter = current.getPlayerFromColorOrFaction(seat.faction());
        if (offerer == null || accepter == null) return Optional.empty();
        List<String> items = List.copyOf(offerer.getTransactionItemsWithPlayer(accepter));
        if (items.isEmpty()) return Optional.empty();
        String label;
        synchronized (tradeLedger) {
            label = lastOfferLabel.getOrDefault(offerer.getFaction() + ">" + accepter.getFaction(), "");
        }
        return Optional.of(
                new TradeRecord(current.getRound(), offerer.getFaction(), accepter.getFaction(), items, label));
    }

    // The engine clears the offerer's items once it carries the offer out; a refused Accept leaves them.
    private boolean accepted(TradeRecord offer) {
        Game current = game.get();
        if (current == null) return false;
        Player offerer = current.getPlayerFromColorOrFaction(offer.from());
        Player accepter = current.getPlayerFromColorOrFaction(offer.to());
        return offerer != null
                && accepter != null
                && offerer.getTransactionItemsWithPlayer(accepter).isEmpty();
    }

    private void recordTrade(TradeRecord trade) {
        synchronized (tradeLedger) {
            tradeLedger.add(trade);
            if (SETTLEMENT_LABEL.equals(lastOfferLabel.get(trade.from() + ">" + trade.to()))) {
                settlementsAccepted.merge(trade.from(), 1, Integer::sum);
            }
        }
        record("TRADE " + trade.from() + " -> " + trade.to() + " " + trade.items());
    }

    // "trade: send the <what> to <faction>": remembers what each offer was for, and counts the Trade settlements.
    private void noteOffer(String faction, AiDecision decision) {
        if (!(decision instanceof AiDecision.Press press) || !press.reason().startsWith(SEND_PREFIX)) return;
        String what = press.reason().substring(SEND_PREFIX.length());
        int to = what.lastIndexOf(" to ");
        if (to < 0) return;
        String label = what.substring(0, to);
        synchronized (tradeLedger) {
            lastOfferLabel.put(faction + ">" + what.substring(to + " to ".length()), label);
            if (SETTLEMENT_LABEL.equals(label)) settlementsSent.merge(faction, 1, Integer::sum);
        }
    }

    @SuppressWarnings("unchecked")
    private Object brains(InvocationOnMock invocation) throws Throwable {
        Object result = invocation.callRealMethod();
        if (!"forBrainId".equals(invocation.getMethod().getName())) return result;
        return ((Optional<FactionBrain>) result).map(this::recording);
    }

    private FactionBrain recording(FactionBrain brain) {
        return new FactionBrain() {
            @Override
            public String id() {
                return brain.id();
            }

            @Override
            public Set<String> publicWindowHandlerPrefixes() {
                return brain.publicWindowHandlerPrefixes();
            }

            @Override
            public AiDecision decide(AiTurnContext context) {
                AiDecision decision = brain.decide(context);
                decisionListener.accept(context, decision);
                if (!(decision instanceof AiDecision.Idle))
                    record(context.faction() + " decided " + describe(decision));
                countReason(decision);
                noteOffer(context.faction(), decision);
                rememberReason(decision);
                countRoundAction(decision, context.game().getRound());
                noteActionSecretScored(context, decision);
                mecatol.observe(context.game());
                expeditions.observe(context.game(), decision);
                countTacticalAction(context, decision);
                countPlanKind(context, decision);
                timeRanked(context, decision);
                return decision;
            }
        };
    }

    // Reasons are grouped by their wording without system positions or other numbers, so the report shows how often
    // the AI chose each kind of decision (for example how many tactical actions were expansions).
    private void countReason(AiDecision decision) {
        String reason =
                switch (decision) {
                    case AiDecision.Press press -> press.reason();
                    case AiDecision.Unsure unsure -> "UNSURE: " + unsure.reason();
                    default -> null;
                };
        if (reason == null) return;
        String kind = reason.replaceAll("\\d+", "#").replaceAll("\\s+#\\S*", "").trim();
        synchronized (decisionReasons) {
            decisionReasons.merge(kind, 1, Integer::sum);
        }
    }

    // Tactical actions and strategy card follows per round, the measure of how much each seat gets done.
    private void countRoundAction(AiDecision decision, int round) {
        if (!(decision instanceof AiDecision.Press press)) return;
        String kind = actionKind(press.reason());
        if (kind == null) return;
        synchronized (actionsByRound) {
            actionsByRound
                    .computeIfAbsent(round, ignored -> new java.util.TreeMap<>())
                    .merge(kind, 1, Integer::sum);
        }
    }

    private static final java.util.regex.Pattern ACTION_SECRET_SCORED =
            java.util.regex.Pattern.compile("^score (\\w+) after a combat$");

    private void noteActionSecretScored(AiTurnContext context, AiDecision decision) {
        if (!(decision instanceof AiDecision.Press press)) return;
        java.util.regex.Matcher scored = ACTION_SECRET_SCORED.matcher(press.reason());
        if (scored.matches()) {
            actionSecretRounds.putIfAbsent(
                    context.faction() + "|" + scored.group(1), context.game().getRound());
        }
    }

    // Every tactical action a seat starts, with the system its plan targets, for the custodians statistics.
    private void countTacticalAction(AiTurnContext context, AiDecision decision) {
        if (!(decision instanceof AiDecision.Press press)) return;
        if (!TACTICAL_ACTION.equals(actionKind(press.reason()))) return;
        Optional<String> target = press.reason().startsWith("start a tactical action")
                ? TacticalRules.rememberedPlan(context).map(TacticalPlan::target)
                : Optional.empty();
        mecatol.tacticalAction(context.game(), context.seat(), target);
    }

    // What kind of plan each tactical action followed, and how many attacks hit another player's home system, landed
    // there, hit Mecatol Rex, landed on it, or stood between the Imperial holder and Mecatol Rex.
    private void countPlanKind(AiTurnContext context, AiDecision decision) {
        if (!(decision instanceof AiDecision.Press press)) return;
        if (!press.reason().startsWith("start a tactical action")) return;
        Optional<TacticalPlan> plan = TacticalRules.rememberedPlan(context);
        if (plan.isEmpty()) return;
        List<String> kinds = new ArrayList<>();
        kinds.add(PLAN_PREFIX + plan.get().kind().name().toLowerCase());
        if (plan.get().kind() == TacticalPlan.Kind.EXPAND) {
            kinds.add(PLAN_PREFIX + expansionZone(context, plan.get()));
        }
        if (plan.get().kind() == TacticalPlan.Kind.ATTACK) {
            Game game = context.game();
            Tile target = game.getTileByPosition(plan.get().target());
            boolean otherHome = target != null
                    && target.isHomeSystem(game)
                    && target != context.seat().getHomeSystemTile();
            if (otherHome) kinds.add(PLAN_PREFIX + "attack on another home");
            if (otherHome && !plan.get().landings().isEmpty()) kinds.add(PLAN_PREFIX + "attack landing on a home");
            if (target != null && target.isMecatol(game)) {
                kinds.add(PLAN_PREFIX + "attack on Mecatol");
                if (plan.get().landings().keySet().stream().anyMatch(game.mecatols()::contains)) {
                    kinds.add(PLAN_PREFIX + "attack landing on Mecatol");
                }
            }
            if (target != null && target.isMecatol(game) && anotherPlayerHoldsImperial(game, context.seat())) {
                kinds.add(PLAN_PREFIX + "attack on Mecatol against Imperial");
            }
        }
        if (leavesSeatStack(context, plan.get())) {
            kinds.add(PLAN_PREFIX + "seat stack left by "
                    + plan.get().kind().name().toLowerCase());
        }
        synchronized (actionsByRound) {
            Map<String, Integer> round =
                    actionsByRound.computeIfAbsent(context.game().getRound(), ignored -> new java.util.TreeMap<>());
            kinds.forEach(kind -> round.merge(kind, 1, Integer::sum));
        }
    }

    // A holder of Occupy the Seat of the Empire whose plan moves ships off Mecatol Rex.
    private static boolean leavesSeatStack(AiTurnContext context, TacticalPlan plan) {
        Game game = context.game();
        Tile mecatol = game.getMecatolTile();
        if (mecatol == null || !context.seat().getSecretsUnscored().containsKey("ose")) return false;
        return plan.moves().stream()
                .anyMatch(move ->
                        move.origin().equals(mecatol.getPosition()) && !BoardView.GROUND_FORCES.contains(move.type()));
    }

    // Where an expansion goes, by the territory zone of its target and the round band, for the zone split in the batch
    // summary.
    private static String expansionZone(AiTurnContext context, TacticalPlan plan) {
        Game game = context.game();
        Tile target = game.getTileByPosition(plan.target());
        Territory.Zone zone = target == null
                ? Territory.Zone.NEUTRAL
                : Territory.of(game, context.seat()).zone(target);
        String area =
                switch (zone) {
                    case HOME, LANE, SLICE -> EXPAND_OWN_AREA;
                    case BORDER -> EXPAND_BORDER;
                    case FOREIGN, OTHER_HOME -> EXPAND_ANOTHERS_AREA;
                    case MECATOL -> EXPAND_MECATOL;
                    case NEUTRAL -> EXPAND_ELSEWHERE;
                };
        return area + " " + roundBand(game.getRound());
    }

    static final String EXPAND_OWN_AREA = "expand in own area";
    static final String EXPAND_BORDER = "expand on a border";
    static final String EXPAND_ANOTHERS_AREA = "expand in another's area";
    static final String EXPAND_MECATOL = "expand on Mecatol";
    static final String EXPAND_ELSEWHERE = "expand elsewhere";
    static final List<String> ROUND_BANDS = List.of("r1-2", "r3-4", "r5+");

    static String roundBand(int round) {
        if (round <= 2) return ROUND_BANDS.get(0);
        return round <= 4 ? ROUND_BANDS.get(1) : ROUND_BANDS.get(2);
    }

    // Every eighth tactical action, the planner ranks the same board again and the time it takes is kept, to compare
    // the
    // cost of planning between versions of the planner on real mid-game boards.
    static final java.util.concurrent.atomic.LongAdder RANKED_NANOS = new java.util.concurrent.atomic.LongAdder();
    static final java.util.concurrent.atomic.LongAdder RANKED_SAMPLES = new java.util.concurrent.atomic.LongAdder();
    static final java.util.concurrent.atomic.AtomicLong RANKED_MAX_NANOS = new java.util.concurrent.atomic.AtomicLong();
    // Sampled rankings that held at least one STAGE plan, next to the stage plans that were chosen.
    static final java.util.concurrent.atomic.LongAdder RANKED_WITH_STAGE = new java.util.concurrent.atomic.LongAdder();
    private static final java.util.concurrent.atomic.AtomicInteger TACTICAL_PRESSES =
            new java.util.concurrent.atomic.AtomicInteger();

    private static void timeRanked(AiTurnContext context, AiDecision decision) {
        if (!(decision instanceof AiDecision.Press press)) return;
        if (!press.reason().startsWith("start a tactical action")) return;
        if (TACTICAL_PRESSES.incrementAndGet() % 8 != 0) return;
        long start = System.nanoTime();
        List<TacticalPlan> ranked = TacticalPlanner.ranked(context.game(), context.seat());
        long elapsed = System.nanoTime() - start;
        RANKED_NANOS.add(elapsed);
        RANKED_SAMPLES.increment();
        RANKED_MAX_NANOS.accumulateAndGet(elapsed, Math::max);
        if (ranked.stream().anyMatch(plan -> plan.kind() == TacticalPlan.Kind.STAGE)) RANKED_WITH_STAGE.increment();
    }

    private static boolean anotherPlayerHoldsImperial(Game game, Player seat) {
        return game.getRealPlayers().stream()
                .filter(player -> player != seat)
                .flatMap(player -> player.getSCs().stream())
                .anyMatch(initiative -> StrategyCard.of(game, initiative) == StrategyCard.IMPERIAL
                        && !game.getPlayedSCs().contains(initiative));
    }

    static final String PLAN_PREFIX = "plan: ";

    private void rememberReason(AiDecision decision) {
        String reason =
                switch (decision) {
                    case AiDecision.Press press -> press.reason();
                    case AiDecision.Unsure unsure -> unsure.reason();
                    case AiDecision.Wait wait -> wait.reason();
                    default -> null;
                };
        if (reason != null) lastDecisionReason = reason;
    }

    static String actionKind(String reason) {
        if (reason.startsWith(TRADE_PREFIX)) return TRADE;
        if (reason.startsWith("start a tactical action") || reason.startsWith("use Warfare for a tactical action")) {
            return TACTICAL_ACTION;
        }
        if (reason.startsWith("follow ")) {
            String[] words = reason.split(" ");
            return words.length > 1 ? "follow " + words[1] : null;
        }
        return null;
    }

    static final String TACTICAL_ACTION = "tactical action";
    static final String TRADE = "trade";
    static final String TRADE_PREFIX = "trade: ";

    static String handlerFamily(String customId) {
        String handler = customId.startsWith("FFCC_")
                ? org.apache.commons.lang3.StringUtils.substringAfter(customId.substring(5), "_")
                : customId;
        int cut = handler.indexOf('_');
        return cut < 0 ? handler : handler.substring(0, cut);
    }

    private static String describe(AiDecision decision) {
        return switch (decision) {
            case AiDecision.Press press -> "press " + press.button().customId() + " (" + press.reason() + ")";
            case AiDecision.Unsure unsure ->
                "unsure about " + unsure.prompt().messageId() + " (" + unsure.reason() + ")";
            case AiDecision.Wait wait -> "wait (" + wait.reason() + ")";
            case AiDecision.Idle idle -> "idle";
            case AiDecision.Announce announce -> "announce (" + announce.text() + ")";
            case AiDecision.Propose propose -> "propose (" + propose.deal().purpose() + ")";
        };
    }

    private String lastTraceLine = "";
    private int repeats;

    synchronized void record(String line) {
        if (WATCH != null && WATCH.matcher(line).find()) {
            watched.add(String.format("[t+%ds] %s", (clock.get() - startOfGame) / 1000, line));
        }
        if (line.equals(lastTraceLine)) {
            repeats++;
            return;
        }
        if (repeats > 0) trace.addLast("    (repeated " + repeats + " more times)");
        repeats = 0;
        lastTraceLine = line;
        trace.addLast(String.format("[t+%ds] %s", (clock.get() - startOfGame) / 1000, line));
        while (trace.size() > TRACE_SIZE) trace.removeFirst();
    }

    synchronized List<String> traceLines() {
        List<String> lines = new ArrayList<>(trace);
        if (repeats > 0) lines.add("    (repeated " + repeats + " more times)");
        return lines;
    }

    private long startOfGame;

    void markStart() {
        startOfGame = clock.get();
    }

    long elapsedMillis() {
        return clock.get() - startOfGame;
    }

    private static String stackSummary(Throwable failure) {
        StringBuilder summary = new StringBuilder(failure.toString());
        StackTraceElement[] frames = failure.getStackTrace();
        for (int i = 0; i < Math.min(14, frames.length); i++)
            summary.append(" @ ").append(frames[i]);
        if (failure.getCause() != null && failure.getCause() != failure) {
            summary.append(" | caused by ").append(stackSummary(failure.getCause()));
        }
        return summary.toString();
    }

    @Override
    public void close() {
        for (int i = mocks.size() - 1; i >= 0; i--) {
            try {
                mocks.get(i).close();
            } catch (RuntimeException ignored) {
                // Keep closing the others.
            }
        }
        DiscordRouter.unbind(discord);
        deleteQuietly(storage);
    }

    private static void deleteQuietly(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Temporary files; the OS cleans up whatever is left.
                }
            });
        } catch (IOException ignored) {
            // Same as above.
        }
    }
}
