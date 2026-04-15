package ti4.service.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import ti4.testUtils.BaseTi4Test;

class RecreateGameServiceTest extends BaseTi4Test {

    @Test
    void sourceGameNameStripsTestSuffix() {
        assertEquals("pbd15036", RecreateGameService.getSourceGameName("pbd15036::test::abc"));
        assertEquals("pbd15036", RecreateGameService.getSourceGameName("pbd15036"));
    }

    @Test
    void testGameDetectionMatchesMarker() {
        assertTrue(RecreateGameService.isTestGame("pbd15036::test::abc"));
        assertFalse(RecreateGameService.isTestGame("pbd15036"));
    }

    @Test
    void sanitizedGameChannelPrefixRemovesUnsupportedCharacters() {
        assertEquals(
                "pbd15036-test-123e4567-e89b-12d3-a456-426614174000",
                RecreateGameService.getSanitizedGameChannelPrefix(
                        "pbd15036::test::123e4567-e89b-12d3-a456-426614174000"));
    }
}
