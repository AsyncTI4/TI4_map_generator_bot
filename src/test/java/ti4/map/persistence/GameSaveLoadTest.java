package ti4.map.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.map.Game;
import ti4.testUtils.BaseTi4Test;

class GameSaveLoadTest extends BaseTi4Test {

    @Test
    void shouldHandleSerializationOfBorderAnomalies() {
        // save game as new file
        Game game = GameLoadService.load("game-with-border-anomalies");
        game.setName("game-with-border-anomalies-test-save");
        GameSaveService.save(game, "test");

        // reload game, and that new file we just created
        game = GameLoadService.load("game-with-border-anomalies");
        Game game2 = GameLoadService.load("game-with-border-anomalies-test-save");

        assertThat(game2)
                .usingRecursiveComparison()
                .ignoringFields(
                        "lastModifiedDate", "draftManager.game.lastModifiedDate", "name", "draftManager.game.name")
                .isEqualTo(game);

        assertThat(game.getLastModifiedDate()).isNotEqualTo(game2.getLastModifiedDate());
        assertThat(game.getName()).isNotEqualTo(game2.getName());
    }

    @Test
    void shouldHandleSerializationOfDisplacedUnits() {
        Game game = GameLoadService.load("game-with-displaced-units");
        game.setName("game-with-displaced-units-test-save");
        GameSaveService.save(game, "test");

        // reload game, and that new file we just created
        game = GameLoadService.load("game-with-displaced-units");
        Game game2 = GameLoadService.load("game-with-displaced-units-test-save");

        assertThat(game2)
                .usingRecursiveComparison()
                .ignoringFields(
                        "latestCommand",
                        "draftManager.game.latestCommand",
                        "lastModifiedDate",
                        "draftManager.game.lastModifiedDate",
                        "name",
                        "draftManager.game.name")
                .isEqualTo(game);

        assertThat(game.getLatestCommand()).isNotEqualTo(game2.getLatestCommand());
        assertThat(game.getLastModifiedDate()).isNotEqualTo(game2.getLastModifiedDate());
        assertThat(game.getName()).isNotEqualTo(game2.getName());
    }
}
