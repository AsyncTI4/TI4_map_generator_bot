package ti4.model;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import ti4.generator.Mapper;
import ti4.generator.PositionMapper;
import ti4.generator.TileHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Storage;
import ti4.message.BotLogger;

public class PromissoryNoteModelTest {

    @BeforeAll
    public static void init() {
        TileHelper.init();
        PositionMapper.init();
        Mapper.init();
        AliasHandler.init();
        Storage.init();
    }

    @Test
    public void testPromissoryNotes() {
        for (PromissoryNoteModel pnModel : Mapper.getPromissoryNotes().values()) {
            assertTrue(pnModel.isValid());
            assertTrue(validateHomebrewReplacesID(pnModel), pnModel.getAlias() + ": invalid HomebrewReplacesID");
        }
    }

    private boolean validateHomebrewReplacesID(PromissoryNoteModel pnModel) {
        if (pnModel.getHomebrewReplacesID().isEmpty()) return true;
        if (Mapper.isValidPromissoryNote(pnModel.getHomebrewReplacesID().get())) return true;
        BotLogger.log("PN **" + pnModel.getAlias() + "** failed validation due to invalid HomebrewReplacesID: `" + pnModel.getHomebrewReplacesID().get() + "`");
        return false;
    }
}
