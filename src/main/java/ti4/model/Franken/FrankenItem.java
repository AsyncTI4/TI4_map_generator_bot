package ti4.model.Franken;

import ti4.generator.Mapper;
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

    public static FrankenItem Generate(Category category, String itemId) {
        FrankenItem item = new FrankenItem(category, itemId);

        var frankenErrata = Mapper.getFrankenErrata().values();
        for(FrankenItem errataItem : frankenErrata) {
            if (errataItem.getAlias().equals(item.getAlias())) {
                return errataItem;
            }
        }
        return item;
    }

    public static FrankenItem GenerateFromAlias(String alias) {
        String[] split = alias.split(":");
        return Generate(Category.valueOf(split[0]), split[1]);
    }

    public FrankenItem(){
    }

    private FrankenItem(Category category, String itemId)
    {
        ItemCategory = category;
        ItemId = itemId;
    }

    public String toHumanReadable()
    {
        // TODO: make this actually human readable
        return getAlias();
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
