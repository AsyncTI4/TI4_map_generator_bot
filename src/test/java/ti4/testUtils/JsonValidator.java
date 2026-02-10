package ti4.testUtils;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import ti4.helpers.Units;
import ti4.json.UnitKeyMapKeyDeserializer;
import ti4.json.UnitKeyMapKeySerializer;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

public final class JsonValidator {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder()
            .addModule(new SimpleModule()
                    .addKeySerializer(Units.UnitKey.class, new UnitKeyMapKeySerializer())
                    .addKeyDeserializer(Units.UnitKey.class, new UnitKeyMapKeyDeserializer()))
            .findAndAddModules()
            .build();

    private JsonValidator() {}

    public static <T> T jsonCycleObject(T obj, Class<T> clazz) {
        String json = JSON_MAPPER.writeValueAsString(obj);
        try {
            T restored = JSON_MAPPER.readValue(json, clazz);
            assertNotNull(restored, "Deserialization returned null for: " + clazz.getSimpleName());
            return restored;
        } catch (Exception e) {
            throw new RuntimeException(String.format("Cycle fail [%s].%nJSON:%n%s", clazz.getSimpleName(), json), e);
        }
    }

    public static void assertAvailableJsonAttributes(Object obj, Set<String> expectedAttributes) {
        JsonNode node = JSON_MAPPER.valueToTree(obj);
        Set<String> actualAttributes = new HashSet<>(node.propertyNames());

        Set<String> unexpected = actualAttributes.stream()
                .filter(a -> !expectedAttributes.contains(a))
                .collect(Collectors.toSet());

        Set<String> missing = expectedAttributes.stream()
                .filter(a -> !actualAttributes.contains(a))
                .collect(Collectors.toSet());

        if (!unexpected.isEmpty() || !missing.isEmpty()) {
            fail(String.format("JSON Schema Mismatch!%nUnexpected: %s%nMissing: %s", unexpected, missing));
        }
    }

    public static void assertIsJacksonSerializable(Class<?> clazz) {
        try {
            JavaType type = JSON_MAPPER.constructType(clazz);

            // 1. Check Deserialization (Respects @JsonCreator and Constructors)
            var deserContext = JSON_MAPPER._deserializationContext();
            var deserializer = deserContext.findRootValueDeserializer(type);

            if (deserializer == null) {
                fail("Jackson 3 cannot find a Deserializer (no-args or @JsonCreator) for: " + clazz.getName());
            }

            // 2. Check Serialization
            var serProvider = JSON_MAPPER._serializationContext();
            var serializer = serProvider.findTypedValueSerializer(type, true);

            if (serializer == null) {
                fail("Jackson cannot find a Serializer for: " + clazz.getName());
            }
        } catch (Exception e) {
            fail("Jackson validation failed for " + clazz.getName() + " Error: " + e.getMessage());
        }
    }
}
