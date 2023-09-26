package ti4.model.Franken;

import ti4.generator.Mapper;
import ti4.generator.TileHelper;
import ti4.helpers.Helper;
import ti4.model.*;

import java.util.HashMap;

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
        FactionModel faction = Mapper.getFactionSetup(ItemId);
        switch (ItemCategory) {
            case ABILITY -> {
                return getAbilityHumanReadable();
            }
            case TECH -> {
                return getTechHumanReadable();
            }
            case AGENT -> {
                return getLeaderHumanReadable(faction);
            }
            case COMMANDER -> {
                return getLeaderHumanReadable(faction);
            }
            case HERO -> {
                return getLeaderHumanReadable(faction);
            }
            case MECH -> {
                return getMech(faction);
            }
            case FLAGSHIP -> {
                return getFlagship(faction);
            }
            case COMMODITIES -> {
                return getCommodities(faction);
            }
            case PN -> {
                return getPromissory(faction);
            }
            case HOMESYSTEM -> {
                return getHomeSystem(faction);
            }
            case STARTINGTECH -> {
                return "Starting Tech: " + faction.getFactionName();
            }
            case STARTINGFLEET -> {
                return "Starting Fleet: " + faction.getFactionName();
            }
            case BLUETILE -> {
                return "Blue Tile: " +TileHelper.getTile(ItemId).getName() + " (" + ItemId +")";
            }
            case REDTILE -> {
                return "Red Tile: " +TileHelper.getTile(ItemId).getName() + " (" + ItemId +")";
            }
            case DRAFTORDER -> {
                return "Speaker Order: " + (ItemId.equals("1") ?"Speaker":ItemId);
            }
        }
        return getAlias();
    }

    private String getHomeSystem(FactionModel faction) {
        String homeSystemID = faction.getHomeSystem();
        TileModel tile = TileHelper.getTile(homeSystemID);
        return "Home System: " + Helper.getFactionIconFromDiscord(faction.getAlias()) + " " + tile.getName();
    }

    private String getPromissory(FactionModel faction) {
        PromissoryNoteModel pn = Mapper.getPromissoryNotes().get(faction.getPromissoryNotes().get(0));
        return "Promissory Note: " + Helper.getFactionIconFromDiscord(faction.getAlias()) + " " + pn.getName();
    }

    private String getCommodities(FactionModel faction) {
        return "Commodities: " + Helper.getFactionIconFromDiscord(faction.getAlias()) + " " + faction.getCommodities();
    }

    private String getFlagship(FactionModel faction) {
        UnitModel flag = null;
        var units = Mapper.getUnits().entrySet();
        for (var unit : units) {
            if (unit.getValue().getBaseType().equals("flagship") && unit.getValue().getFaction() != null && faction.getAlias().equals(unit.getValue().getFaction())){
                flag = unit.getValue();
                break;
            }

        }
        if (flag != null) {
            return "Flagship: " + Helper.getFactionIconFromDiscord(faction.getAlias()) + flag.getName();
        }
        return "Flagship: " + Helper.getFactionIconFromDiscord(faction.getAlias()) + " flagship";
    }

    private String getMech(FactionModel faction) {
        UnitModel mech = null;
        var units = Mapper.getUnits().entrySet();
        for (var unit : units) {
            if (unit.getValue().getBaseType().equals("mech") && unit.getValue().getFaction() != null && faction.getAlias().equals(unit.getValue().getFaction())){
                mech = unit.getValue();
                break;
            }

        }
        if (mech != null) {
            return "Mech: " + Helper.getFactionIconFromDiscord(faction.getAlias()) + mech.getName();
        }
        return "Mech: " + Helper.getFactionIconFromDiscord(faction.getAlias()) + " mech";
    }

    private String getLeaderHumanReadable(FactionModel faction) {
        var leaderIDs = faction.getLeaders();
        String leaderType = ItemCategory.toString().toLowerCase();
        String leaderId = "";
        for (var l : leaderIDs) {
            if (l.contains(leaderType)) {
                leaderId = l;
                break;
            }
        }
        HashMap<String, String> leaders = Mapper.getLeaderRepresentations();

        String leaderHumanReadable = Character.toTitleCase(leaderType.charAt(0)) + leaderType.substring(1);
        return leaderHumanReadable + ": " +  Helper.getFactionIconFromDiscord(faction.getAlias()) + " " + leaders.get(leaderId).split(";")[0];
    }


    private String getTechHumanReadable() {
        TechnologyModel tech = Mapper.getTech(ItemId);
        String factionName = Mapper.getFactionRepresentations().get(tech.getFaction());
        return "Tech: " + Helper.getFactionIconFromDiscord(tech.getFaction()) + " " + tech.getName();
    }

    private String getAbilityHumanReadable() {
        HashMap<String, String> abilities = Mapper.getFactionAbilities();
        String[] abilitySplit = abilities.get(ItemId).split("\\|");

        return "Ability: " + Helper.getFactionIconFromDiscord(abilitySplit[1]) + " " + abilitySplit[0];
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
