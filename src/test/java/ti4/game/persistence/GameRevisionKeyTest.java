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

class GameRevisionKeyTest extends BaseTi4Test {
    @Test
    void successfulSavesHaveDistinctRevisionsThatSurviveReloadAndUndo() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            var game = harness.load();
            assertThat(game.getRevisionKey()).isNull();
            assertThat(GameSaveService.save(game, "first")).isTrue();
            String first = game.getRevisionKey();
            assertThat(first).isNotBlank();
            assertThat(harness.load().getRevisionKey()).isEqualTo(first);
            assertThat(GameSaveService.save(game, "second")).isTrue();
            assertThat(game.getRevisionKey()).isNotEqualTo(first);
            game.setFowMode(true);
            var restored = GameUndoService.undo(game, 1);
            assertThat(restored).isNotNull();
            assertThat(restored.getRevisionKey()).isEqualTo(first);
            assertThat(GameSaveService.save(restored, "after undo")).isTrue();
            assertThat(restored.getRevisionKey()).isNotEqualTo(first);
        }
    }

    @Test
    void loadsLegacyRevisionKey() throws IOException {
        try (var harness = TestGameHarness.forDefaultMap()) {
            var game = harness.load();
            assertThat(GameSaveService.save(game, "saved revision")).isTrue();
            var path = Storage.getGamePath(game.getName() + Constants.TXT);
            Files.writeString(path, Files.readString(path).replace("revision_key ", "persistence_revision "));
            assertThat(harness.load().getRevisionKey()).isEqualTo(game.getRevisionKey());
        }
    }

    @Test
    void failedSavePreservesPreviousRevisionAndFile() throws IOException {
        try (var harness = TestGameHarness.forDefaultMap()) {
            var game = harness.load();
            assertThat(GameSaveService.save(game, "first")).isTrue();
            String first = game.getRevisionKey();
            var path = Storage.getGamePath(game.getName() + Constants.TXT);
            byte[] original = Files.readAllBytes(path);
            try (MockedStatic<PosixFileSystemUtility> permissions = mockStatic(PosixFileSystemUtility.class)) {
                permissions
                        .when(() -> PosixFileSystemUtility.setPermissionsIfPosix(any()))
                        .thenThrow(new IllegalStateException("simulated save failure"));
                assertThat(GameSaveService.save(game, "failed")).isFalse();
            }
            assertThat(game.getRevisionKey()).isEqualTo(first);
            assertThat(Files.readAllBytes(path)).isEqualTo(original);
        }
    }
}
