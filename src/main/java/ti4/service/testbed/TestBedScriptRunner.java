package ti4.service.testbed;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.cron.CronManager;
import ti4.executors.ExecutorServiceManager;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedGame;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.TestBedPreset;
import ti4.model.TestBedScript;
import ti4.model.TestBedScript.Expect;
import ti4.model.TestBedScript.Step;
import ti4.service.fow.GMService;
import ti4.service.game.StartPhaseService;
import ti4.service.testbed.TestBedPress.PressResult;
import ti4.service.testbed.TestBedPress.Recorder;
import ti4.service.turn.StartTurnService;

public final class TestBedScriptRunner {

    private static final int HISTORY_SIZE = 50;
    private static final String CARDS_INFO_LAST_TEXT = "You may whisper to people from here";
    private static final long CARDS_INFO_POLL_MILLIS = 1000;
    private static final long POLL_MILLIS = 2000;
    private static final int MAX_DETAIL = 160;

    private enum Status {
        PASS,
        FAIL,
        SKIP,
        INFO
    }

    private record Result(int index, String step, Status status, String expected, String actual) {}

    private final String gameName;
    private final String title;
    private final TestBedScript script;
    private final GenericInteractionCreateEvent origin;
    private final Member developer;
    private final MessageChannel reportChannel;

    @Nullable
    private final Consumer<String> onDone;

    private final boolean resetFirst;
    private final List<Result> results = new ArrayList<>();
    private final Map<String, Long> baselines = new HashMap<>();
    private Recorder lastPress = new Recorder();
    private String placeholderProblems = "";

    private TestBedScriptRunner(
            Game game,
            String title,
            TestBedScript script,
            GenericInteractionCreateEvent origin,
            @Nullable Consumer<String> onDone,
            boolean resetFirst) {
        this.gameName = game.getName();
        this.title = title;
        this.script = script;
        this.origin = origin;
        this.developer = origin.getMember();
        this.reportChannel = origin.getMessageChannel();
        this.onDone = onDone;
        this.resetFirst = resetFirst;
    }

    public static void start(Game game, TestBedScript script, GenericInteractionCreateEvent origin) {
        TestBedScriptRunner runner =
                new TestBedScriptRunner(game, titleOf(script), script, origin, null, script.getPreset() != null);
        ExecutorServiceManager.runAsync("test bed script " + runner.title, runner::runAndReport);
    }

    public static void startSuite(Game game, List<TestBedScript> scripts, GenericInteractionCreateEvent origin) {
        ExecutorServiceManager.runAsync("test bed suite in " + game.getName(), () -> runSuite(game, scripts, origin));
    }

    public static void startShortcut(
            Game game, String label, List<Step> steps, GenericInteractionCreateEvent origin, Consumer<String> onDone) {
        TestBedScript script = new TestBedScript();
        script.setName(label);
        script.setSteps(steps);
        TestBedScriptRunner runner = new TestBedScriptRunner(game, label, script, origin, onDone, false);
        ExecutorServiceManager.runAsync("test bed shortcut " + label, runner::runAndReport);
    }

    private static String titleOf(TestBedScript script) {
        return script.getName() == null ? "custom" : script.getName();
    }

    private void runAndReport() {
        runSafely();
        if (onDone != null) {
            onDone.accept(shortSummary());
        } else {
            report();
        }
    }

    private void runSafely() {
        try {
            run();
        } catch (Exception e) {
            BotLogger.error("Test bed script " + title + " stopped", e);
            add(-1, "runner", Status.FAIL, "no exception", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static void runSuite(Game game, List<TestBedScript> scripts, GenericInteractionCreateEvent origin) {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        StringBuilder discord = new StringBuilder("## Test bed suite in `")
                .append(game.getName())
                .append("`\n");
        StringBuilder markdown = new StringBuilder("# Test bed suite in ")
                .append(game.getName())
                .append(" (")
                .append(stamp)
                .append(")\n");
        for (TestBedScript script : scripts) {
            String reason = suiteSkipReason(game, script);
            if (reason != null) {
                discord.append("⏭️ `")
                        .append(titleOf(script))
                        .append("`: ")
                        .append(reason)
                        .append('\n');
                continue;
            }
            TestBedScriptRunner runner = new TestBedScriptRunner(game, titleOf(script), script, origin, null, true);
            runner.runSafely();
            discord.append(runner.countOf(Status.FAIL) == 0 ? "✅ `" : "❌ `")
                    .append(runner.title)
                    .append("`: ")
                    .append(runner.countOf(Status.PASS))
                    .append(" passed, ")
                    .append(runner.countOf(Status.FAIL))
                    .append(" failed")
                    .append(runner.firstFailure())
                    .append('\n');
            markdown.append('\n').append(runner.markdownSection());
        }
        MessageHelper.sendMessageToChannel(origin.getMessageChannel(), discord.toString());
        String fileName = "suite-" + game.getName() + "-" + stamp + ".md";
        MessageHelper.sendFileUploadToChannel(
                origin.getMessageChannel(),
                FileUpload.fromData(markdown.toString().getBytes(StandardCharsets.UTF_8), fileName));
    }

    @Nullable
    static String suiteSkipReason(Game game, TestBedScript script) {
        if (script.getPreset() == null) return "no preset";
        TestBedPreset preset = TestBedPresetService.getPreset(script.getPreset());
        return gameModeMismatch(game, preset);
    }

    @Nullable
    private static String gameModeMismatch(Game game, @Nullable TestBedPreset preset) {
        if (preset == null) return "unknown preset";
        if (preset.getFog() != null && preset.getFog() != game.isFowMode()) {
            return preset.getFog() ? "needs a fog game" : "needs a normal game";
        }
        return null;
    }

    private String firstFailure() {
        return results.stream()
                .filter(result -> result.status() == Status.FAIL)
                .findFirst()
                .map(result -> " (first: " + abbreviate(result.step()) + ")")
                .orElse("");
    }

    private String shortSummary() {
        long failed = countOf(Status.FAIL);
        if (failed == 0) return "done.";
        StringBuilder line = new StringBuilder().append(failed).append(" step(s) failed");
        results.stream()
                .filter(result -> result.status() == Status.FAIL)
                .forEach(result ->
                        line.append("\n❌ ").append(result.step()).append(": ").append(abbreviate(result.actual())));
        return line.toString();
    }

    private void run() {
        if (developer == null) {
            add(0, "start", Status.FAIL, "run by a server member", "no member");
            return;
        }
        if (!resetIfAsked() || !applyPresetIfNeeded()) return;
        if (!TestBedService.isTestBed(current())) {
            add(0, "start", Status.FAIL, "a test bed game", "not a test bed");
            return;
        }
        recordBaselines();
        List<Step> steps = script.getSteps();
        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            Status status = runStep(i + 1, step);
            boolean stop = step.getStopOnFail() != null ? step.getStopOnFail() : script.isStopOnFail();
            if (status == Status.FAIL && stop) {
                for (int rest = i + 1; rest < steps.size(); rest++) {
                    add(rest + 1, steps.get(rest).describe(), Status.SKIP, "", "stopped after a failure");
                }
                return;
            }
        }
    }

    private boolean resetIfAsked() {
        if (!resetFirst) return true;
        Game game = current();
        if (!TestBedService.isTestBed(game) || game.getRealPlayers().isEmpty()) return true;
        List<TestBedResetService.ResetResult> results = new ArrayList<>();
        boolean done = TestBedPress.runLocked(game, false, locked -> {
            TestBedResetService.ResetResult result = TestBedResetService.reset(locked);
            if (!result.fromSnapshot()) GameManager.save(locked, "Test bed reset");
            results.add(result);
        });
        if (!done || results.isEmpty()) {
            add(0, "reset", Status.FAIL, "reset", "game not loaded");
            return false;
        }
        add(0, "reset", Status.INFO, "", results.getFirst().toString());
        settle(null);
        return true;
    }

    private boolean applyPresetIfNeeded() {
        if (script.getPreset() == null) return true;
        Game game = current();
        String label = "preset `" + script.getPreset() + "`";
        if (!game.getRealPlayers().isEmpty()) {
            String applied = TestBedApplyService.appliedPreset(game);
            if (!script.getPreset().equals(applied)) {
                add(
                        0,
                        label,
                        Status.FAIL,
                        "a game set up with `" + script.getPreset() + "`",
                        "this game was set up with `" + (applied.isEmpty() ? "something else" : applied)
                                + "`; run it in a new game");
                return false;
            }
            add(0, label, Status.INFO, "", "already applied; earlier runs may have changed the game");
            return true;
        }
        TestBedPreset preset = TestBedPresetService.getPreset(script.getPreset());
        String mismatch = gameModeMismatch(game, preset);
        if (mismatch != null) {
            add(0, label, Status.FAIL, "a game the preset fits", mismatch);
            return false;
        }
        List<String> warnings = new ArrayList<>();
        Map<String, Long> before = latestCardsInfoIds(game);
        boolean done = TestBedPress.runLocked(
                game, locked -> warnings.addAll(TestBedApplyService.apply(locked, preset, origin)));
        if (!done) {
            add(0, "preset `" + script.getPreset() + "`", Status.FAIL, "applied", "game not loaded");
            return false;
        }
        add(
                0,
                "preset `" + script.getPreset() + "`",
                Status.INFO,
                "",
                warnings.isEmpty() ? "applied" : "applied; " + warnings);
        waitForCardsInfo(before);
        settle(null);
        return true;
    }

    private Map<String, Long> latestCardsInfoIds(Game game) {
        Map<String, Long> latest = new HashMap<>();
        for (Player player : game.getPlayers().values()) {
            String threadId = player.getCardsInfoThreadID();
            if (threadId == null || threadId.isBlank() || "null".equals(threadId)) continue;
            ThreadChannel thread = origin.getJDA().getThreadChannelById(threadId);
            if (thread != null) latest.put(threadId, latestMessageId(thread));
        }
        return latest;
    }

    private void waitForCardsInfo(Map<String, Long> before) {
        long deadline = System.currentTimeMillis() + script.getTimeoutSeconds() * 1000L;
        List<String> late = new ArrayList<>();
        for (Player player : current().getRealPlayers()) {
            MessageChannel thread = scopeChannel(current(), player.getFaction() + ":cards-info");
            if (thread == null) continue;
            long after = before.getOrDefault(thread.getId(), 0L);
            while (!cardsInfoArrived(thread, after) && System.currentTimeMillis() < deadline) {
                TestBedPress.sleep(CARDS_INFO_POLL_MILLIS);
            }
            if (!cardsInfoArrived(thread, after)) late.add(player.getFaction());
        }
        if (!late.isEmpty()) {
            add(0, "cards info", Status.INFO, "", "still arriving for " + late + "; hand buttons may be missing");
        }
    }

    private static boolean cardsInfoArrived(MessageChannel thread, long after) {
        return thread.getHistory().retrievePast(TestBedPress.HISTORY_SIZE).complete().stream()
                .anyMatch(message ->
                        message.getIdLong() > after && message.getContentRaw().contains(CARDS_INFO_LAST_TEXT));
    }

    private void recordBaselines() {
        Game game = current();
        List<String> scopes = new ArrayList<>(List.of("main", "actions"));
        for (Player player : game.getRealPlayers()) {
            String seat = player.getFaction();
            scopes.addAll(List.of(seat, seat + ":private", seat + ":cards-info", seat + ":combat"));
        }
        if (game.isFowMode()) scopes.add("gm");
        for (String scope : scopes) {
            MessageChannel channel = scopeChannel(game, scope);
            if (channel != null) baselines.putIfAbsent(channel.getId(), latestMessageId(channel));
        }
    }

    private Status runStep(int index, Step original) {
        String verb = original.verbs().getFirst();
        try {
            Step step = withPlaceholdersResolved(original);
            if (step == null) {
                return add(index, original.describe(), Status.FAIL, "placeholders resolved", placeholderProblems);
            }
            return switch (verb) {
                case "note" -> add(index, step.getNote(), Status.INFO, "", "");
                case "wait" -> {
                    sleep((long) (step.getWait() * 1000));
                    yield add(index, step.describe(), Status.INFO, "", "");
                }
                case "press", "pressId" -> press(index, step);
                case "do" -> doAction(index, step);
                case "expect" -> expect(index, step);
                default -> add(index, step.describe(), Status.FAIL, "a known verb", verb);
            };
        } catch (Exception e) {
            BotLogger.error("Test bed script step failed: " + original.describe(), e);
            return add(
                    index,
                    original.describe(),
                    Status.FAIL,
                    "no exception",
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private Status press(int index, Step step) {
        Game game = current();
        Player seat = seat(game, step.getAs());
        if (seat == null)
            return add(index, step.describe(), Status.FAIL, "seat `" + step.getAs() + "`", "no such seat");
        PressResult result;
        if (step.getPressId() != null) {
            MessageChannel channel =
                    step.getIn() != null ? scopeChannel(game, step.getIn()) : TestBedPress.ownChannel(game, seat);
            if (channel == null) return add(index, step.describe(), Status.FAIL, "a channel for the carrier", "none");
            result = TestBedPress.pressById(game, developer, seat, channel, step.getPressId());
        } else {
            result = TestBedPress.pressVisible(
                    game,
                    developer,
                    seat,
                    () -> pressChannels(current(), seat, step.getIn()),
                    step.getPress(),
                    timeoutMillis(step));
        }
        lastPress = result.recorder();
        settle(step);
        String detail = result.detail();
        if (!result.recorder().unsupportedCalls().isEmpty()) {
            detail += "; unsupported interaction calls " + result.recorder().unsupportedCalls();
        }
        return add(index, step.describe(), result.pressed() ? Status.PASS : Status.FAIL, "pressed", detail);
    }

    private List<MessageChannel> pressChannels(Game game, Player seat, @Nullable String scope) {
        if (scope != null) return listOf(scopeChannel(game, scope));
        List<MessageChannel> channels = new ArrayList<>();
        channels.add(seat.getCardsInfoThread());
        channels.add(seat.getCorrectChannel());
        channels.add(game.getMainGameChannel());
        return channels;
    }

    private static List<MessageChannel> listOf(@Nullable MessageChannel channel) {
        List<MessageChannel> channels = new ArrayList<>();
        if (channel != null) channels.add(channel);
        return channels;
    }

    private Status doAction(int index, Step step) {
        String action = step.getAction();
        String describe = step.describe();
        if ("runCron".equals(action)) {
            boolean started = CronManager.runCron(step.getValue());
            settle(step);
            return add(
                    index,
                    describe,
                    started ? Status.PASS : Status.FAIL,
                    "cron started",
                    started ? "started" : "unknown cron");
        }
        List<String> problems = new ArrayList<>();
        boolean done = TestBedPress.runLocked(current(), game -> applyAction(game, step, problems));
        settle(step);
        if (!done) return add(index, describe, Status.FAIL, "done", "game not loaded");
        return add(
                index,
                describe,
                problems.isEmpty() ? Status.PASS : Status.FAIL,
                "done",
                problems.isEmpty() ? "done" : String.join("; ", problems));
    }

    private void applyAction(Game game, Step step, List<String> problems) {
        switch (step.getAction()) {
            case "startPhase" -> StartPhaseService.startPhase(origin, game, step.getValue());
            case "setStored" -> TestBedService.store(game, step.getKey(), step.getValue());
            case "removeStored" -> game.removeStoredValue(step.getKey());
            case "setActivePlayer" -> {
                Player seat = seat(game, step.getAs());
                if (seat == null) {
                    problems.add("no seat `" + step.getAs() + "`");
                    return;
                }
                game.updateActivePlayer(seat);
                StartTurnService.turnStart(origin, game, seat);
            }
            case "actAs" -> {
                boolean you = TestBedScriptService.YOU.equals(step.getAs());
                Player seat = you ? null : seat(game, step.getAs());
                if (!you && seat == null) {
                    problems.add("no seat `" + step.getAs() + "`");
                    return;
                }
                TestBedService.setActingAs(game, developer.getId(), seat);
            }
            case "hand" -> {
                List<Player> targets = seats(game, step.getAs());
                if (targets.isEmpty()) problems.add("no seat `" + step.getAs() + "`");
                for (Player seat : targets) {
                    TestBedApplyService.applyHand(game, seat, step.getHand(), origin, problems);
                }
            }
            default -> problems.add("unknown action `" + step.getAction() + "`");
        }
    }

    private record Check(boolean ok, String actual) {}

    private Status expect(int index, Step step) {
        Expect expect = step.getExpect();
        Check check = waitsForChange(expect) ? waitFor(() -> evaluate(expect), step) : evaluate(expect);
        return add(index, step.describe(), check.ok() ? Status.PASS : Status.FAIL, expect.describe(), check.actual());
    }

    private static boolean waitsForChange(Expect expect) {
        if (expect.getState() != null) return true;
        return expect.getIn() != null && expect.getNotContains().isEmpty() && !expect.isNoFactionLeak();
    }

    private Check waitFor(Supplier<Check> evaluation, Step step) {
        long deadline = System.currentTimeMillis() + timeoutMillis(step);
        Check check = evaluation.get();
        while (!check.ok() && System.currentTimeMillis() < deadline) {
            sleep(POLL_MILLIS);
            check = evaluation.get();
        }
        return check;
    }

    private long timeoutMillis(Step step) {
        int seconds = step.getTimeoutSeconds() != null ? step.getTimeoutSeconds() : script.getTimeoutSeconds();
        return seconds * 1000L;
    }

    private Check evaluate(Expect expect) {
        if (expect.getState() != null) return checkState(expect);
        if (expect.getEphemeral() != null) {
            String hit = lastPress.replies().stream()
                    .filter(reply -> reply.contains(expect.getEphemeral()))
                    .findFirst()
                    .orElse(null);
            return new Check(hit != null, hit != null ? hit : "replies: " + lastPress.replies());
        }
        if (expect.getModal() != null) {
            String modal = lastPress.modalId();
            return new Check(modal != null && modal.startsWith(expect.getModal()), String.valueOf(modal));
        }
        return checkMessages(expect);
    }

    private Check checkState(Expect expect) {
        Game game = current();
        String actual = TestBedStateResolver.resolve(game, expect.getState(), name -> seat(game, name));
        boolean ok = expect.getEquals() != null
                ? expect.getEquals().trim().equalsIgnoreCase(actual.trim())
                : expect.getContains().stream().allMatch(actual::contains)
                        && expect.getNotContains().stream().noneMatch(actual::contains);
        return new Check(ok, actual);
    }

    @Nullable
    private Step withPlaceholdersResolved(Step step) {
        placeholderProblems = "";
        String json = TestBedPresetService.toJson(step);
        if (!TestBedPlaceholders.hasPlaceholders(json)) return step;
        Game game = current();
        Player actor = step.getAs() == null || TestBedScriptService.ALL_SEATS.equals(step.getAs())
                ? null
                : seat(game, step.getAs());
        TestBedPlaceholders.Resolution resolution = TestBedPlaceholders.resolve(json, actor, name -> seat(game, name));
        if (!resolution.problems().isEmpty()) {
            placeholderProblems = String.join("; ", resolution.problems());
            return null;
        }
        return TestBedPresetService.parse(resolution.text(), Step.class);
    }

    private Check checkMessages(Expect expect) {
        Game game = current();
        MessageChannel channel = scopeChannel(game, expect.getIn());
        if (channel == null) return new Check(false, "channel `" + expect.getIn() + "` not found");
        List<String> texts = messagesSinceStart(channel);
        List<String> problems = new ArrayList<>();
        if (expect.getCount() != null) {
            String text = expect.getContains().getFirst();
            long count =
                    texts.stream().filter(message -> message.contains(text)).count();
            if (count != expect.getCount()) problems.add("`" + text + "` ×" + count);
        } else {
            for (String text : expect.getContains()) {
                if (texts.stream().noneMatch(message -> message.contains(text))) problems.add("missing `" + text + "`");
            }
        }
        for (String text : expect.getNotContains()) {
            texts.stream()
                    .filter(message -> message.contains(text))
                    .findFirst()
                    .ifPresent(hit -> problems.add("found `" + text + "` in: " + abbreviate(hit)));
        }
        if (expect.isNoFactionLeak()) problems.addAll(factionLeaks(game, texts));
        if (problems.isEmpty()) return new Check(true, texts.size() + " new messages checked");
        String latest = texts.isEmpty() ? " (no new messages)" : "; latest: " + abbreviate(texts.getFirst());
        return new Check(false, String.join("; ", problems) + latest);
    }

    private static List<String> factionLeaks(Game game, List<String> texts) {
        List<String> leaks = new ArrayList<>();
        for (String text : texts) {
            for (Player player : game.getRealPlayers()) {
                Pattern faction =
                        Pattern.compile("\\b" + Pattern.quote(player.getFaction()) + "\\b", Pattern.CASE_INSENSITIVE);
                if (faction.matcher(text).find()) leaks.add(player.getFaction() + " named in: " + abbreviate(text));
                if (text.contains("<@" + player.getUserID() + ">")) leaks.add(player.getFaction() + " mentioned");
                String emoji = player.getFactionEmoji();
                if (emoji != null && !emoji.isBlank() && text.contains(emoji))
                    leaks.add(player.getFaction() + " emoji");
            }
        }
        return leaks;
    }

    private List<String> messagesSinceStart(MessageChannel channel) {
        long baseline = baselines.getOrDefault(channel.getId(), 0L);
        List<String> texts = new ArrayList<>();
        for (Message message : channel.getHistory().retrievePast(HISTORY_SIZE).complete()) {
            if (message.getIdLong() <= baseline) continue;
            texts.add(messageText(message));
        }
        return texts;
    }

    private static String messageText(Message message) {
        StringBuilder text = new StringBuilder(message.getContentRaw());
        for (MessageEmbed embed : message.getEmbeds()) {
            if (embed.getTitle() != null) text.append('\n').append(embed.getTitle());
            if (embed.getDescription() != null) text.append('\n').append(embed.getDescription());
        }
        return text.toString();
    }

    private static long latestMessageId(MessageChannel channel) {
        List<Message> last = channel.getHistory().retrievePast(1).complete();
        return last.isEmpty() ? 0 : last.getFirst().getIdLong();
    }

    @Nullable
    private MessageChannel scopeChannel(Game game, String scope) {
        switch (scope) {
            case "main" -> {
                return game.getMainGameChannel();
            }
            case "actions" -> {
                return game.getActionsChannel();
            }
            case "gm" -> {
                return game.isFowMode() ? GMService.getGMChannel(game) : null;
            }
            default -> {
                int colon = scope.indexOf(':');
                Player seat = seat(game, colon < 0 ? scope : scope.substring(0, colon));
                if (seat == null) return null;
                if (colon < 0) return seat.getCorrectChannel();
                return switch (scope.substring(colon + 1)) {
                    case "private" -> seat.getPrivateChannel();
                    case "combat" -> TestBedCombatThreads.latest(game, seat);
                    default -> seat.getCardsInfoThread();
                };
            }
        }
    }

    @Nullable
    private Player seat(Game game, String name) {
        if (TestBedScriptService.YOU.equals(name)) return game.getPlayer(developer.getId());
        if (name.startsWith(TestBedScriptService.VIRTUAL_SEAT_NAME)
                && name.length() > TestBedScriptService.VIRTUAL_SEAT_NAME.length()) {
            String userName = "TestSeat" + name.substring(TestBedScriptService.VIRTUAL_SEAT_NAME.length());
            for (Player player : game.getRealPlayers()) {
                if (userName.equals(player.getUserName())) return player;
            }
        }
        return game.getPlayerFromColorOrFaction(name);
    }

    private List<Player> seats(Game game, String name) {
        if (TestBedScriptService.ALL_SEATS.equals(name)) return game.getRealPlayers();
        Player seat = seat(game, name);
        return seat == null ? List.of() : List.of(seat);
    }

    private Game current() {
        ManagedGame managed = GameManager.getManagedGame(gameName);
        if (managed == null) throw new IllegalStateException("game " + gameName + " is not loaded");
        return managed.getGame();
    }

    private void settle(@Nullable Step step) {
        int seconds =
                step != null && step.getSettleSeconds() != null ? step.getSettleSeconds() : script.getSettleSeconds();
        sleep(seconds * 1000L);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Status add(int index, String step, Status status, String expected, String actual) {
        results.add(new Result(index, step, status, flatten(expected), flatten(actual)));
        return status;
    }

    private static String flatten(@Nullable String text) {
        return text == null ? "" : text.replace('\n', ' ');
    }

    private static String abbreviate(@Nullable String text) {
        String flat = flatten(text);
        return flat.length() > MAX_DETAIL ? flat.substring(0, MAX_DETAIL - 3) + "..." : flat;
    }

    private long countOf(Status status) {
        return results.stream().filter(result -> result.status() == status).count();
    }

    private void report() {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        StringBuilder discord = new StringBuilder("## Test bed script `")
                .append(title)
                .append("`: ")
                .append(countOf(Status.PASS))
                .append(" ✅ ")
                .append(countOf(Status.FAIL))
                .append(" ❌ ")
                .append(countOf(Status.SKIP))
                .append(" ⏭️\n");
        for (Result result : results) {
            discord.append(icon(result.status())).append(' ');
            if (result.index() > 0) discord.append(result.index()).append(". ");
            discord.append(result.step());
            if (result.status() == Status.FAIL) {
                discord.append(" — expected `")
                        .append(abbreviate(result.expected()))
                        .append("`, got `")
                        .append(abbreviate(result.actual()))
                        .append('`');
            }
            discord.append('\n');
        }
        MessageHelper.sendMessageToChannel(reportChannel, discord.toString());
        String markdown =
                "# Test bed script `" + title + "` in " + gameName + " (" + stamp + ")\n\n" + markdownSection();
        String fileName = "script-" + title + "-" + gameName + "-" + stamp + ".md";
        MessageHelper.sendFileUploadToChannel(
                reportChannel, FileUpload.fromData(markdown.getBytes(StandardCharsets.UTF_8), fileName));
    }

    private String markdownSection() {
        StringBuilder markdown = new StringBuilder("## `").append(title).append("`\n\n");
        if (script.getDescription() != null)
            markdown.append(script.getDescription()).append("\n\n");
        markdown.append("| # | Step | Result | Expected | Actual |\n|---|---|---|---|---|\n");
        for (Result result : results) {
            markdown.append("| ")
                    .append(result.index())
                    .append(" | ")
                    .append(result.step().replace("|", "/"))
                    .append(" | ")
                    .append(result.status())
                    .append(" | ")
                    .append(result.expected().replace("|", "/"))
                    .append(" | ")
                    .append(result.actual().replace("|", "/"))
                    .append(" |\n");
        }
        return markdown.toString();
    }

    private static String icon(Status status) {
        return switch (status) {
            case PASS -> "✅";
            case FAIL -> "❌";
            case SKIP -> "⏭️";
            case INFO -> "ℹ️";
        };
    }
}
