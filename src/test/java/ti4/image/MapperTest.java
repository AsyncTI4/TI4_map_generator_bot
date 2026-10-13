package ti4.image;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import ti4.testUtils.BaseTi4Test;

class MapperTest extends BaseTi4Test {

    @Test
    void testMapperInit() {
        assertDoesNotThrow(
                Mapper::loadData,
                "Mapper failed to load data. This will prevent the bot from loading altogether and must be fixed.");
    }

    @Test
    void dynamicDecksUseEnabledReplacementsAndExcludeDisabledExplores() {
        var blueReverieTechs = Mapper.getDeck(Mapper.getDynamicTechnologyDeckID(false, false, false, true));
        assertTrue(blueReverieTechs.getNewDeck().contains("gd_br"));
        assertFalse(blueReverieTechs.getNewDeck().contains("gd"));

        var absolBlueReverieTechs = Mapper.getDeck(Mapper.getDynamicTechnologyDeckID(false, false, true, true));
        assertTrue(absolBlueReverieTechs.getNewDeck().contains("absol_gd"));
        assertFalse(absolBlueReverieTechs.getNewDeck().contains("gd_br"));
        assertFalse(absolBlueReverieTechs.getNewDeck().contains("gd"));

        var explores = Mapper.getDeck(Mapper.getDynamicExploreDeckID(true, true));
        assertFalse(explores.getNewDeck().contains("warforgeruins"));
        assertFalse(explores.getNewDeck().contains("arcaneshieldemitter"));
    }
}
