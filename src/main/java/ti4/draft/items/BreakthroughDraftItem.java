package ti4.draft.items;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import ti4.draft.DraftCategory;
import ti4.draft.DraftItem;
import ti4.game.Game;
import ti4.image.Mapper;
import ti4.model.BreakthroughModel;
import ti4.model.FactionModel;
import ti4.service.emoji.TI4Emoji;

public class BreakthroughDraftItem extends DraftItem {

    public BreakthroughDraftItem(String itemId) {
        super(DraftCategory.BREAKTHROUGH, itemId);
    }

    @JsonIgnore
    @Override
    public String getTitle(Game game) {
        return getBreakthroughModel().getNameRepresentation();
    }

    @JsonIgnore
    @Override
    public String getShortDescription() {
        return getBreakthroughModel().getName();
    }

    @JsonIgnore
    @Override
    public String getLongDescriptionImpl(Game game) {
        return getLongDescriptionImpl();
    }

    @JsonIgnore
    @Override
    public String getLongDescriptionImpl() {
        BreakthroughModel breakthroughModel = getBreakthroughModel();
        return breakthroughModel.getText() + "\n";
    }

    @JsonIgnore
    @Override
    public TI4Emoji getItemEmoji() {
        return getBreakthroughModel().getFactionEmoji();
    }

    @JsonIgnore
    private BreakthroughModel getBreakthroughModel() {
        if (!Mapper.isValidBreakthrough(getItemId())) System.err.println(getItemId() + " is not a valid breakthrough");
        return Mapper.getBreakthrough(getItemId());
    }

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions, Game game) {
        return draftable(
                DraftCategory.BREAKTHROUGH, withoutBanned(buildAllItems(factions), game, "bannedBreakthroughs"));
    }

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions) {
        return draftable(DraftCategory.BREAKTHROUGH, buildAllItems(factions));
    }

    public static List<DraftItem> buildAllItems(List<FactionModel> factions) {
        return generateAll(DraftCategory.BREAKTHROUGH, factions.stream().map(FactionModel::getBreakthrough));
    }
}
