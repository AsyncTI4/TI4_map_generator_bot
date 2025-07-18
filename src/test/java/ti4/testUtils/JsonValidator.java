package ti4.testUtils;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ti4.json.ObjectMapperFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Utility test class that allows us to validate our source files are correctly configured
 * for JSON save/restore.
 */
public final class JsonValidator<T> {
    private static final ObjectMapper objectMapper = ObjectMapperFactory.build();

    private JsonValidator() {
    }

    /**
     * Subjects the provided object to a save/restore JSON loop where we serialize the object to
     * a JSON string and then re-consistue it. Allows tests to confirm no data is lost in this
     * process.
     */
    public static <T> T jsonCycleObject(T obj, Class<T> clazz) throws JsonProcessingException {
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            System.err.println("JSON which failed to be restored:");
            System.err.println(json);
            throw e;
        }
    }

    /**
     * Scans an object and confirms we only see attributes in the JSON output that we expect to see.
     *
     * Any any missing attributes or unknown attributes will cause an exception to be thrown.
     */
    public static void assertAvailableJsonAttributes(Object obj, Set<String> knownJsonAttributes) throws JacksonConfigurationException {
        JsonNode json = objectMapper.valueToTree(obj);

        // Validate all fields we expect to be present in JSON output are accounted for with no extras.
        Iterator<Entry<String, JsonNode>> fields = json.fields();
        while (fields.hasNext()) {
            Entry<String, JsonNode> field = fields.next();
            if (!knownJsonAttributes.remove(field.getKey())) {
                throw new JacksonConfigurationException("Untested JSON property found in class. Please update tests to validate this new field is JSON safe. Field: " + field.getKey());
            }
        }

        assertEquals(0, knownJsonAttributes.size(), "JSON field was expected to be seen on object but was never observed");
    }

    /**
     * Runs any tests we can on the entire class to determine if Jackson things this class can be successfully serialized.
     */
    public static void assertIsJacksonSerializable(Class<?> clazz) {
        assertTrue(objectMapper.canSerialize(clazz), "Jackson doesn't think it can serialize this class");
    }
}
