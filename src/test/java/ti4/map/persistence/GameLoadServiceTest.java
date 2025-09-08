package ti4.map.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.map.Game;
import ti4.testUtils.BaseTi4Test;

class GameLoadServiceTest extends BaseTi4Test {

    private static final String GAME_NAME = "pbd10972";

    @Test
    void shouldLoadGameFromFile() {
        Game game = GameLoadService.load(GAME_NAME);

        assertThat(game).isNotNull();
        assertThat(game.getName()).isEqualTo("pbd10972");
    }
}
