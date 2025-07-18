package ti4.map;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import ti4.testUtils.BaseTi4Test;
import ti4.testUtils.JsonValidator;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LeaderTest extends BaseTi4Test {
    private final String expectedId = "testId";
    private final String expectedType = "testType";
    private final int expectedTgCount = 1;
    private final boolean expectedExhausted = false;
    private final boolean expectedLocked = true;
    private final boolean expectedActive = false;

    private Leader buildLeader() {
        return new Leader(expectedId, expectedType, expectedTgCount, expectedExhausted, expectedLocked, expectedActive);
    }

    @Test
    public void testLeaderHasNoUnexpectedProperties() throws Exception {
        // Given
        Leader leader = buildLeader();
        Set<String> knownJsonAttributes = new HashSet<>(Arrays.asList(
            "id",
            "type",
            "tgCount",
            "exhausted",
            "locked",
            "active"));

        // When
        JsonValidator.assertAvailableJsonAttributes(leader, knownJsonAttributes);
    }

    @Test
    public void testLeaderIsJacksonSerializable() {
        JsonValidator.assertIsJacksonSerializable(Leader.class);
    }

    @Test
    public void testLeaderJsonSaveAndRestore() throws JsonProcessingException {
        // Given
        Leader leader = buildLeader();

        // When
        Leader restoredLeader = JsonValidator.jsonCycleObject(leader, Leader.class);

        // Then
        assertEquals(expectedId, restoredLeader.getId());
        assertEquals(expectedType, restoredLeader.getType());
        assertEquals(expectedTgCount, restoredLeader.getTgCount());
        assertEquals(expectedExhausted, restoredLeader.isExhausted());
        assertEquals(expectedLocked, restoredLeader.isLocked());
        assertEquals(expectedActive, restoredLeader.isActive());
    }
}
