package ti4.image.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;
import ti4.testUtils.BaseTi4Test;

class FontsTest extends BaseTi4Test {

    @Test
    void tiFontsHaveTheRequestedSizeAndAreReused() {
        assertEquals(32, Fonts.ti(32).getSize());
        assertEquals(12, Fonts.ti(12).getSize());
        assertSame(Fonts.ti(32), Fonts.ti(32));
    }

    @Test
    void emojiFontIsLoaded() {
        assertNotNull(Fonts.emoji());
        assertSame(Fonts.emoji(), Fonts.emoji());
    }
}
