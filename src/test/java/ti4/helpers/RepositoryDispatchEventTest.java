package ti4.helpers;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class RepositoryDispatchEventTest {

    @Test
    void testPayloadIsValidWithEntries() {
        RespositoryDispatchClientPayload payload = new RespositoryDispatchClientPayload(
            Map.of("chronicles_thread", "111", "bot_thread", "222"));
        assertTrue(payload.isValid());
    }

    @Test
    void testPayloadIsInvalidWhenEmpty() {
        RespositoryDispatchClientPayload payload = new RespositoryDispatchClientPayload(Map.of());
        assertFalse(payload.isValid());
    }

    @Test
    void testPayloadToJsonContainsVideoGenerationKeys() {
        RespositoryDispatchClientPayload payload = new RespositoryDispatchClientPayload(
            Map.of("chronicles_thread", "111", "bot_thread", "222"));
        String json = payload.toJson();
        assertTrue(json.contains("\"chronicles_thread\""));
        assertTrue(json.contains("\"bot_thread\""));
        assertTrue(json.contains("\"111\""));
        assertTrue(json.contains("\"222\""));
    }

    @Test
    void testGenerateVideoDoesNotThrow() {
        // sendEvent() is a no-op when REPO_DISPATCH_TOKEN is not set (as in test environments)
        assertDoesNotThrow(() -> RepositoryDispatchEvent.generateVideo("111", "222"));
    }
}
