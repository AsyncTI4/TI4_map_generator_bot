package ti4.model;

import com.fasterxml.jackson.databind.JsonNode;

import ti4.message.BotLogger;

public class AgendaModel extends Model {
    public String name;
    public String type;
    public String target;
    public String text1;
    public String text2;
    public String source;

    public AgendaModel(JsonNode json) {
        try {
            alias = json.get("alias").asText();
            name = json.get("name").asText();
            type = json.get("type").asText();
            target = json.get("target").asText();
            text1 = json.get("text1").asText();
            text2 = json.get("text2").asText();
            source = json.get("source").asText();

        } catch (Exception e) {
            BotLogger.log("Could not load agenda.");
        }
    }

    public boolean isValid() {
        return super.isValid()
            && name != null
            && type != null
            && target != null
            && text1 != null
            && text2 != null
            && source != null;
    }
}
