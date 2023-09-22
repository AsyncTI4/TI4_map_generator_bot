package ti4.model;

import java.util.List;

import ti4.generator.Mapper;
import ti4.generator.TileHelper;
import ti4.message.BotLogger;

import java.util.ArrayList;

public class FactionModel implements ModelInterface {
    private String alias;
    private String factionName;
    private String homeSystem;
    private String startingFleet;
    private int commodities;
    private List<String> factionTech;
    private List<String> startingTech;
    private List<String> homePlanets;
    private List<String> abilities;
    private List<String> leaders;
    private List<String> promissoryNotes;
    private List<String> units;
    private String source;

    public boolean isValid() {
        validateAbilities();
        validateFactionTech();
        validateHomePlanets();
        validateStartingTech();
        validateLeaders();
        validatePromissoryNotes();
        validateUnits();
        return alias != null
            && factionName != null
            && homeSystem != null
            && startingFleet != null
            && factionTech != null
            && startingTech != null
            && homePlanets != null
            && abilities != null
            && leaders != null
            && promissoryNotes != null
            && units != null;
    }

    public String getAlias() {
        return alias;
    }

    public String getFactionName() {
        return factionName;
    }

    public String getHomeSystem() {
        return homeSystem;
    }

    public String getStartingFleet() {
        return startingFleet;
    }

    public int getCommodities() {
        return commodities;
    }

    public String getSource() {
        return source;
    }

    public List<String> getFactionTech() {
        return new ArrayList<>(factionTech);
    }

    public List<String> getStartingTech() {
        return new ArrayList<>(startingTech);
    }

    public List<String> getHomePlanets() {
        return new ArrayList<>(homePlanets);
    }

    public List<String> getAbilities() {
        return new ArrayList<>(abilities);
    }

    public List<String> getLeaders() {
        return new ArrayList<>(leaders);
    }

    public List<String> getPromissoryNotes() {
        return new ArrayList<>(promissoryNotes);
    }

    public List<String> getUnits() {
        return new ArrayList<>(units);
    }

    private boolean validateLeaders() {
        if (Mapper.getLeaderRepresentations().keySet().containsAll(getLeaders())) return true;
        List<String> invalidLeaderIDs = new ArrayList<>();
        for (String leaderID : getLeaders()) {
            if (!Mapper.getLeaderRepresentations().containsKey(leaderID)) invalidLeaderIDs.add(leaderID);
        }

        BotLogger.log("Faction **" + getAlias() + "** failed validation due to invalid leader IDs: `" + invalidLeaderIDs + "`");
        return false;
    }

    private boolean validateUnits() {
        if (Mapper.getUnits().keySet().containsAll(getUnits())) return true;
        List<String> invalidUnitIDs = new ArrayList<>();
        for (String unitID : getUnits()) {
            if (!Mapper.getUnits().containsKey(unitID)) invalidUnitIDs.add(unitID);
        }

        BotLogger.log("Faction **" + getAlias() + "** failed validation due to invalid unit IDs: `" + invalidUnitIDs + "`");
        return false;
    }

    private boolean validatePromissoryNotes() {
        if (Mapper.getPromissoryNotes().keySet().containsAll(getPromissoryNotes())) return true;
        List<String> invalidPromissoryNoteIDs = new ArrayList<>();
        for (String promissoryNoteID : getPromissoryNotes()) {
            if (!Mapper.getPromissoryNotes().containsKey(promissoryNoteID)) invalidPromissoryNoteIDs.add(promissoryNoteID);
        }

        BotLogger.log("Faction **" + getAlias() + "** failed validation due to invalid promissory note IDs: `" + invalidPromissoryNoteIDs + "`");
        return false;
    }

    private boolean validateAbilities() {
        if (Mapper.getFactionAbilities().keySet().containsAll(getAbilities())) return true;
        List<String> invalidAbilityIDs = new ArrayList<>();
        for (String abilityID : getAbilities()) {
            if (!Mapper.getFactionAbilities().containsKey(abilityID)) invalidAbilityIDs.add(abilityID);
        }

        BotLogger.log("Faction **" + getAlias() + "** failed validation due to invalid ability IDs: `" + invalidAbilityIDs + "`");
        return false;
    }

    private boolean validateHomePlanets() {
        if (TileHelper.getAllPlanets().keySet().containsAll(getHomePlanets())) return true;
        List<String> invalidPlanetIDs = new ArrayList<>();
        for (String planetID : getHomePlanets()) {
            if (!TileHelper.getAllPlanets().containsKey(planetID)) invalidPlanetIDs.add(planetID);
        }

        BotLogger.log("Faction **" + getAlias() + "** failed validation due to invalid home planet IDs: `" + invalidPlanetIDs + "`");
        return false;
    }

    private boolean validateStartingTech() {
        if (Mapper.getTechs().keySet().containsAll(getStartingTech())) return true;
        List<String> invalidStartingTechIDs = new ArrayList<>();
        for (String startingTechID : getStartingTech()) {
            if (!Mapper.getTechs().containsKey(startingTechID)) invalidStartingTechIDs.add(startingTechID);
        }

        BotLogger.log("Faction **" + getAlias() + "** failed validation due to invalid starting tech IDs: `" + invalidStartingTechIDs + "`");
        return false;
    }

    private boolean validateFactionTech() {
        if (Mapper.getTechs().keySet().containsAll(getFactionTech())) return true;
        List<String> invalidFactionTechIDs = new ArrayList<>();
        for (String factionTechID : getFactionTech()) {
            if (!Mapper.getTechs().containsKey(factionTechID)) invalidFactionTechIDs.add(factionTechID);
        }

        BotLogger.log("Faction **" + getAlias() + "** failed validation due to invalid faction tech IDs: `" + invalidFactionTechIDs + "`");
        return false;
    }
}
