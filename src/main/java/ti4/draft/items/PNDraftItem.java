package ti4.draft.items;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import ti4.draft.DraftCategory;
import ti4.draft.DraftItem;
import ti4.game.Game;
import ti4.image.Mapper;
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
        return draftable(DraftCategory.PN, buildAllItems(factions));
    }

    public static List<DraftItem> buildAllItems(List<FactionModel> factions) {
        return generateAll(
                DraftCategory.PN, factions.stream().flatMap(faction -> faction.getPromissoryNotes().stream()));
    }

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions, Game game) {
        return draftable(DraftCategory.PN, withoutBanned(buildAllItems(factions), game, "bannedPNs"));
    }
}
