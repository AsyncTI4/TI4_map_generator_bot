package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import org.junit.jupiter.api.Test;
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

    // A stale autocomplete selection (e.g. another undo already deleted the file) used to throw
    // NoSuchFileException mid-undo; it should now fail cleanly and leave the game file untouched.
    @Test
    void undoToDeletedSaveReturnsNullWithoutTouchingGameFile() throws Exception {
        try (var harness = TestGameHarness.forDefaultMap()) {
            String gameName = harness.getGameName();
            GameUndoService.createUndoCopy(gameName);
            GameUndoService.createUndoCopy(gameName);
            GameUndoService.createUndoCopy(gameName);
            Files.delete(harness.buildUndoPath(2));
            var gameFile = Storage.getGamePath(gameName + Constants.TXT);
            byte[] before = Files.readAllBytes(gameFile);

            assertThat(GameUndoService.undo(harness.load(), 1)).isNull();
            assertThat(Files.readAllBytes(gameFile)).isEqualTo(before);
            assertThat(Files.exists(harness.buildUndoPath(3))).isTrue();
        }
    }

    @Test
    void undoToSaveAtOrBeyondLatestReturnsNull() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            String gameName = harness.getGameName();
            GameUndoService.createUndoCopy(gameName);
            GameUndoService.createUndoCopy(gameName);

            assertThat(GameUndoService.undo(harness.load(), 2)).isNull();
            assertThat(GameUndoService.undo(harness.load(), 5)).isNull();
        }
    }
}
