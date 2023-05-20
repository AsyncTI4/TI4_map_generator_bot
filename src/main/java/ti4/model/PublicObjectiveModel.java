package ti4.model;

import com.fasterxml.jackson.databind.JsonNode;

import ti4.message.BotLogger;

public class PublicObjectiveModel extends Model {
    public String name;
    public String phase;
    public String window;
    public String text;
    public int points;
    public String source;

    public PublicObjectiveModel(JsonNode json) {
        try {
            alias = json.get("alias").asText();
            name = json.get("name").asText();
            phase = json.get("phase").asText();
            text = json.get("text").asText();
            points = json.get("points").asInt();
            source = json.get("source").asText();
        } catch (Exception e) {
            BotLogger.log("Could not load agenda.");
        }
    }

    public boolean isValid() {
        return super.isValid()
            && name != null
            && phase != null
            && text != null
            && source != null;
    }
}
