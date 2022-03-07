package ti4.commands;

import ti4.helpers.Constants;
import ti4.map.Tile;

public class AddUnits extends AddRemoveUnits {
    @Override
    protected void unitAction(Tile tile, int count, String planetName, String unitID) {
        tile.addUnit(planetName, unitID, count);
    }

    @Override
    protected String getActionID() {
        return Constants.ADD_UNITS;
    }

    @Override
    protected String getActionDescription() {
        return "Add units to map";
    }


}
