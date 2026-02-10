package ti4.map.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.map.Game;
import ti4.testUtils.BaseTi4Test;

class GameSaveLoadTest extends BaseTi4Test {

    @Test
    void shouldHandleSerializationOfBorderAnomalies() {
        Game game = GameLoadService.load("game-with-border-anomalies");
        game.setName("game-with-border-anomalies-test-save");
        GameSaveService.save(game, "test");
        Game game2 = GameLoadService.load("game-with-border-anomalies-test-save");

        assertThat(game2).usingRecursiveComparison().isEqualTo(game);
    }

    @Test
    void shouldHandleSerializationOfDisplacedUnits() {
        Game game = GameLoadService.load("game-with-displaced-units");
        game.setName("game-with-displaced-units-test-save");
        GameSaveService.save(game, "test");
        Game game2 = GameLoadService.load("game-with-displaced-units-test-save");

        assertThat(game2).usingRecursiveComparison().isEqualTo(game);
    }
}
