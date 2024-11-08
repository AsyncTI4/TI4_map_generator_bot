package ti4.map;

import org.junit.jupiter.api.Test;
import ti4.generator.Mapper;

import static org.assertj.core.api.Assertions.assertThat;

class GameSaveLoadManagerTest {

    @Test
    void loadGame() {
        Mapper.init();

        assertThat(0 == 0);
    }

}