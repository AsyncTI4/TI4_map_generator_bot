package ti4.ai.runtime;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import ti4.ai.strategy.SecondaryQueue;
import ti4.ai.tactical.StyxProbe;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.SaveText;
import ti4.testUtils.BaseTi4Test;

// Reads games recorded with -Dai.selfplay.savesdir and runs the Styx probe at the start of every action-phase turn,
// straight from the recorded saves, without playing anything again: minutes instead of the hours a batch takes. Each
// turn is the first save of the action phase with a new active seat; the per-game AI state recorded next to the saves
// (remembered Fracture marches, open secondaries, Slay intents) is put back first. Attack memory (AttackLedger) is not
// recorded, so a seat with the ATTACK_MEMORY experiment can rank strikes differently than it did in the game; replay
// the
// game with AiSelfPlayTest#replaySavedGame when that matters.
// mvn -o test -Dtest=SavedGameInspectionTest -Dai.selfplay.inspect=DIR [-Dai.selfplay.inspect.threads=8]
// DIR is either one game's folder or the recording folder holding several.
@EnabledIfSystemProperty(named = "ai.selfplay.inspect", matches = ".+")
class SavedGameInspectionTest extends BaseTi4Test {

    @Test
    void probeRecordedTurns() throws Exception {
        Path root = Path.of(System.getProperty("ai.selfplay.inspect"));
        List<Path> games = gameFolders(root);
        int threads = Math.clamp(Integer.getInteger("ai.selfplay.inspect.threads", 8), 1, Math.max(1, games.size()));
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<List<String>>> lines = new ArrayList<>();
            for (Path game : games) lines.add(pool.submit(() -> inspect(game)));
            for (Future<List<String>> found : lines) found.get().forEach(System.out::println);
        } finally {
            pool.shutdownNow();
        }
    }

    private static List<Path> gameFolders(Path root) throws IOException {
        if (Files.exists(root.resolve(GameRecorder.MANIFEST))) return List.of(root);
        try (Stream<Path> listing = Files.list(root)) {
            return listing.filter(folder -> Files.exists(folder.resolve(GameRecorder.MANIFEST)))
                    .sorted()
                    .toList();
        }
    }

    private static List<String> inspect(Path folder) {
        Properties manifest = SavedGameReplay.manifest(folder);
        String name = manifest.getProperty("name");
        TreeMap<Integer, Path> saves = SavedGameReplay.saveFiles(folder);
        TreeMap<Integer, Path> aiStates = SavedGameReplay.aiStateFiles(folder);
        List<String> lines = new ArrayList<>();
        try (SelfPlayEnvironment env = new SelfPlayEnvironment(Long.parseLong(manifest.getProperty("clockStart")))) {
            String probedTurn = "";
            for (Map.Entry<Integer, Path> save : saves.entrySet()) {
                if (!save.getValue().getFileName().toString().contains("-action")) continue;
                Game game = SaveText.read(SavedGameReplay.read(save.getValue()));
                if (!"action".equalsIgnoreCase(game.getPhaseOfGame())) continue;
                Player active = game.getActivePlayer();
                if (active == null) continue;
                String turn = game.getRound() + "|" + active.getFaction();
                if (turn.equals(probedTurn)) continue;
                probedTurn = turn;
                Map.Entry<Integer, Path> aiState = aiStates.floorEntry(save.getKey());
                if (aiState != null) GameRecorder.restoreAiState(name, Files.readString(aiState.getValue()));
                try {
                    StyxProbe.report(game, active).ifPresent(line -> lines.add(line + " save=" + save.getKey()));
                } catch (RuntimeException e) {
                    lines.add("STYXPROBE error game=" + name + " save=" + save.getKey() + " " + e);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            AiGameState.forget(name);
            SecondaryQueue.forget(name);
        }
        return lines;
    }
}
