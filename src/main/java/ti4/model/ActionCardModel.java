package ti4.model;

import com.fasterxml.jackson.databind.JsonNode;

import ti4.message.BotLogger;
import ti4.helpers.Emojis;

public class ActionCardModel extends Model {
    public String name;
    public String phase;
    public String window;
    public String text;
    public String flavorText;
    public String source;

    public ActionCardModel(JsonNode json) {
        try {
            alias = json.get("alias").asText();
            name = json.get("name").asText();
            phase = json.get("phase").asText();
            window = json.get("window").asText();
            text = json.get("text").asText();
            flavorText = json.get("flavorText").asText();
            source = json.get("source").asText();
        } catch (Exception e) {
            BotLogger.log("Could not load agenda.");
        }
    }

    public boolean isValid() {
        return super.isValid()
            && name != null
            && phase != null
            && window != null
            && text != null
            && flavorText != null
            && source != null;
    }

    public String getRepresentation() {
        StringBuilder sb = new StringBuilder();
        sb.append(Emojis.ActionCard).append("__**" + name + "**__").append(" *(").append(phase).append(" Phase)*: ");
        sb.append("_").append(window).append(":_ ").append(text).append("\n");
        return sb.toString();
    }
}
