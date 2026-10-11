package ti4.draft.items;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import ti4.draft.DraftCategory;
import ti4.draft.DraftItem;
import ti4.game.Game;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.model.FactionModel;
import ti4.service.emoji.TI4Emoji;
import ti4.service.emoji.TechEmojis;

public class StartingFleetDraftItem extends DraftItem {

    public StartingFleetDraftItem(String itemId) {
        super(DraftCategory.STARTINGFLEET, itemId);
    }

    @JsonIgnore
    @Override
    public String getTitle(Game game) {
        return getFaction().getFactionEmoji() + " Starting Fleet";
    }

    @JsonIgnore
    private FactionModel getFaction() {
        return Mapper.getFaction(getItemId());
    }

    @JsonIgnore
    @Override
    public String getShortDescription() {
        return getFaction().getShortName() + " Starting Fleet";
    }

    @JsonIgnore
    @Override
    public String getLongDescriptionImpl(Game game) {
        return getLongDescriptionImpl();
    }

    @JsonIgnore
    @Override
    public String getLongDescriptionImpl() {
        return Helper.getOrderedUnitListEmojis(getFaction().getStartingFleet(), true);
    }

    @JsonIgnore
    @Override
    public TI4Emoji getItemEmoji() {
        return TechEmojis.NonUnitTechSkip;
    }

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions) {
        return draftable(DraftCategory.STARTINGFLEET, buildAllItems(factions));
    }

    public static List<DraftItem> buildAllItems(List<FactionModel> factions) {
        return generateAll(DraftCategory.STARTINGFLEET, factions.stream().map(FactionModel::getAlias));
    }

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions, Game game) {
        return draftable(
                DraftCategory.STARTINGFLEET,
                withoutBanned(buildAllItems(factions), game, "bannedFleets"),
                game.isTwilightsFallMode());
    }
}
