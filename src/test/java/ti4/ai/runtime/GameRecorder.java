package ti4.ai.runtime;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.TreeMap;
import java.util.function.LongSupplier;
import java.util.zip.GZIPOutputStream;
import javax.annotation.Nullable;
import ti4.ai.strategy.SecondaryQueue;
import ti4.ai.tactical.FractureMarch;
import ti4.ai.tactical.SlayIntent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.SaveText;
import ti4.json.JsonMapperManager;
import ti4.spring.service.gameevent.GameEventRows;
import ti4.website.model.CompactMapState;

/**
 * -Dai.selfplay.savesdir=DIR: every self-play game keeps a folder DIR/GAME with
 * <ul>
 *   <li>manifest.properties: the game name, slot, seed, virtual clock start, factions and every ai.selfplay.* flag the
 *       batch ran with, plus replay.cmd, the Maven command that replays the game;</li>
 *   <li>saves/NNNNNN-rR-PHASE.txt.gz: the save file after save number NNNNNN, written only when it changed (apart from
 *       real-clock stamps), so the
 *       state after save N is the newest file numbered N or lower;</li>
 *   <li>saves/NNNNNN.ai.json: the per-game AI state outside the save (open secondaries, Slay intents, remembered
 *       Fracture marches), written when it changed;</li>
 *   <li>finished.properties: the number of saves and events, written when the game ended;</li>
 *   <li>events.jsonl.gz: one line per game event, with the columns of the game events table (seq, archetype, round,
 *       phase, faction, virtual timestamp, payload, map state when it changed, movement state, player state when it
 *       changed) and the number of the save it follows.</li>
 * </ul>
 * The seeded game is deterministic, so AiSelfPlayTest#replaySavedGame plays it again from the manifest and checks every
 * save against the recorded one; SavedGameInspection reads the saves directly.
 */
final class GameRecorder implements AutoCloseable {

    static final String PROPERTY = "ai.selfplay.savesdir";
    static final String MANIFEST = "manifest.properties";
    static final String REPLAY_COMMAND = "replay.cmd";
    static final String SAVES = "saves";
    static final String EVENTS = "events.jsonl.gz";
    static final String SAVE_SUFFIX = ".txt.gz";
    static final String AI_SUFFIX = ".ai.json";
    static final String FINISHED = "finished.properties";

    private final Path folder;
    private final Path saves;
    private final LongSupplier clock;
    private final Writer events;
    private int saveIndex;
    private long eventSeq;
    private String lastSave;
    private String lastAiState;
    private String lastMapState;
    private String lastPlayerState;

    static Optional<Path> root() {
        return Optional.ofNullable(System.getProperty(PROPERTY))
                .filter(value -> !value.isBlank())
                .map(Path::of);
    }

    GameRecorder(Path folder, LongSupplier clock) throws IOException {
        this.folder = folder;
        this.saves = Files.createDirectories(folder.resolve(SAVES));
        this.clock = clock;
        this.events = new OutputStreamWriter(
                new GZIPOutputStream(Files.newOutputStream(folder.resolve(EVENTS))), StandardCharsets.UTF_8);
    }

    static void writeManifest(Path folder, Properties manifest, String replayCommand) throws IOException {
        Files.createDirectories(folder);
        try (Writer writer = Files.newBufferedWriter(folder.resolve(MANIFEST), StandardCharsets.UTF_8)) {
            manifest.store(writer, "AI self-play recording");
        }
        Files.writeString(folder.resolve(REPLAY_COMMAND), replayCommand + System.lineSeparator());
    }

    synchronized void saved(Game game) {
        saveIndex++;
        String text = SaveText.of(game);
        String comparable = SavedGameReplay.comparableText(text);
        if (!comparable.equals(lastSave)) {
            lastSave = comparable;
            writeGzip(saves.resolve(saveFileName(saveIndex, game)), text);
        }
        String aiState = aiState(game.getName());
        if (!aiState.equals(lastAiState)) {
            lastAiState = aiState;
            write(saves.resolve(String.format(Locale.ROOT, "%06d%s", saveIndex, AI_SUFFIX)), aiState);
        }
    }

    synchronized void event(
            Game game,
            String archetype,
            @Nullable Player player,
            @Nullable Map<String, Object> payload,
            @Nullable String movementState) {
        String mapState = CompactMapState.serialize(game);
        String playerState = GameEventRows.playerState(game);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("seq", ++eventSeq);
        row.put("save", saveIndex);
        row.put("archetype", archetype);
        row.put("round", game.getRound());
        row.put("phase", game.getPhaseOfGame());
        row.put("faction", player == null ? null : player.getFaction());
        row.put("timestampEpochMillis", clock.getAsLong());
        row.put("payload", payload);
        row.put("mapState", mapState.equals(lastMapState) ? null : mapState);
        row.put("movementState", movementState);
        row.put("playerState", playerState.equals(lastPlayerState) ? null : playerState);
        lastMapState = mapState;
        lastPlayerState = playerState;
        try {
            events.write(JsonMapperManager.basic().writeValueAsString(row));
            events.write('\n');
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    synchronized int saveCount() {
        return saveIndex;
    }

    static String saveFileName(int index, Game game) {
        String phase =
                String.valueOf(game.getPhaseOfGame()).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return String.format(Locale.ROOT, "%06d-r%d-%s%s", index, game.getRound(), phase, SAVE_SUFFIX);
    }

    static String aiState(String gameName) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("openFollows", new TreeMap<>(SecondaryQueue.snapshot(gameName)));
        state.put("slayIntents", new TreeMap<>(SlayIntent.snapshot(gameName)));
        state.put("marches", new TreeMap<>(FractureMarch.snapshot(gameName)));
        return JsonMapperManager.basic().writeValueAsString(state);
    }

    record AiState(
            Map<String, Long> openFollows, Map<String, String> slayIntents, Map<String, FractureMarch> marches) {}

    static void restoreAiState(String gameName, String json) {
        AiState state = JsonMapperManager.basic().readValue(json, AiState.class);
        SecondaryQueue.restore(gameName, state.openFollows());
        SlayIntent.restore(gameName, state.slayIntents());
        FractureMarch.restore(gameName, state.marches());
    }

    private static void writeGzip(Path path, String text) {
        try (Writer writer =
                new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(path)), StandardCharsets.UTF_8)) {
            writer.write(text);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(Path path, String text) {
        try {
            Files.writeString(path, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public synchronized void close() throws IOException {
        events.close();
        Properties finished = new Properties();
        finished.setProperty("saves", String.valueOf(saveIndex));
        finished.setProperty("events", String.valueOf(eventSeq));
        try (Writer writer = Files.newBufferedWriter(folder.resolve(FINISHED), StandardCharsets.UTF_8)) {
            finished.store(writer, "AI self-play recording, written when the game ended");
        }
    }
}
