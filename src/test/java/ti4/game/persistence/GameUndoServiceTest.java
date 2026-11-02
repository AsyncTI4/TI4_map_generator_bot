package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.service.game.GameUndoNameService;
import ti4.testUtils.BaseTi4Test;

class GameUndoServiceTest extends BaseTi4Test {

    @Test
    void createUndoCopyReturnsCreatedIndex() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            int first = GameUndoService.createUndoCopy(harness.getGameName());
            int second = GameUndoService.createUndoCopy(harness.getGameName());

            assertThat(first).isEqualTo(1);
            assertThat(second).isEqualTo(2);
            assertThat(Files.exists(harness.buildUndoPath(1))).isTrue();
            assertThat(Files.exists(harness.buildUndoPath(2))).isTrue();
        }
    }

    @Test
    void fowUndoDeletesSavesAboveRestoredIndex() throws IOException {
        try (var harness = TestGameHarness.forDefaultMap()) {
            writeSaves(harness, 5);

            assertThat(GameUndoService.undo(harness.load(), 2)).isNotNull();

            assertThat(latestCommand(harness)).isEqualTo("save 2");
            assertThat(GameUndoNameService.getSortedUndoNumbers(harness.getGameName()))
                    .containsExactly(1, 2);
        }
    }

    @Test
    void fowButtonUndoAfterIndexedUndoReturnsToSaveBeforeLatestAction() throws IOException {
        try (var harness = TestGameHarness.forDefaultMap()) {
            writeSaves(harness, 5);
            GameUndoService.undo(harness.load(), 2);

            // A new action after the undo: the next save must follow save 2, not the undone save 5.
            writeGameFile(harness, "action after undo");
            assertThat(GameUndoService.createUndoCopy(harness.getGameName())).isEqualTo(3);

            assertThat(GameUndoService.undo(harness.load())).isNotNull();
            assertThat(latestCommand(harness)).isEqualTo("save 2");
        }
    }

    // Writes FoW game saves 1..count, each tagged with "save N" as its latest_command so the restored
    // file can be identified after an undo.
    private static void writeSaves(TestGameHarness harness, int count) throws IOException {
        for (int i : IntStream.rangeClosed(1, count).toArray()) {
            writeGameFile(harness, "save " + i);
            assertThat(GameUndoService.createUndoCopy(harness.getGameName())).isEqualTo(i);
        }
    }

    private static void writeGameFile(TestGameHarness harness, String latestCommand) throws IOException {
        Path gamePath = gamePath(harness);
        List<String> lines = Files.readAllLines(gamePath).stream()
                .map(line -> line.startsWith(Constants.FOW_MODE + " ") ? Constants.FOW_MODE + " true" : line)
                .map(line -> line.startsWith("latest_command ") ? "latest_command " + latestCommand : line)
                .toList();
        Files.write(gamePath, lines);
    }

    private static String latestCommand(TestGameHarness harness) throws IOException {
        return Files.readAllLines(gamePath(harness)).stream()
                .filter(line -> line.startsWith("latest_command "))
                .findFirst()
                .map(line -> line.substring("latest_command ".length()))
                .orElseThrow();
    }

    private static Path gamePath(TestGameHarness harness) {
        return Storage.getGamePath(harness.getGameName() + Constants.TXT);
    }
}
