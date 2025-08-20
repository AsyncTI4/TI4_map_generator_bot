package ti4.service.unit;

import lombok.Getter;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Units;

@Getter
public class ParsedUnit {

    private final Units.UnitKey unitKey;
    private final int count;
    private final String location;

    public ParsedUnit(Units.UnitKey unitKey) {
        this.unitKey = unitKey;
        count = 1;
        location = Constants.SPACE;
    }

    public ParsedUnit(Units.UnitKey unitKey, int count, String location) {
        this.unitKey = unitKey;
        this.count = count;
        this.location = Constants.SPACE.equalsIgnoreCase(location)
                ? Constants.SPACE
                : AliasHandler.resolvePlanet(location.toLowerCase());
    }
}
