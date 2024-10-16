package ti4.map;

import org.junit.jupiter.api.Test;
import ti4.generator.Mapper;

import static org.assertj.core.api.Assertions.assertThat;

class GameSaveLoadManagerTest {

    @Test
    void loadMaps() {
        Mapper.init();
        GameSaveLoadManager.loadMaps();

        assertThat(GameManager.getInstance().getGameNameToGame())
            .hasSize(4)
            .containsKey("pbd780")
            .containsKey("pbd845")
            .containsKey("pbd1408")
            .containsKey("pbd1415");
    }

}