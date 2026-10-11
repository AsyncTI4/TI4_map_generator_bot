package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
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

    // Undoing to save 1 first copies save 2 over the game file (to read its saved buttons) and only then
    // copies save 1. With save 1 gone, the old code left the game file holding save 2 with no rollback.
    @Test
    void undoToMissingSaveLeavesGameFileUntouched() throws Exception {
        try (var harness = TestGameHarness.forDefaultMap()) {
            writeSaves(harness, 3);
            Files.delete(harness.buildUndoPath(1));
            byte[] before = Files.readAllBytes(gameFile(harness));

            assertThat(GameUndoService.undo(harness.load(), 1, null)).isNull();
            assertThat(Files.readAllBytes(gameFile(harness))).isEqualTo(before);
            assertThat(Files.exists(harness.buildUndoPath(3))).isTrue();
        }
    }

    @Test
    void undoToSaveAtOrBeyondLatestReturnsNull() throws Exception {
        try (var harness = TestGameHarness.forDefaultMap()) {
            writeSaves(harness, 2);
            byte[] before = Files.readAllBytes(gameFile(harness));

            assertThat(GameUndoService.undo(harness.load(), 2, null)).isNull();
            assertThat(GameUndoService.undo(harness.load(), 5, null)).isNull();
            assertThat(Files.readAllBytes(gameFile(harness))).isEqualTo(before);
        }
    }

    // Another undo deletes the saves above its target and later actions reuse those numbers, so a stale
    // autocomplete pick can name a save number that now holds a different state.
    @Test
    void undoRejectsSelectionWhoseSaveNumberWasReused() throws Exception {
        try (var harness = TestGameHarness.forDefaultMap()) {
            writeSaves(harness, 3);
            byte[] before = Files.readAllBytes(gameFile(harness));

            assertThat(GameUndoService.undo(harness.load(), 2, lastModifiedDateOf(3) + 1))
                    .isNull();
            assertThat(Files.readAllBytes(gameFile(harness))).isEqualTo(before);
            assertThat(Files.exists(harness.buildUndoPath(3))).isTrue();
        }
    }

    // The test game has no Discord channels, so posting to the undo-log thread throws after the rollback
    // has already happened. That must not turn a completed undo into a reported failure.
    @Test
    void undoReportsSuccessWhenPostUndoMessagingFails() throws Exception {
        try (var harness = TestGameHarness.forDefaultMap()) {
            writeSaves(harness, 3);

            Game undone = GameUndoService.undo(harness.load(), 1, lastModifiedDateOf(2));

            assertThat(undone).isNotNull();
            assertThat(undone.getLatestCommand()).isEqualTo("save-1");
            assertThat(Files.readString(gameFile(harness))).contains("latest_command save-1");
            assertThat(Files.exists(harness.buildUndoPath(1))).isTrue();
            assertThat(Files.exists(harness.buildUndoPath(2))).isFalse();
            assertThat(Files.exists(harness.buildUndoPath(3))).isFalse();
        }
    }

    // Writes saves 1..count with distinct latest commands and dates; the game file matches the latest save,
    // as it does after a real save.
    private static void writeSaves(TestGameHarness harness, int count) throws IOException {
        String template = Files.readString(gameFile(harness));
        for (int i = 1; i <= count; i++) {
            String save = template.replaceFirst("(?m)^latest_command .*$", "latest_command save-" + i)
                    .replaceFirst("(?m)^last_modified_date .*$", "last_modified_date " + lastModifiedDateOf(i));
            Files.writeString(harness.buildUndoPath(i), save);
            if (i == count) Files.writeString(gameFile(harness), save);
        }
    }

    private static long lastModifiedDateOf(int undoIndex) {
        return 1_700_000_000_000L + undoIndex;
    }

    private static Path gameFile(TestGameHarness harness) {
        return Storage.getGamePath(harness.getGameName() + Constants.TXT);
    }
}
