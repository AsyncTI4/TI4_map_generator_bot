package ti4.map;

import java.awt.*;
import java.util.HashMap;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("space")
public class Space extends UnitHolder {
    protected Space(String name, Point holderCenterPosition) {
        super(name, holderCenterPosition);
    }
    
    @JsonCreator
    public Space(@JsonProperty("name") String name) {
        super(name, null);
    }
}
