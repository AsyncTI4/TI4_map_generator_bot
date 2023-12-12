package ti4.draft.items;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ti4.draft.DraftItem;
import ti4.generator.Mapper;
import ti4.helpers.Emojis;
import ti4.model.DraftErrataModel;
import ti4.model.FactionModel;
import ti4.model.PromissoryNoteModel;

import java.util.ArrayList;
import java.util.List;

public class PNDraftItem extends DraftItem {
    public PNDraftItem(String itemId) {
        super(Category.PN, itemId);
    }

    @JsonIgnore
    private PromissoryNoteModel getPn() {
        return Mapper.getPromissoryNoteByID(ItemId);
    }

    @JsonIgnore
    @Override
    public String getShortDescription() {
        PromissoryNoteModel pn = getPn();
        return "Promissory Note - " + pn.getName();
    }

    @JsonIgnore
    @Override
    public String getLongDescriptionImpl() {
        PromissoryNoteModel pn = getPn();
        return pn.getText();
    }

    @JsonIgnore
    @Override
    public String getItemEmoji() {
        return Emojis.PN;
    }

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions) {
        List<DraftItem> allItems = new ArrayList<>();
        for (FactionModel faction : factions) {
            allItems.add(DraftItem.Generate(Category.PN,faction.getPromissoryNotes().get(0)));
        }
        DraftErrataModel.filterUndraftablesAndShuffle(allItems, DraftItem.Category.PN);
        return allItems;
    }
}
