package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;

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
}
