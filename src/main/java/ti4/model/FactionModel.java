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
    public List<String> leaders;
    public List<String> promissoryNotes;

    public FactionModel(JsonNode json) {
        try {
            alias = json.get("alias").asText();
            factionName = json.get("factionName").asText();
            homeSystemTile = json.get("homeSystem").asText();
            startingFleet = json.get("startingFleet").asText();
            commodities = json.get("commodities").intValue();

            factionTech = new ArrayList<String>();
            json.get("factionTech").elements().forEachRemaining(val -> factionTech.add(val.asText()));

            startingTech = new ArrayList<String>();
            json.get("startingTech").elements().forEachRemaining(val -> startingTech.add(val.asText()));

            homePlanets = new ArrayList<String>();
            json.get("homePlanets").elements().forEachRemaining(val -> homePlanets.add(val.asText()));

            abilities = new ArrayList<String>();
            json.get("abilities").elements().forEachRemaining(val -> abilities.add(val.asText()));

            leaders = new ArrayList<String>();
            json.get("leaders").elements().forEachRemaining(val -> leaders.add(val.asText()));

            promissoryNotes = new ArrayList<String>();
            json.get("promissoryNotes").elements().forEachRemaining(val -> promissoryNotes.add(val.asText()));
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
            && abilities != null
            && leaders != null
            && promissoryNotes != null;
    }
}
