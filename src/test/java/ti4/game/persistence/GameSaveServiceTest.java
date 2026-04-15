package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.testUtils.BaseTi4Test;

class GameSaveServiceTest extends BaseTi4Test {

    @Test
    void shouldSaveAndReloadGame() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            game.setLatestOutcomeVotedFor("testOutcome");

            boolean saved = GameSaveService.save(game, "test");
            assertThat(saved).isTrue();

            Game reloaded = harness.load();
            assertThat(reloaded).isNotNull();
            assertThat(reloaded.getLatestOutcomeVotedFor()).isEqualTo("testOutcome");
        }
    }
}
