package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalMatchers.and;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.logging.BotLogger;
import ti4.testUtils.BaseTi4Test;

class GameManagerCorruptGameTest extends BaseTi4Test {

    @Test
    void corruptGameIsReportedOncePerFileVersion() throws IOException {
        try (var harness = TestGameHarness.forDefaultMap();
                MockedStatic<BotLogger> botLogger = mockStatic(BotLogger.class)) {
            String gameName = harness.getGameName();
            harness.corruptGameFile();

            GameManager.get(gameName);
            GameManager.get(gameName);

            botLogger.verify(
                    () -> BotLogger.error(
                            and(contains(gameName), contains("/bothelper reload_game")), any(Throwable.class)),
                    times(1));

            // Any write to the file is a new version, so a file that is still corrupt is reported again.
            Path gameFile = harness.getGameFilePath();
            Files.setLastModifiedTime(
                    gameFile,
                    FileTime.fromMillis(Files.getLastModifiedTime(gameFile).toMillis() + 60_000));
            GameManager.get(gameName);

            botLogger.verify(() -> BotLogger.error(contains(gameName), any(Throwable.class)), times(2));
        }
    }

    @Test
    void onlyAnUnparseableGameFileIsCorrupt() {
        try (var healthy = TestGameHarness.forDefaultMap();
                var corrupt = TestGameHarness.forDefaultMap()) {
            corrupt.corruptGameFile();

            assertThat(GameManager.isCorrupt(healthy.getGameName())).isFalse();
            assertThat(GameManager.isCorrupt(corrupt.getGameName())).isTrue();
            // A game whose file is gone is missing, not corrupt.
            assertThat(GameManager.isCorrupt("missing-" + UUID.randomUUID())).isFalse();
        }
    }
}
