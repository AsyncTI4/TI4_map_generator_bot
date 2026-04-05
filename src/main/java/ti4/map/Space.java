package ti4.map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.awt.Point;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ti4.helpers.Units;

@JsonTypeName("space")
public class Space extends UnitHolder {

    public Space(String name, Point holderCenterPosition) {
        super(name, holderCenterPosition);
    }

    @JsonCreator
    public Space(
            @JsonProperty("name") String name,
            @JsonProperty("holderCenterPosition") Point holderCenterPosition,
            @JsonProperty("unitsByState") Map<Units.UnitKey, List<Integer>> unitsByState,
            @JsonProperty("ccList") Set<String> ccList,
            @JsonProperty("controlList") Set<String> controlList,
            @JsonProperty("tokenList") Set<String> tokenList) {
        super(name, holderCenterPosition, unitsByState, ccList, controlList, tokenList);
    }

    public String getRepresentation(Game game) {
        return "Space";
    }
}
