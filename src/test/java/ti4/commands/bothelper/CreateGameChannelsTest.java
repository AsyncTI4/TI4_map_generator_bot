package ti4.commands.bothelper;

import org.junit.jupiter.api.Test;
import ti4.testUtils.BaseTi4Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CreateGameChannelsTest extends BaseTi4Test {
    @Test
    public void testGetCategoryNameForGame() {
        // // Tests when hardcoded to 25 games per category
        // assertEquals("PBD #76-100", CreateGameChannels.getCategoryNameForGame("pbd99"));
        // assertEquals("PBD #76-100", CreateGameChannels.getCategoryNameForGame("pbd100"));
        // assertEquals("PBD #101-125", CreateGameChannels.getCategoryNameForGame("pbd101"));
        // assertEquals("PBD #101-125", CreateGameChannels.getCategoryNameForGame("pbd115"));
        // assertEquals("PBD #101-125", CreateGameChannels.getCategoryNameForGame("pbd124"));
        // assertEquals("PBD #101-125", CreateGameChannels.getCategoryNameForGame("pbd125"));
        // assertEquals("PBD #976-1000", CreateGameChannels.getCategoryNameForGame("pbd999"));
        // assertEquals("PBD #976-1000", CreateGameChannels.getCategoryNameForGame("pbd1000"));
        // assertEquals("PBD #1001-1025", CreateGameChannels.getCategoryNameForGame("pbd1001"));

        // Tests when hardcoded to 10 games per category
        assertEquals("PBD #90-99", CreateGameChannels.getCategoryNameForGame("pbd99"));
        assertEquals("PBD #100-109", CreateGameChannels.getCategoryNameForGame("pbd100"));
        assertEquals("PBD #100-109", CreateGameChannels.getCategoryNameForGame("pbd101"));
        assertEquals("PBD #110-119", CreateGameChannels.getCategoryNameForGame("pbd115"));
        assertEquals("PBD #120-129", CreateGameChannels.getCategoryNameForGame("pbd124"));
        assertEquals("PBD #120-129", CreateGameChannels.getCategoryNameForGame("pbd125"));
        assertEquals("PBD #990-999", CreateGameChannels.getCategoryNameForGame("pbd999"));
        assertEquals("PBD #1000-1009", CreateGameChannels.getCategoryNameForGame("pbd1000"));
        assertEquals("PBD #1000-1009", CreateGameChannels.getCategoryNameForGame("pbd1001"));
        assertEquals("PBD #1010-1019", CreateGameChannels.getCategoryNameForGame("pbd1010"));
        assertEquals("PBD #1230-1239", CreateGameChannels.getCategoryNameForGame("pbd1234"));
        assertEquals("PBD #9990-9999", CreateGameChannels.getCategoryNameForGame("pbd9999"));
        assertEquals("PBD #10000-10009", CreateGameChannels.getCategoryNameForGame("pbd10000"));
        assertEquals("PBD #10000-10009", CreateGameChannels.getCategoryNameForGame("pbd10001"));

        // Tests when hardcoded to 5 games per category
        // assertEquals("PBD #95-99", CreateGameChannels.getCategoryNameForGame("pbd99"));
        // assertEquals("PBD #100-104", CreateGameChannels.getCategoryNameForGame("pbd100"));
        // assertEquals("PBD #100-104", CreateGameChannels.getCategoryNameForGame("pbd101"));
        // assertEquals("PBD #115-119", CreateGameChannels.getCategoryNameForGame("pbd115"));
        // assertEquals("PBD #120-124", CreateGameChannels.getCategoryNameForGame("pbd124"));
        // assertEquals("PBD #125-129", CreateGameChannels.getCategoryNameForGame("pbd125"));
        // assertEquals("PBD #995-999", CreateGameChannels.getCategoryNameForGame("pbd999"));
        // assertEquals("PBD #1000-1004", CreateGameChannels.getCategoryNameForGame("pbd1000"));
        // assertEquals("PBD #1000-1004", CreateGameChannels.getCategoryNameForGame("pbd1001"));
        // assertEquals("PBD #1010-1014", CreateGameChannels.getCategoryNameForGame("pbd1010"));
        // assertEquals("PBD #1230-1234", CreateGameChannels.getCategoryNameForGame("pbd1234"));
        // assertEquals("PBD #1235-1239", CreateGameChannels.getCategoryNameForGame("pbd1235"));
        // assertEquals("PBD #9995-9999", CreateGameChannels.getCategoryNameForGame("pbd9999"));
        // assertEquals("PBD #10000-10004", CreateGameChannels.getCategoryNameForGame("pbd10000"));
        // assertEquals("PBD #10000-10004", CreateGameChannels.getCategoryNameForGame("pbd10001"));
    }
}
