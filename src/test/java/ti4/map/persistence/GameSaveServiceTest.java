package ti4.map.persistence;

import org.junit.jupiter.api.Test;
import ti4.map.Game;
import ti4.testUtils.BaseTi4Test;

import static org.assertj.core.api.Assertions.assertThat;

class GameSaveServiceTest extends BaseTi4Test {

    @Test
    void shouldSaveAndReloadGame() {
        Game game = GameTestHelper.loadGame();
        game.setLatestOutcomeVotedFor("testOutcome");

        boolean saved = GameSaveService.save(game, "test");
        assertThat(saved).isTrue();

        Game reloaded = GameLoadService.load("pbd10972");
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getLatestOutcomeVotedFor()).isEqualTo("testOutcome");
    }
}
