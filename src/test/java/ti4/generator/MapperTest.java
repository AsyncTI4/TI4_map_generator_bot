package ti4.generator;

import org.junit.jupiter.api.Test;
import ti4.testUtils.BaseTi4Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class MapperTest extends BaseTi4Test {

    @Test
    public void testMapperInit() {
        assertDoesNotThrow(Mapper::loadData, "Mapper failed to load data. This will prevent the bot from loading altogether and must be fixed.");
    }
}
