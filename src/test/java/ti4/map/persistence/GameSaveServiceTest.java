package ti4.map.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import ti4.map.Game;
import ti4.testUtils.BaseTi4Test;

class GameSaveServiceTest extends BaseTi4Test {

    @Test
    void shouldSaveAndReloadGame() throws Exception {
        Game game = GameTestHelper.loadGame();
        game.setLatestOutcomeVotedFor("testOutcome");

        boolean saved = GameSaveService.save(game, "test");
        assertThat(saved).isTrue();

        Game reloaded = GameLoadService.load("pbd10972");
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getLatestOutcomeVotedFor()).isEqualTo("testOutcome");
    }
}
