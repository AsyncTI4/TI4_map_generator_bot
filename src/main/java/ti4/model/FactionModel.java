package ti4.model;

import java.util.List;
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

  public boolean isValid() {
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
}
