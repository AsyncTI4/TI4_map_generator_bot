package ti4.draft;

import ti4.draft.items.*;
import ti4.generator.Mapper;
import ti4.map.Player;
import ti4.model.*;

import java.util.ArrayList;
import java.util.List;

public abstract class DraftItem implements ModelInterface {
    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public String getAlias() {
        return ItemCategory.toString()+":"+ItemId;
    }

    public enum Category{
        ABILITY,
        TECH,
        AGENT,
        COMMANDER,
        HERO,
        MECH,
        FLAGSHIP,
        COMMODITIES,
        PN,
        HOMESYSTEM,
        STARTINGTECH,
        STARTINGFLEET,
        BLUETILE,
        REDTILE,
        DRAFTORDER
    }

    public Category ItemCategory;

    // The system ID of the item. Only convert this to player-readable text when necessary
    public String ItemId;


    public static DraftItem Generate(Category category, String itemId) {
        if (itemId.contains("keleres") && category != Category.HERO) {
            itemId = "keleres";
        }
        switch (category) {

            case ABILITY -> {
                return new AbilityDraftItem(itemId);
            }
            case TECH -> {
                return new TechDraftItem(itemId);
            }
            case AGENT -> {
                return new AgentDraftItem(itemId);
            }
            case COMMANDER -> {
                return new CommanderDraftItem(itemId);
            }
            case HERO -> {
                return new HeroDraftItem(itemId);
            }
            case MECH -> {
                return new MechDraftItem(itemId);
            }
            case FLAGSHIP -> {
                return new FlagshipDraftItem(itemId);
            }
            case COMMODITIES -> {
                return new CommoditiesDraftItem(itemId);
            }
            case PN -> {
                return new PNDraftItem(itemId);
            }
            case HOMESYSTEM -> {
                return new HomeSystemDraftItem(itemId);
            }
            case STARTINGTECH -> {
                return new StartingTechDraftItem(itemId);
            }
            case STARTINGFLEET -> {
                return new StartingFleetDraftItem(itemId);
            }
            case BLUETILE -> {
                return new BlueTileDraftItem(itemId);
            }
            case REDTILE -> {
                return new RedTileDraftItem(itemId);
            }
            case DRAFTORDER -> {
                return new SpeakerOrderDraftItem(itemId);
            }
        }
        return null;
    }

    public static DraftItem GenerateFromAlias(String alias) {
        String[] split = alias.split(":");
        return Generate(Category.valueOf(split[0]), split[1]);
    }

    public static List<DraftItem> GetAlwaysIncludeItems(Category type) {
        List<DraftItem> alwaysInclude = new ArrayList<>();
        var frankenErrata = Mapper.getFrankenErrata().values();
        for(DraftErrataModel errataItem : frankenErrata) {
            if (errataItem.ItemCategory == type && errataItem.AlwaysAddToPool) {
                alwaysInclude.add(GenerateFromAlias(errataItem.getAlias()));
            }
        }

        return alwaysInclude;
    }

    protected DraftItem(Category category, String itemId)
    {
        ItemCategory = category;
        ItemId = itemId;
    }

    public abstract String getShortDescription();

    public abstract String getLongDescription();

    public abstract String getItemEmoji();

    public boolean isDraftable(Player player) {
        BagDraft draftRules = player.getGame().getActiveBagDraft();
        DraftBag draftHand = player.getDraftHand();
        boolean isAtHandLimit = draftHand.getCategoryCount(ItemCategory) >= draftRules.getItemLimitForCategory(ItemCategory);
        if (isAtHandLimit) {
            return false;
        }
        boolean hasDraftedThisBag = player.getDraftQueue().getCategoryCount(ItemCategory) > 0;

        boolean allOtherCategoriesAtHandLimit = true;
        for (Category cat : Category.values()) {
            if (ItemCategory == cat) {
                continue;
            }
            allOtherCategoriesAtHandLimit &= draftHand.getCategoryCount(cat) >= draftRules.getItemLimitForCategory(cat);
        }

        if (hasDraftedThisBag) {
            return allOtherCategoriesAtHandLimit;
        }
        return true;
    }


}
