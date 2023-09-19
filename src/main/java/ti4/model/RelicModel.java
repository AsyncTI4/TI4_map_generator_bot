package ti4.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;

@Data
public class RelicModel implements ModelInterface {
    private String alias;
    private String name;
    private String shortName;
    private String text;
    private String source;

    @Override
    public boolean isValid() {
        return true;
    }

    @JsonIgnore
    public String getSimpleRepresentation() {
        return String.format("**%s** *(%s)*: %s", getName(), getSource(), getText());
    }
}
