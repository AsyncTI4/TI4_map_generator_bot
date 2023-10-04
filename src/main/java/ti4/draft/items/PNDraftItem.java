package ti4.draft.items;

import ti4.draft.DraftItem;
import ti4.generator.Mapper;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.model.FactionModel;
import ti4.model.PromissoryNoteModel;

public class PNDraftItem extends DraftItem {
    public PNDraftItem(String itemId) {
        super(Category.PN, itemId);
    }

    private PromissoryNoteModel getPn() {
        FactionModel faction = Mapper.getFactionSetup(ItemId);
        return Mapper.getPromissoryNoteByID(faction.getPromissoryNotes().get(0));
    }
    @Override
    public String getShortDescription() {
        PromissoryNoteModel pn = getPn();
        return "Promissory Note - " + pn.getName();
    }

    @Override
    public String getLongDescription() {
        PromissoryNoteModel pn = getPn();
        return pn.getText();
    }

    @Override
    public String getItemEmoji() {
        return Emojis.PN;
    }
}
