package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import org.junit.jupiter.api.Test;
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
}
