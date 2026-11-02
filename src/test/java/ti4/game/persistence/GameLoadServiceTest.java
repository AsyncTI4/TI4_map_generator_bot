package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.testUtils.BaseTi4Test;

class GameLoadServiceTest extends BaseTi4Test {

    @Test
    void shouldLoadGameFromFile() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();

            assertThat(game).isNotNull();
            assertThat(game.getName()).isEqualTo(harness.getGameName());
        }
    }

    @Test
    void readableGameFileIsLoaded() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            assertThat(GameLoadService.tryLoad(harness.getGameName()))
                    .isInstanceOfSatisfying(
                            GameLoadResult.Loaded.class,
                            loaded -> assertThat(loaded.game().getName()).isEqualTo(harness.getGameName()));
        }
    }

    @Test
    void absentGameFileIsMissing() {
        assertThat(GameLoadService.tryLoad("missing-" + UUID.randomUUID())).isInstanceOf(GameLoadResult.Missing.class);
    }

    @Test
    void unparseableGameFileIsCorruptAndRemembersWhichVersionFailed() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            harness.corruptGameFile();

            assertThat(GameLoadService.tryLoad(harness.getGameName()))
                    .isInstanceOfSatisfying(GameLoadResult.Corrupt.class, corrupt -> {
                        assertThat(corrupt.fileLastModified())
                                .isEqualTo(harness.getGameFilePath().toFile().lastModified());
                        assertThat(corrupt.cause()).isNotNull();
                    });
            // Callers that only need a Game still get null, as before.
            assertThat(GameLoadService.load(harness.getGameName())).isNull();
        }
    }
}
