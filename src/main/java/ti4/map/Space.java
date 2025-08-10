package ti4.map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.awt.*;

@JsonTypeName("space")
public class Space extends UnitHolder {
    @JsonCreator
    public Space(@JsonProperty("name") String name, @JsonProperty("holderCenterPosition") Point holderCenterPosition) {
        super(name, holderCenterPosition);
    }

    public String getRepresentation(Game game) {
        return "Space";
    }
}
