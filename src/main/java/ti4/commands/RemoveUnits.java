package ti4.commands;

import ti4.helpers.Constants;
import ti4.map.Tile;

public class RemoveUnits extends AddRemoveUnits {
    @Override
    protected void unitAction(Tile tile, int count, String planetName, String unitID) {
        tile.removeUnit(planetName, unitID, count);
    }

    @Override
    protected String getActionID() {
        return Constants.REMOVE_UNITS;
    }
}
