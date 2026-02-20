package ti4.draft.items;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import ti4.draft.DraftCategory;
import ti4.draft.DraftItem;
import ti4.helpers.PatternHelper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.model.DraftErrataModel;
import ti4.model.FactionModel;
import ti4.model.PromissoryNoteModel;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.TI4Emoji;

public class PNDraftItem extends DraftItem {

    public PNDraftItem(String itemId) {
        super(DraftCategory.PN, itemId);
    }

    @JsonIgnore
    @Override
    public String getTitle(Game game) {
        return getPn().getNameRepresentation();
    }

    @JsonIgnore
    private PromissoryNoteModel getPn() {
        return Mapper.getPromissoryNote(getItemId());
    }

    @JsonIgnore
    @Override
    public String getLongDescriptionImpl(Game game) {
        return getLongDescriptionImpl();
    }

    @JsonIgnore
    @Override
    public String getShortDescription() {
        return getPn().getName();
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
        DraftErrataModel.filterUndraftablesAndShuffle(allItems, DraftCategory.PN);
        return allItems;
    }

    public static List<DraftItem> buildAllItems(List<FactionModel> factions) {
        List<DraftItem> allItems = new ArrayList<>();
        for (FactionModel faction : factions) {
            for (String pnID : faction.getPromissoryNotes()) {
                allItems.add(generate(DraftCategory.PN, pnID));
            }
        }
        return allItems;
    }

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions, Game game) {
        List<DraftItem> allItems = buildAllItems(factions, game);
        DraftErrataModel.filterUndraftablesAndShuffle(allItems, DraftCategory.PN);
        return allItems;
    }

    private static List<DraftItem> buildAllItems(List<FactionModel> factions, Game game) {
        List<DraftItem> allItems = new ArrayList<>();
        String[] results = PatternHelper.FIN_SEPERATOR_PATTERN.split(game.getStoredValue("bannedPNs"));
        for (FactionModel faction : factions) {
            for (String pnID : faction.getPromissoryNotes()) {
                if (Arrays.asList(results).contains(pnID)) {
                    continue;
                }
                allItems.add(generate(DraftCategory.PN, pnID));
            }
        }
        return allItems;
    }
}
