package ti4.draft;

import ti4.draft.items.*;
import ti4.generator.Mapper;
import ti4.generator.TileHelper;
import ti4.helpers.Helper;
import ti4.model.*;

import java.util.ArrayList;
import java.util.HashMap;
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
            //TileHelper.getTile(ItemId).getName()
            case BLUETILE -> {
                return "Blue Tile: " +Helper.getBasicTileRep(ItemId);
            }
            case REDTILE -> {
                return "Red Tile: " +Mapper.getTileRepresentations().get(ItemId) + " (" + ItemId +")";
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
        return "Home System: " + tile.getName();
    }

    private String getPromissory(FactionModel faction) {
        PromissoryNoteModel pn = Mapper.getPromissoryNotes().get(faction.getPromissoryNotes().get(0));
        return "Promissory Note: " + pn.getName();
    }

    private String getCommodities(FactionModel faction) {
        return "Commodities: " + faction.getCommodities();
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
            return "Flagship: "  +faction.getFactionName() + flag.getName();
        }
        return "Flagship: "  +faction.getFactionName() + " flagship";
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
            return "Mech: " +faction.getFactionName() + mech.getName();
        }
        return "Mech: " + faction.getFactionName() + " mech";
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
        HashMap<String, LeaderModel> leaders = Mapper.getLeaders();

        String leaderHumanReadable = Character.toTitleCase(leaderType.charAt(0)) + leaderType.substring(1);
        return leaderHumanReadable + ": " + leaders.get(leaderId).getName();
    }

//Helper.getFactionIconFromDiscord(tech.getFaction())
    private String getTechHumanReadable() {
        TechnologyModel tech = Mapper.getTech(ItemId);
        String factionName = Mapper.getFactionRepresentations().get(tech.getFaction());
        return "Tech: " + tech.getName();
    }

    private String getAbilityHumanReadable() {
        HashMap<String, String> abilities = Mapper.getFactionAbilities();
        String[] abilitySplit = abilities.get(ItemId).split("\\|");
        //Helper.getFactionIconFromDiscord(abilitySplit[1])
        return "Ability: " + abilitySplit[0];
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
                limit = 2;
            }
            case DRAFTORDER -> {
                limit = 1;
            }
        }
        return limit;
    }
}
