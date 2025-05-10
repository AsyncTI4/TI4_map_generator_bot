package ti4.helpers;

import java.util.Comparator;
import ti4.helpers.Units.UnitKey;

public class ShipRepairComparator implements Comparator<UnitKey> {

    @Override
    public int compare(UnitKey o1, UnitKey o2) {
        return Integer.compare(getAssignedValue(o1), getAssignedValue(o2));
    }

    private int getAssignedValue(UnitKey ship) {
        return switch (ship.getUnitType()) {
            case Units.UnitType.Cruiser -> 4;       // SE2 can have sustained damage
            case Units.UnitType.Dreadnought -> 3;
            case Units.UnitType.Flagship -> 2;
            case Units.UnitType.Warsun -> 1;
            default -> 0;
        };
    }
}