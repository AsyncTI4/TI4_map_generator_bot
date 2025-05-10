package ti4.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;
import ti4.testUtils.JsonValidator;

public class UnitsTest extends BaseTi4Test {

    @Nested
    class UnitKeyTest {
        private final UnitType expectedUnitType = UnitType.Carrier;
        private final String expectedColorId = "blu";

        private UnitKey buildUnitKey() {
            return new UnitKey(expectedUnitType, expectedColorId);
        }

        @Test
        public void testUnitKeyHasNoUnexpectedProperties() throws Exception {
            // Given        
            UnitKey unitKey = buildUnitKey();
            // DO NOT ADD NEW JSON KEYS TO THIS OBJECT.
            // This object is being used as a key in maps which causes issues when we
            // try to conver the Java map to a JSON map (as maps only allow for string keys).
            Set<String> knownJsonAttributes = new HashSet<>(Arrays.asList(
                "unitType",
                "colorID"));

            // When
            JsonValidator.assertAvailableJsonAttributes(unitKey, knownJsonAttributes);
        }

        @Test
        public void testUnitKeyIsJacksonSerializable() {
            JsonValidator.assertIsJacksonSerializable(UnitKey.class);
        }

        @Test
        public void testUnitKeyJsonSaveAndRestore() throws JsonProcessingException {
            // Given        
            UnitKey unitKey = buildUnitKey();

            // When
            UnitKey restoredUnitKey = JsonValidator.jsonCycleObject(unitKey, UnitKey.class);

            // Then
            assertEquals(expectedColorId, restoredUnitKey.getColorID());
            assertEquals(expectedUnitType, restoredUnitKey.getUnitType());
        }
    }
}
