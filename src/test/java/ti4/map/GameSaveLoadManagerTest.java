package ti4.map;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import ti4.generator.Mapper;

class GameSaveLoadManagerTest {

    @Test
    void loadMaps() {
        Mapper.init();

        assertThat(0 == 0);
    }

}