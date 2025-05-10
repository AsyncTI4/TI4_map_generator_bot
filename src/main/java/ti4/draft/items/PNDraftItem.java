package ti4.draft.items;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ti4.draft.DraftItem;
import ti4.image.Mapper;
import ti4.model.DraftErrataModel;
import ti4.model.FactionModel;
import ti4.model.PromissoryNoteModel;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.TI4Emoji;

public class PNDraftItem extends DraftItem {
    public PNDraftItem(String itemId) {
        super(Category.PN, itemId);
    }

    @JsonIgnore
    private PromissoryNoteModel getPn() {
        return Mapper.getPromissoryNote(ItemId);
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
    public TI4Emoji getItemEmoji() {
        return CardEmojis.PN;
    }

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions) {
        List<DraftItem> allItems = buildAllItems(factions);
        DraftErrataModel.filterUndraftablesAndShuffle(allItems, DraftItem.Category.PN);
        return allItems;
    }

    public static List<DraftItem> buildAllItems(List<FactionModel> factions) {
        List<DraftItem> allItems = new ArrayList<>();
        for (FactionModel faction : factions) {
            for (String pnID : faction.getPromissoryNotes()) {
                allItems.add(DraftItem.generate(Category.PN, pnID));
            }
        }
        return allItems;
    }
}
