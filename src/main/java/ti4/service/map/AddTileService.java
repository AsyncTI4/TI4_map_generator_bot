package ti4.service.map;

import java.util.Map;

import lombok.experimental.UtilityClass;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Tile;
import ti4.map.UnitHolder;

@UtilityClass
public class AddTileService {

    public static void addTile(Game game, Tile tile) {
        game.removeTile(tile.getPosition()); //remove old tile first to clean up associated planet ownership
        game.setTile(tile);
        addCustodianToken(tile);
    }

    public static void addCustodianToken(Tile tile) {
        if (!tile.isMecatol()) {
            return;
        }
        Map<String, UnitHolder> unitHolders = tile.getUnitHolders();
        for (String mecatol : Constants.MECATOLS) {
            UnitHolder unitHolder = unitHolders.get(mecatol);
            if (unitHolder instanceof Planet && mecatol.equals(unitHolder.getName())) {
                unitHolder.addToken(Constants.CUSTODIAN_TOKEN_PNG);
            }
        }
    }
}
