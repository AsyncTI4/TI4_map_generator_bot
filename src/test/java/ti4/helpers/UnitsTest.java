package ti4.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.json.JsonMapperManager;
import ti4.json.UnitKeyMapKeyDeserializer;
import ti4.json.UnitKeyMapKeySerializer;
import ti4.testUtils.BaseTi4Test;
import ti4.testUtils.JsonValidator;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

class UnitsTest extends BaseTi4Test {

    private static final JsonMapper JSON_MAPPER = JsonMapperManager.basic()
            .rebuild()
            .addModule(new SimpleModule()
                    .addKeySerializer(Units.UnitKey.class, new UnitKeyMapKeySerializer())
                    .addKeyDeserializer(Units.UnitKey.class, new UnitKeyMapKeyDeserializer()))
            .build();

    @Nested
    class UnitKeyTest {
        private final UnitType expectedUnitType = UnitType.Carrier;
        private static final String expectedColorId = "blu";

        private UnitKey buildUnitKey() {
            return Units.getUnitKey(expectedUnitType, expectedColorId);
        }

        @Test
        void testUnitKeyHasNoUnexpectedProperties() {
            // Given
            UnitKey unitKey = buildUnitKey();
            // DO NOT ADD NEW JSON KEYS TO THIS OBJECT.
            // This object is being used as a key in maps which causes issues when we
            // try to convert the Java map to a JSON map (as maps only allow for string keys).
            Set<String> knownJsonAttributes = new HashSet<>(Arrays.asList("unitType", "colorID"));

            // When
            JsonValidator.assertAvailableJsonAttributes(unitKey, knownJsonAttributes);
        }

        @Test
        void testUnitKeyIsJacksonSerializable() {
            JsonValidator.assertIsJacksonSerializable(UnitKey.class);
        }

        @Test
        void testUnitKeyJsonSaveAndRestore() {
            // Given
            UnitKey unitKey = buildUnitKey();

            // When
            UnitKey restoredUnitKey = JsonValidator.jsonCycleObject(unitKey, UnitKey.class);

            // Then
            assertEquals(expectedColorId, restoredUnitKey.getColorID());
            assertEquals(expectedUnitType, restoredUnitKey.getUnitType());
        }

        @Test
        void testUnitTypeJacksonDeserializationSupportsLegacyEnumName() {
            UnitKey restoredUnitKey =
                    JSON_MAPPER.readValue("{\"unitType\":\"Cruiser\",\"colorID\":\"blu\"}", UnitKey.class);

            assertEquals(UnitType.Cruiser, restoredUnitKey.getUnitType());
        }

        @Test
        void testUnitTypeJacksonDeserializationRejectsUnknownValue() {
            assertThrows(
                    Exception.class,
                    () -> JSON_MAPPER.readValue(
                            "{\"unitType\":\"SomeUnknownShip\",\"colorID\":\"blu\"}", UnitKey.class));
        }
    }
}
