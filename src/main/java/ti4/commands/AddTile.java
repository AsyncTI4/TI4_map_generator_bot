package ti4.commands;

import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.Tile;

public class AddTile extends AddRemoveTile {

    @Override
    protected String getActionID() {
        return Constants.ADD_TILE;
    }

    @Override
    protected void tileAction(Tile tile, String position, Map userActiveMap) {
        userActiveMap.setTile(tile);
    }

    @Override
    protected String getActionDescription() {
        return "Remove tile to map";
    }
}
