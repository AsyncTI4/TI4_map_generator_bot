package ti4.message;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import ti4.json.JsonMapperManager;

class GameMessageTypeTest {

    @Test
    void testLegacyStatusEndDeserializes() {
        GameMessageType restored =
                JsonMapperManager.basic().readValue("\"STATUS_END\"", GameMessageType.class);

        assertEquals(GameMessageType.STATUS_END, restored);
    }
}
