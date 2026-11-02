package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.testUtils.BaseTi4Test;

class GamePersistenceRevisionTest extends BaseTi4Test {
    @Test
    void successfulSavesHaveDistinctRevisionsThatSurviveReloadAndUndo() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            var game = harness.load();
            assertThat(game.getPersistenceRevision()).isNull();
            assertThat(GameLoadService.loadRevision(game.getName())).isNull();
            assertThat(GameSaveService.save(game, "first")).isTrue();
            String first = game.getPersistenceRevision();
            assertThat(first).isNotBlank();
            assertThat(harness.load().getPersistenceRevision()).isEqualTo(first);
            assertThat(GameLoadService.loadRevision(game.getName())).isEqualTo(first);
            assertThat(GameSaveService.save(game, "second")).isTrue();
            assertThat(game.getPersistenceRevision()).isNotEqualTo(first);
            game.setFowMode(true);
            var restored = GameUndoService.undo(game, 1);
            assertThat(restored).isNotNull();
            assertThat(restored.getPersistenceRevision()).isEqualTo(first);
            assertThat(GameLoadService.loadRevision(game.getName())).isEqualTo(first);
            assertThat(GameSaveService.save(restored, "after undo")).isTrue();
            assertThat(restored.getPersistenceRevision()).isNotEqualTo(first);
        }
    }

    @Test
    void failedSavePreservesPreviousRevisionAndFile() throws IOException {
        try (var harness = TestGameHarness.forDefaultMap()) {
            var game = harness.load();
            assertThat(GameSaveService.save(game, "first")).isTrue();
            String first = game.getPersistenceRevision();
            var path = Storage.getGamePath(game.getName() + Constants.TXT);
            byte[] original = Files.readAllBytes(path);
            try (MockedStatic<PosixFileSystemUtility> permissions = mockStatic(PosixFileSystemUtility.class)) {
                permissions
                        .when(() -> PosixFileSystemUtility.setPermissionsIfPosix(any()))
                        .thenThrow(new IllegalStateException("simulated save failure"));
                assertThat(GameSaveService.save(game, "failed")).isFalse();
            }
            assertThat(game.getPersistenceRevision()).isEqualTo(first);
            assertThat(Files.readAllBytes(path)).isEqualTo(original);
        }
    }
}
