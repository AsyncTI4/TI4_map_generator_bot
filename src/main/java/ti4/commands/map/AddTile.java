package ti4.commands.map;

import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Tile;
import ti4.map.UnitHolder;

import java.util.HashMap;

public class AddTile extends AddRemoveTile {
    public AddTile() {
        super(Constants.ADD_TILE, "Add tile to map");
        //addOption(OptionType.STRING, Constants.TILE_NAME, "Tile name", true);
    }

    @Override
    protected void tileAction(Tile tile, String position, Game userActiveGame) {
        userActiveGame.removeTile(position); //remove old tile first to clean up associated planet ownership
        userActiveGame.setTile(tile);
        addCustodianToken(tile);
    }

    public static void addCustodianToken(Tile tile) {
        if (tile.getTileID().equals("18")) {
            HashMap<String, UnitHolder> unitHolders = tile.getUnitHolders();
            for (UnitHolder unitHolder : unitHolders.values()) {
                if (unitHolder instanceof Planet && unitHolder.getName().equals("mr")) {
                    unitHolder.addToken(Constants.CUSTODIAN_TOKEN_PNG);
                }
            }
        }
    }
}
