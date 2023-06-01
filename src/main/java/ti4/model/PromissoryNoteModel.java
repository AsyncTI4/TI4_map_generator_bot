package ti4.model;

import com.fasterxml.jackson.databind.JsonNode;

import ti4.message.BotLogger;

public class PromissoryNoteModel extends Model {
    public String name;
    public String faction;
    public String colour;
    public Boolean playArea;
    public String attachment;
    public String text;
    public String source;

    public PromissoryNoteModel(JsonNode json) {
        try {
            alias = json.get("alias").asText();
            name = json.get("name").asText();
            faction = json.get("faction").asText();
            colour = json.get("colour").asText();
            playArea = json.get("playArea").asBoolean();
            attachment = json.get("attachment").asText();
            text = json.get("text").asText();
            source = json.get("source").asText();
        } catch (Exception e) {
            BotLogger.log("Could not load agenda.");
        }
    }

    public boolean isValid() {
        return super.isValid()
            && name != null
            && (faction != null && colour != null)
            && playArea != null
            && attachment != null
            && text != null
            && source != null;
    }

    public String getOwner() {
        if (faction == null || faction.isEmpty()) return colour;
        return faction;
    }

}
