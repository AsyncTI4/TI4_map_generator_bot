package ti4.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import ti4.message.BotLogger;

public class DeckModel extends Model {
    private String name;
    private String type;
    private String description;
    private List<String> cardIDs;

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

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getCardIDs() {
        return cardIDs;
    }

    public List<String> getShuffledCardList() {
        List<String> cardList = cardIDs;
        Collections.shuffle(cardIDs);
        return cardList;    
    }
}
