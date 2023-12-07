package ti4.generator;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

public class MapperTest {
    
    @Test
    public void testMapperInit() {
        assertDoesNotThrow(Mapper::loadData, "Mapper failed to load data. This will prevent the bot from loading altogether and must be fixed.");
    }
}
