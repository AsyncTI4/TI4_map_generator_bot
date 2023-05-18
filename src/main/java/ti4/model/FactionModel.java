package ti4.model;

import com.fasterxml.jackson.databind.JsonNode;

import ti4.message.BotLogger;

import java.util.*;

public class FactionModel extends Model {
    
    public String factionName;
    public String homeSystemTile;
    public String startingFleet;
    public int commodities;

    public List<String> factionTech;
    public List<String> startingTech;
    public List<String> homePlanets;
    public List<String> abilities;

    public FactionModel(JsonNode json) {
        try {
            alias = json.get("alias").asText();
            factionName = json.get("factionName").asText();
            homeSystemTile = json.get("homeSystem").asText();
            startingFleet = json.get("startingFleet").asText();
            commodities = json.get("commodities").intValue();

            factionTech = new ArrayList<String>(json.findValuesAsText("factionTech"));
            startingTech = new ArrayList<String>(json.findValuesAsText("startingTech"));
            homePlanets = new ArrayList<String>(json.findValuesAsText("homePlanets"));
            abilities = new ArrayList<String>(json.findValuesAsText("abilities"));
        } catch (Exception e) {
            BotLogger.log("Could not load faction setup.");
        }
    }

    public boolean isValid() {
        return super.isValid() 
            && factionName != null 
            && homeSystemTile != null 
            && startingFleet != null
            && factionTech != null
            && startingTech != null
            && homePlanets != null
            && abilities != null;
    }
}
