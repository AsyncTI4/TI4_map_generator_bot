package ti4.ai.runtime;

import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.mockito.MockedStatic;
import org.mockito.invocation.InvocationOnMock;
import ti4.ai.AiSettings;
import ti4.ai.actuation.AiActuator;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.FactionBrain;
import ti4.ai.brain.FactionBrainRegistry;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.service.CombatReplayService;
import ti4.discord.JdaService;
import ti4.executors.CircuitBreaker;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Storage;
import ti4.image.MapRenderPipeline;
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
import ti4.spring.service.title.PlayerTitleService;
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
    // Trading: every accepted offer (read from the offerer's items just before the Accept press), the presses each seat
    // made by handler family, and the Trade settlements each holder sent and had accepted.
    final List<TradeRecord> tradeLedger = new ArrayList<>();
    final Map<String, Map<String, Integer>> pressesBySeat = new java.util.TreeMap<>();
    final Map<String, Integer> settlementsSent = new java.util.TreeMap<>();
    final Map<String, Integer> settlementsAccepted = new java.util.TreeMap<>();
    private final Map<String, String> lastOfferLabel = new HashMap<>();
    volatile String lastDecisionReason = "";
    Supplier<Game> game = () -> null;

    record TradeRecord(int round, String from, String to, List<String> items) {}

    static final String SEND_PREFIX = "trade: send the ";
    static final String SETTLEMENT_LABEL = "Trade settlement";
    private static final String ACCEPT_FAMILY = "acceptOffer";
    private final Map<String, UserSettings> userSettings = new HashMap<>();
    private final List<MockedStatic<?>> mocks = new ArrayList<>();
    private final Object previousJda = JdaService.jda;
    private final Object previousGuild = JdaService.guildPrimary;

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
        JdaService.jda = discord.jda();
        JdaService.guildPrimary = discord.guild();

        Map<Class<?>, Object> beans = new HashMap<>();
        beans.put(GameMessageService.class, gameMessages.service());
        beans.put(CombatContestSettings.class, mock(CombatContestSettings.class));
        beans.put(CombatReplayService.class, mock(CombatReplayService.class));
        beans.put(PlayerTitleService.class, mock(PlayerTitleService.class));
        beans.put(GameEventService.class, mock(GameEventService.class));
        beans.put(ActiveLeaseService.class, mock(ActiveLeaseService.class, invocation -> {
            Class<?> type = invocation.getMethod().getReturnType();
            if (type == boolean.class)
                return !invocation.getMethod().getName().toLowerCase().contains("drain");
            return null;
        }));
        MockedStatic<SpringContext> spring = open(mockStatic(SpringContext.class));
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
                invocation -> "getStoragePath".equals(invocation.getMethod().getName())
                        ? storage.toString()
                        : invocation.callRealMethod()));
        Storage.init();

        open(mockStatic(MapRenderPipeline.class));
        open(mockStatic(GameDatabaseSyncPipeline.class));
        open(mockStatic(AutoPingMetadataManager.class));
        open(mockStatic(TechSummariesMetadataManager.class));
        open(mockStatic(UserSettingsManager.class, this::userSettings));

        MockedStatic<AiRuntime> runtime = open(mockStatic(AiRuntime.class, CALLS_REAL_METHODS));
        runtime.when(AiRuntime::mayMutate).thenReturn(true);
        runtime.when(AiRuntime::isProcessReady).thenReturn(true);
        MockedStatic<AiSettings> settings = open(mockStatic(AiSettings.class, CALLS_REAL_METHODS));
        settings.when(AiSettings::isEnabled).thenReturn(true);

        open(mockStatic(CircuitBreaker.class, this::circuitBreaker));
        open(mockStatic(BotLogger.class, this::botLogger));
        open(mockStatic(AiActuator.class, this::actuator));
        open(mockStatic(FactionBrainRegistry.class, this::brains));
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
        if ("info".equals(name) || name.startsWith("log")) return null;
        return invocation.callRealMethod();
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
            }
            record(seat.faction() + " pressed " + target.customId() + " -> " + outcome.outcome() + " ("
                    + outcome.detail() + ")");
        }
        return result;
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
        return Optional.of(new TradeRecord(current.getRound(), offerer.getFaction(), accepter.getFaction(), items));
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
                if (!(decision instanceof AiDecision.Idle))
                    record(context.faction() + " decided " + describe(decision));
                countReason(decision);
                noteOffer(context.faction(), decision);
                rememberReason(decision);
                countRoundAction(decision, context.game().getRound());
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
        JdaService.jda = (net.dv8tion.jda.api.JDA) previousJda;
        JdaService.guildPrimary = (net.dv8tion.jda.api.entities.Guild) previousGuild;
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
