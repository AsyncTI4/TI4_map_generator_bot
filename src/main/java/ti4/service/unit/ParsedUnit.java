package ti4.service.unit;

import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Units;

public record ParsedUnit(Units.UnitKey unitKey, int count, String location) {

    public ParsedUnit(Units.UnitKey unitKey) {
        this(unitKey, 1, Constants.SPACE);
    }

    public ParsedUnit(Units.UnitKey unitKey, int count, String location) {
        this.unitKey = unitKey;
        this.count = count;
        this.location = Constants.SPACE.equalsIgnoreCase(location)
                ? Constants.SPACE
                : AliasHandler.resolvePlanet(location.toLowerCase());
    }
}
