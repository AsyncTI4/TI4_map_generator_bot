package ti4.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Map.Entry;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class LeaderTest {
    private ObjectMapper objectMapper = new ObjectMapper();

    private final String expectedId = "testId";
    private final  String expectedType = "testType";
    private final int expectedTgCount = 1;
    private final boolean expectedExhausted = false;
    private final boolean expectedLocked = true;
    private final boolean expectedActive = false;

    
    @Test
    public void testLeaderHasNoUnexpectedProperties() throws Exception {
        // Given        
        Leader leader = new Leader(expectedId, expectedType, expectedTgCount, expectedExhausted, expectedLocked, expectedActive);
        Set<String> knownJsonAttributes = new HashSet<>(Arrays.asList(
            "id",
            "type",
            "tgCount",
            "exhausted",
            "locked",
            "active"
        ));

        // When
        JsonNode json = objectMapper.valueToTree(leader);

        // Then
        Iterator<Entry<String, JsonNode>> fields = json.fields();
        while (fields.hasNext()) {
            Entry<String, JsonNode> field = fields.next();
            if (!knownJsonAttributes.remove(field.getKey())) {
                throw new Exception("Untested JSON property found in class. Please update tests to validate this new field is JSON safe. Field: " + field.getKey());
            }
        }

        assertEquals(0, knownJsonAttributes.size(), "JSON field was expected to be seen on object but was never observed");
    }

    @Test
    public void testLeaderIsJacksonSerializable() {
        assertTrue(objectMapper.canSerialize(Leader.class), "Jackson doesn't think it can serialize this class");
    }

    @Test
    public void testLeaderJsonSaveAndRestore() throws JsonProcessingException {
        // Given        
        Leader leader = new Leader(expectedId, expectedType, expectedTgCount, expectedExhausted, expectedLocked, expectedActive);

        // When
        String json = objectMapper.writeValueAsString(leader);
        Leader restoredLeader = objectMapper.readValue(json, Leader.class);

        // Then
        assertEquals(expectedId, restoredLeader.getId());
        assertEquals(expectedType, restoredLeader.getType());
        assertEquals(expectedTgCount, restoredLeader.getTgCount());
        assertEquals(expectedExhausted, restoredLeader.isExhausted());
        assertEquals(expectedLocked, restoredLeader.isLocked());
        assertEquals(expectedActive, restoredLeader.isActive());
    }
}
