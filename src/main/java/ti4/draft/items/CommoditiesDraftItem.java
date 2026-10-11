package ti4.draft.items;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import ti4.draft.DraftCategory;
import ti4.draft.DraftItem;
import ti4.game.Game;
import ti4.image.Mapper;
import ti4.model.FactionModel;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.TI4Emoji;

public class CommoditiesDraftItem extends DraftItem {

    public CommoditiesDraftItem(String itemId) {
        super(DraftCategory.COMMODITIES, itemId);
    }

    @JsonIgnore
    @Override
    public String getTitle(Game game) {
        return getFaction().getFactionEmoji() + " " + getLongDescriptionImpl();
    }

    private FactionModel getFaction() {
        if ("keleres".equals(getItemId())) {
            return Mapper.getFaction("keleresa");
        }
        return Mapper.getFaction(getItemId());
    }

    @JsonIgnore
    @Override
    public String getLongDescriptionImpl(Game game) {
        return getLongDescriptionImpl();
    }

    @JsonIgnore
    @Override
    public String getShortDescription() {
        return getFaction().getShortName() + " Commodities (" + getCommodities() + ")";
    }

    @JsonIgnore
    @Override
    public String getLongDescriptionImpl() {
        int comms = getFaction().getCommodities();
        return comms + " Commodities";
    }

    @JsonIgnore
    public int getCommodities() {
        return getFaction().getCommodities();
    }

    @JsonIgnore
    @Override
    public TI4Emoji getItemEmoji() {
        return MiscEmojis.comm;
    }

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions) {
        return draftable(DraftCategory.COMMODITIES, buildAllItems(factions));
    }

    public static List<DraftItem> buildAllItems(List<FactionModel> factions) {
        return generateAll(DraftCategory.COMMODITIES, factions.stream().map(FactionModel::getAlias));
    }

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions, Game game) {
        return draftable(DraftCategory.COMMODITIES, withoutBanned(buildAllItems(factions), game, "bannedComms"));
    }
}
