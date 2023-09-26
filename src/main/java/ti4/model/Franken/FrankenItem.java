package ti4.model.Franken;

import ti4.model.ModelInterface;

public class FrankenItem implements ModelInterface {
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

    // The type of item to be drafted
    public Category ItemCategory;

    // The system ID of the item. Only convert this to player-readable text when necessary
    public String ItemId;

    public FrankenItem[] AdditionalComponents;
    public FrankenItem[] OptionalSwaps;
    public boolean Undraftable;

    public FrankenItem(){
    }

    public FrankenItem(Category category, String itemId)
    {
        ItemCategory = category;
        ItemId = itemId;
    }

    // What gets written to the save file
    public String toStoreString()
    {
        return ItemCategory + ":" + ItemId;
    }

    public String toHumanReadable()
    {
        switch (ItemCategory) {
        }
        return "";
    }

    public static int GetBagLimit(Category category, boolean powered, boolean largeMap) {
        int limit = 0;
        switch (category) {

            case ABILITY -> {
                limit = powered ? 4 : 3;
            }
            case TECH -> {
                limit = powered ? 3 : 2;
            }
            case AGENT -> {
                limit = 2;
            }
            case COMMANDER -> {
                limit = 2;
            }
            case HERO -> {
                limit = 2;
            }
            case MECH -> {
                limit = 2;
            }
            case FLAGSHIP -> {
                limit = 2;
            }
            case COMMODITIES -> {
                limit = 2;
            }
            case PN -> {
                limit = 2;
            }
            case HOMESYSTEM -> {
                limit = 2;
            }
            case STARTINGTECH -> {
                limit = 2;
            }
            case STARTINGFLEET -> {
                limit = 2;
            }
            case BLUETILE -> {
                limit = 3;
            }
            case REDTILE -> {
                limit = largeMap ? 2 : 3;
            }
            case DRAFTORDER -> {
                limit = 1;
            }
        }
        return limit;
    }
}
