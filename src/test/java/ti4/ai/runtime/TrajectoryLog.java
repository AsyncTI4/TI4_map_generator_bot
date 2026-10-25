package ti4.ai.runtime;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.annotation.Nullable;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.strategy.SecondaryQueue;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.SaveText;
import ti4.message.GameMessage;
import tools.jackson.databind.ObjectMapper;

/**
 * Self-play trajectory log (-Dai.selfplay.trajectories=DIR): one JSON line per seat decision that does something
 * (presses, unsure fallbacks, announcements and proposals; idles and waits are left out), one per press outcome and
 * a closing outcome line, in DIR/GAME.jsonl.gz. Each decision line holds the board summary, the enabled buttons the
 * seat could see (grouped by prompt as [customId, label] pairs) and the choice with its reason, so batches can be
 * mined for how decisions relate to results.
 *
 * <p>With -Dai.selfplay.trajectories.snapshots=true, DIR/GAME.snapshots.zip also keeps, per decision, the save
 * file of the board the seat decided on ({@code NNNNNN.save.txt}) and the rest of its turn context ({@code
 * NNNNNN.context.json}: prompts, pressed keys, memory before the decision, virtual time, the game's message
 * reactions and open strategy card follows), which is enough for {@link TrajectoryReplay} to ask the brain the same
 * question again. Snapshots take about 40 MB per full game.
 */
final class TrajectoryLog implements AutoCloseable {

    static final String PROPERTY = "ai.selfplay.trajectories";
    static final String SNAPSHOTS_PROPERTY = "ai.selfplay.trajectories.snapshots";
    static final String LINES_SUFFIX = ".jsonl.gz";
    static final String SAVE_SUFFIX = ".save.txt";
    static final String CONTEXT_SUFFIX = ".context.json";
    static final ObjectMapper JSON = new ObjectMapper();

    private final String game;
    private final Supplier<List<GameMessage>> gameMessages;
    private final BufferedWriter lines;

    @Nullable
    private final ZipOutputStream snapshots;

    private int decisions;
    private final Map<String, Integer> lastDecisionBySeat = new HashMap<>();
    private final Map<String, String> lastChoiceBySeat = new HashMap<>();

    private TrajectoryLog(
            String game,
            Supplier<List<GameMessage>> gameMessages,
            BufferedWriter lines,
            @Nullable ZipOutputStream snapshots) {
        this.game = game;
        this.gameMessages = gameMessages;
        this.lines = lines;
        this.snapshots = snapshots;
    }

    @Nullable
    static TrajectoryLog openIfEnabled(String game, Supplier<List<GameMessage>> gameMessages) {
        String dir = System.getProperty(PROPERTY, "");
        if (dir.isBlank()) return null;
        try {
            Path folder = Files.createDirectories(Path.of(dir));
            OutputStream gzip = new GZIPOutputStream(Files.newOutputStream(folder.resolve(game + LINES_SUFFIX)));
            BufferedWriter lines = new BufferedWriter(new OutputStreamWriter(gzip, StandardCharsets.UTF_8));
            ZipOutputStream snapshots = null;
            if (Boolean.getBoolean(SNAPSHOTS_PROPERTY)) {
                OutputStream zip = Files.newOutputStream(folder.resolve(game + ".snapshots.zip"));
                snapshots = new ZipOutputStream(zip, StandardCharsets.UTF_8);
            }
            return new TrajectoryLog(game, gameMessages, lines, snapshots);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static boolean records(AiDecision decision) {
        return !(decision instanceof AiDecision.Idle) && !(decision instanceof AiDecision.Wait);
    }

    synchronized void decided(AiTurnContext context, Map<String, String> memoryBefore, AiDecision decision) {
        if (!records(decision)) return;
        int index = decisions++;
        Game board = context.game();
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("type", "decision");
        line.put("game", game);
        line.put("index", index);
        line.put("round", board.getRound());
        line.put("phase", board.getPhaseOfGame());
        line.put("active", factionOf(board, board.getActivePlayerID()));
        line.put("seat", context.faction());
        line.put("virtualMillis", context.now());
        line.put("kind", decision.getClass().getSimpleName().toLowerCase());
        line.put("reason", reasonOf(decision));
        line.put("chosen", chosen(decision));
        line.put("choices", choices(context.prompts()));
        line.put("seats", seats(board));
        write(line);
        lastDecisionBySeat.put(context.faction(), index);
        if (decision instanceof AiDecision.Press press) {
            lastChoiceBySeat.put(context.faction(), press.button().customId());
        }
        snapshot(index, context, memoryBefore);
    }

    synchronized void pressed(String faction, String customId, String outcome, String detail) {
        Integer index = lastDecisionBySeat.get(faction);
        if (index == null || !customId.equals(lastChoiceBySeat.get(faction))) return;
        lastChoiceBySeat.remove(faction);
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("type", "press");
        line.put("game", game);
        line.put("index", index);
        line.put("seat", faction);
        line.put("outcome", outcome);
        line.put("detail", detail);
        write(line);
    }

    synchronized void finish(String ending, @Nullable Game board) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("type", "outcome");
        line.put("game", game);
        line.put("ending", ending);
        line.put("decisions", decisions);
        if (board != null) {
            line.put("round", board.getRound());
            Map<String, Integer> points = new LinkedHashMap<>();
            board.getRealPlayers().forEach(player -> points.put(player.getFaction(), player.getTotalVictoryPoints()));
            line.put("victoryPoints", points);
            int best =
                    points.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            line.put(
                    "leaders",
                    points.entrySet().stream()
                            .filter(entry -> entry.getValue() == best)
                            .map(Map.Entry::getKey)
                            .toList());
        }
        write(line);
    }

    private void snapshot(int index, AiTurnContext context, Map<String, String> memoryBefore) {
        if (snapshots == null) return;
        Map<String, Object> rest = new LinkedHashMap<>();
        rest.put("seat", context.faction());
        rest.put("userId", context.seat().getUserID());
        rest.put("now", context.now());
        rest.put("pressedKeys", List.copyOf(context.pressedKeys()));
        rest.put("memory", memoryBefore);
        rest.put(
                "prompts", context.prompts().stream().map(TrajectoryLog::prompt).toList());
        rest.put(
                "gameMessages",
                gameMessages.get().stream().map(TrajectoryLog::gameMessage).toList());
        rest.put("secondaryQueue", SecondaryQueue.snapshot(context.game().getName()));
        try {
            entry(String.format("%06d%s", index, SAVE_SUFFIX), SaveText.of(context.game()));
            entry(String.format("%06d%s", index, CONTEXT_SUFFIX), JSON.writeValueAsString(rest));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void entry(String name, String text) throws IOException {
        snapshots.putNextEntry(new ZipEntry(name));
        snapshots.write(text.getBytes(StandardCharsets.UTF_8));
        snapshots.closeEntry();
    }

    private void write(Map<String, Object> line) {
        try {
            lines.write(JSON.writeValueAsString(line));
            lines.newLine();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String reasonOf(AiDecision decision) {
        return switch (decision) {
            case AiDecision.Press press -> press.reason();
            case AiDecision.Unsure unsure -> unsure.reason();
            case AiDecision.Announce announce -> announce.text();
            case AiDecision.Propose propose -> propose.deal().purpose();
            default -> "";
        };
    }

    @Nullable
    private static Map<String, Object> chosen(AiDecision decision) {
        return switch (decision) {
            case AiDecision.Press press -> button(press.prompt(), press.button());
            case AiDecision.Unsure unsure -> Map.of("prompt", unsure.prompt().key());
            default -> null;
        };
    }

    private static List<Map<String, Object>> choices(List<AiPrompt> prompts) {
        List<Map<String, Object>> choices = new ArrayList<>();
        for (AiPrompt prompt : prompts) {
            List<PromptButton> enabled = prompt.enabledButtons();
            if (enabled.isEmpty()) continue;
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("prompt", prompt.key());
            group.put("source", prompt.source().name());
            group.put(
                    "buttons",
                    enabled.stream()
                            .map(button -> List.of(button.customId(), button.label()))
                            .toList());
            choices.add(group);
        }
        return choices;
    }

    private static Map<String, Object> gameMessage(GameMessage message) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("messageId", message.messageId());
        entry.put("type", message.type().name());
        entry.put("factionsThatReacted", List.copyOf(message.factionsThatReacted()));
        entry.put("gameSaveTime", message.gameSaveTime());
        entry.put("key", message.key());
        return entry;
    }

    private static Map<String, Object> prompt(AiPrompt prompt) {
        Map<String, Object> full = new LinkedHashMap<>();
        full.put("channelId", prompt.channelId());
        full.put("messageId", prompt.messageId());
        full.put("source", prompt.source().name());
        full.put("content", prompt.content());
        full.put("createdAtMillis", prompt.createdAtMillis());
        List<Map<String, Object>> buttons = new ArrayList<>();
        for (PromptButton button : prompt.buttons()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("index", button.index());
            entry.put("customId", button.customId());
            entry.put("handlerId", button.handlerId());
            entry.put("ownerFaction", button.ownerFaction());
            entry.put("label", button.label());
            entry.put("disabled", button.disabled());
            buttons.add(entry);
        }
        full.put("buttons", buttons);
        return full;
    }

    private static Map<String, Object> button(AiPrompt prompt, PromptButton button) {
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("prompt", prompt.key());
        choice.put("source", prompt.source().name());
        choice.put("customId", button.customId());
        choice.put("handler", button.handlerId());
        choice.put("label", button.label());
        return choice;
    }

    private static List<Map<String, Object>> seats(Game board) {
        List<Map<String, Object>> seats = new ArrayList<>();
        for (Player player : board.getRealPlayers()) {
            Map<String, Object> seat = new LinkedHashMap<>();
            seat.put("faction", player.getFaction());
            seat.put("victoryPoints", player.getTotalVictoryPoints());
            seat.put("tradeGoods", player.getTg());
            seat.put("commodities", player.getCommodities());
            seat.put("planets", player.getPlanets().size());
            seat.put("tactic", player.getTacticalCC());
            seat.put("fleet", player.getFleetCC());
            seat.put("strategy", player.getStrategicCC());
            seat.put("actionCards", player.getActionCards().size());
            seat.put("secretsScored", player.getSecretsScored().size());
            seat.put("secretsHeld", player.getSecretsUnscored().size());
            seat.put("technologies", player.getTechs().size());
            seat.put("strategyCards", List.copyOf(player.getSCs()));
            seat.put("passed", player.isPassed());
            seats.add(seat);
        }
        return seats;
    }

    @Nullable
    private static String factionOf(Game board, @Nullable String userId) {
        if (userId == null) return null;
        Player player = board.getPlayer(userId);
        return player == null ? null : player.getFaction();
    }

    @Override
    public synchronized void close() {
        try {
            lines.close();
            if (snapshots != null) snapshots.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
