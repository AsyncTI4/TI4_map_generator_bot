package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.game.Game;
import ti4.logging.BotLogger;
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
    void absentGameFileLoadsAsNull() {
        assertThat(GameLoadService.load("missing-" + UUID.randomUUID())).isNull();
    }

    @Test
    void unparseableGameFileLoadsAsNullAndIsReportedOnce() {
        try (var harness = TestGameHarness.forDefaultMap();
                MockedStatic<BotLogger> botLogger = mockStatic(BotLogger.class)) {
            harness.corruptGameFile();

            assertThat(GameLoadService.load(harness.getGameName())).isNull();
            assertThat(GameLoadService.load(harness.getGameName())).isNull();

            botLogger.verify(() -> BotLogger.error(contains(harness.getGameName()), any(Throwable.class)), times(1));
        }
    }
}
