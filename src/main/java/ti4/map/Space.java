package ti4.map;

import java.awt.*;
import java.util.HashMap;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;

import ti4.helpers.Constants;

@JsonTypeName("space")
public class Space extends UnitHolder {
    @JsonCreator
    protected Space(@JsonProperty("name") String name, @JsonProperty("holderCenterPosition") Point holderCenterPosition) {
        super(name, holderCenterPosition);
    }
}
