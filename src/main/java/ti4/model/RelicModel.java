package ti4.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;
import ti4.helpers.Emojis;

@Data
public class RelicModel implements ModelInterface {
    private String alias;
    private String name;
    private String shortName;
    private String text;
    private String source;

    @Override
    public boolean isValid() {
        return alias != null 
            && name != null 
            && text != null 
            && source != null;
    }

    @JsonIgnore
    public String getSimpleRepresentation() {
        return getSourceEmoji() + String.format("**%s**: %s *(%s)*", getName(), getText(), getSource());
    }

    @JsonIgnore
    public String getSourceEmoji() {
        return switch (source) {
            case "absol" -> Emojis.Absol;
            case "ds" -> Emojis.DiscordantStars;
            default -> "";
        };
    }
}
