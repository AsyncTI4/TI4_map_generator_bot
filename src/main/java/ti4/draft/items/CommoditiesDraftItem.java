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
        List<DraftItem> allItems = buildAllItems(factions);
        DraftErrataModel.filterUndraftablesAndShuffle(allItems, DraftCategory.COMMODITIES);
        return allItems;
    }

    public static List<DraftItem> buildAllItems(List<FactionModel> factions) {
        List<DraftItem> allItems = new ArrayList<>();
        for (FactionModel faction : factions) {
            allItems.add(generate(DraftCategory.COMMODITIES, faction.getAlias()));
        }
        return allItems;
    }

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions, Game game) {
        List<DraftItem> allItems = buildAllItems(factions, game);
        DraftErrataModel.filterUndraftablesAndShuffle(allItems, DraftCategory.COMMODITIES);
        return allItems;
    }

    private static List<DraftItem> buildAllItems(List<FactionModel> factions, Game game) {
        List<DraftItem> allItems = new ArrayList<>();
        String[] results = PatternHelper.FIN_SEPERATOR_PATTERN.split(game.getStoredValue("bannedComms"));
        for (FactionModel faction : factions) {
            if (Arrays.asList(results).contains(faction.getAlias())) {
                continue;
            }
            allItems.add(generate(DraftCategory.COMMODITIES, faction.getAlias()));
        }
        return allItems;
    }
}
