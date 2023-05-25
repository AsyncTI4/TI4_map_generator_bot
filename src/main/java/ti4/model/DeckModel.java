package ti4.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import ti4.message.BotLogger;

public class DeckModel extends Model {
    public String name;
    public String type;
    public String description;
    public List<String> cardIDs;

    public DeckModel(JsonNode json) {
        try {
            alias = json.get("alias").asText();
            name = json.get("name").asText();
            type = json.get("type").asText();
            description = json.get("description").asText();

            cardIDs = new ArrayList<String>();
            json.get("cardIDs").elements().forEachRemaining(val -> cardIDs.add(val.asText()));
        } catch (Exception e) {
            BotLogger.log("Could not load agenda.");
        }
    }

    public boolean isValid() {
        return super.isValid()
            && name != null
            && type != null
            && description != null
            && cardIDs != null;
    }

    public List<String> getShuffledCardList() {
        List<String> cardList = cardIDs;
        Collections.shuffle(cardIDs);
        return cardList;    
    }
}
