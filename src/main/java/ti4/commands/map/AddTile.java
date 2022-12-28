package ti4.commands.map;

import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.Planet;
import ti4.map.Tile;
import ti4.map.UnitHolder;

import java.util.HashMap;

public class AddTile extends AddRemoveTile {

    @Override
    public String getActionID() {
        return Constants.ADD_TILE;
    }

    @Override
    protected void tileAction(Tile tile, String position, Map userActiveMap) {
        userActiveMap.setTile(tile);
        if (tile.getTileID().equals("18")){
            HashMap<String, UnitHolder> unitHolders = tile.getUnitHolders();
            for (UnitHolder unitHolder : unitHolders.values()) {
                if (unitHolder instanceof Planet && unitHolder.getName().equals("mr")){
                    unitHolder.addToken(Constants.CUSTODIAN_TOKEN_PNG);
                }
            }
        }
    }

    @Override
    protected String getActionDescription() {
        return "Add tile to map";
    }
}
