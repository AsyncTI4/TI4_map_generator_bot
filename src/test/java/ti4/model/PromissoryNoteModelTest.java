package ti4.model;

import org.junit.jupiter.api.Test;
import ti4.generator.Mapper;
import ti4.testUtils.BaseTi4Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class PromissoryNoteModelTest extends BaseTi4Test {
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
        System.out.println("PN **" + pnModel.getAlias() + "** failed validation due to invalid HomebrewReplacesID: `" + pnModel.getHomebrewReplacesID().get() + "`");
        return false;
    }
}
