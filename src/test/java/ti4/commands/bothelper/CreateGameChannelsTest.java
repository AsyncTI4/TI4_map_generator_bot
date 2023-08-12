package ti4.commands.bothelper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class CreateGameChannelsTest {
    @Test
    void testGetCategoryNameForGame() {
        assertEquals("PBD #76-100", CreateGameChannels.getCategoryNameForGame("pbd99"));
        assertEquals("PBD #76-100", CreateGameChannels.getCategoryNameForGame("pbd100"));
        assertEquals("PBD #101-125", CreateGameChannels.getCategoryNameForGame("pbd101"));
        assertEquals("PBD #101-125", CreateGameChannels.getCategoryNameForGame("pbd115"));
        assertEquals("PBD #101-125", CreateGameChannels.getCategoryNameForGame("pbd124"));
        assertEquals("PBD #101-125", CreateGameChannels.getCategoryNameForGame("pbd125"));
        assertEquals("PBD #976-1000", CreateGameChannels.getCategoryNameForGame("pbd999"));
        assertEquals("PBD #976-1000", CreateGameChannels.getCategoryNameForGame("pbd1000"));
        assertEquals("PBD #1001-1025", CreateGameChannels.getCategoryNameForGame("pbd1001"));
    }
}
