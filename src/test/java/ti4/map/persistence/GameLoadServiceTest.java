package ti4.map.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import ti4.map.Game;
import ti4.testUtils.BaseTi4Test;

class GameLoadServiceTest extends BaseTi4Test {

    @Test
    void shouldLoadGameFromFile() throws Exception {
        Game game = GameTestHelper.loadGame();
        assertThat(game).isNotNull();
        assertThat(game.getName()).isEqualTo("pbd10972");
    }
}
