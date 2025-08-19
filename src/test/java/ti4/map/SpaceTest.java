package ti4.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.awt.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;
import ti4.testUtils.JsonValidator;

class SpaceTest extends BaseTi4Test {
    private static final String expectedName = "space";
    private final Point expectedHolderCenterPosition = new Point(1, 2);
    private final UnitType expectedUnitType = UnitType.Carrier;
    private static final String expectedColorID = "blu";
    private final UnitKey expectedUnitKey = Units.getUnitKey(expectedUnitType, expectedColorID);
    private static final int expectedUnitCount = 4;
    private static final int expectedUnitDamage = 1;
    private static final String expectedCommandCounter = "Random Command Counter";
    private static final String expectedControl = "Random Control";
    private static final String expectedToken = "token_frontier.png";

    private Space buildSpace() {
        Space space = new Space(expectedName, expectedHolderCenterPosition);

        space.addUnit(expectedUnitKey, expectedUnitCount);
        space.addDamagedUnit(expectedUnitKey, expectedUnitDamage);
        space.addCC(expectedCommandCounter);
        space.addControl(expectedControl);
        space.addToken(expectedToken);

        return space;
    }

    @Test
    void testSpaceHasNoUnexpectedProperties() throws Exception {
        // Given
        Space space = buildSpace();
        Set<String> knownJsonAttributes = new HashSet<>(Arrays.asList(
                "name", "javaClassType", "holderCenterPosition", "unitsByState", "ccList", "controlList", "tokenList"));

        // When
        JsonValidator.assertAvailableJsonAttributes(space, knownJsonAttributes);
    }

    @Test
    void testSpaceIsJacksonSerializable() {
        JsonValidator.assertIsJacksonSerializable(Space.class);
    }

    @Test
    void testSpaceJsonSaveAndRestore() throws JsonProcessingException {
        // Given
        Space space = buildSpace();

        // When
        Space restoredSpace = JsonValidator.jsonCycleObject(space, Space.class);

        // Then
        assertEquals(expectedName, restoredSpace.getName());
        assertEquals(expectedHolderCenterPosition, restoredSpace.getHolderCenterPosition());
        Set<UnitKey> restoredUnits = restoredSpace.getUnitKeys();
        assertEquals(1, restoredUnits.size());
        assertEquals(expectedUnitCount, restoredSpace.getUnitCount(expectedUnitKey));
        assertEquals(expectedUnitDamage, restoredSpace.getDamagedUnitCount(expectedUnitKey));
        Set<String> restoredCCList = restoredSpace.getCcList();
        assertEquals(1, restoredCCList.size());
        assertTrue(restoredCCList.contains(expectedCommandCounter));
        Set<String> restoredControlList = restoredSpace.getControlList();
        assertEquals(1, restoredControlList.size());
        assertTrue(restoredControlList.contains(expectedControl));
        Set<String> restoredTokenList = restoredSpace.getTokenList();
        assertEquals(1, restoredTokenList.size());
        assertTrue(restoredTokenList.contains(expectedToken));
    }
}
