package ti4.draft.items;

import ti4.draft.DraftItem;
import ti4.generator.Mapper;
import ti4.helpers.Emojis;
import ti4.model.FactionModel;
import ti4.model.PromissoryNoteModel;

public class PNDraftItem extends DraftItem {
    public PNDraftItem(String itemId) {
        super(Category.PN, itemId);
    }

    private FactionModel getFaction() {
        if ("keleres".equals(ItemId)) {
            return Mapper.getFaction("keleresa");
        }
        return Mapper.getFaction(ItemId);
    }

    private PromissoryNoteModel getPn() {
        var pn = Mapper.getPromissoryNoteByID(ItemId);
        if (pn == null) {
            FactionModel faction = getFaction();
            return Mapper.getPromissoryNoteByID(faction.getPromissoryNotes().get(0));
        }
        return pn;
    }

    @Override
    public String getShortDescription() {
        PromissoryNoteModel pn = getPn();
        return "Promissory Note - " + pn.getName();
    }

    @Override
    public String getLongDescriptionImpl() {
        PromissoryNoteModel pn = getPn();
        return pn.getText();
    }

    @Override
    public String getItemEmoji() {
        return Emojis.PN;
    }
}
