package ti4.helpers;

import java.io.Serializable;
import java.util.Comparator;
import ti4.helpers.Units.UnitKey;

class ShipRepairComparator implements Comparator<UnitKey>, Serializable {

    @Override
    public int compare(UnitKey o1, UnitKey o2) {
        return Integer.compare(getAssignedValue(o1), getAssignedValue(o2));
    }

    private int getAssignedValue(UnitKey ship) {
        return switch (ship.getUnitType()) {
            case Cruiser -> 4; // SE2 can have sustained damage
            case Dreadnought -> 3;
            case Flagship -> 2;
            case Warsun -> 1;
            default -> 0;
        };
    }
}
