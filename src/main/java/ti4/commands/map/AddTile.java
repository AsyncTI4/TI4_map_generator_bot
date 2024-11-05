package ti4.commands.map;

import java.util.Map;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Tile;
import ti4.map.UnitHolder;

public class AddTile extends AddRemoveTile {
    public AddTile() {
        super(Constants.ADD_TILE, "Add tile to map");
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "Tile name", true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.POSITION, "Tile position on map", true));
    }

    @Override
    protected void tileAction(Tile tile, String position, Game game) {
        addTile(game, tile);
    }

    public static void addTile(Game game, Tile tile) {
        game.removeTile(tile.getPosition()); //remove old tile first to clean up associated planet ownership
        game.setTile(tile);
        addCustodianToken(tile);
    }

    public static void addCustodianToken(Tile tile) {
        if (tile.isMecatol()) {
            Map<String, UnitHolder> unitHolders = tile.getUnitHolders();
            for (String mecatol : Constants.MECATOLS) {
                UnitHolder unitHolder = unitHolders.get(mecatol);
                if (unitHolder instanceof Planet && mecatol.equals(unitHolder.getName())) {
                    unitHolder.addToken(Constants.CUSTODIAN_TOKEN_PNG);
                }
            }
        }
    }
}
