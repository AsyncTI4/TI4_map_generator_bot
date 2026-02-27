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
            Map.of("map_id", "pbd1234", "thread_id", "111", "post_to_thread_id", "222"));
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
            Map.of("map_id", "pbd1234", "thread_id", "111", "post_to_thread_id", "222"));
        String json = payload.toJson();
        assertTrue(json.contains("\"map_id\""));
        assertTrue(json.contains("\"thread_id\""));
        assertTrue(json.contains("\"post_to_thread_id\""));
        assertTrue(json.contains("\"pbd1234\""));
        assertTrue(json.contains("\"111\""));
        assertTrue(json.contains("\"222\""));
    }

    @Test
    void testGenerateVideoDoesNotThrow() {
        // sendEvent() is a no-op when REPO_DISPATCH_TOKEN is not set (as in test environments)
        assertDoesNotThrow(() -> RepositoryDispatchEvent.generateVideo("pbd1234", "111", "222"));
    }
}
