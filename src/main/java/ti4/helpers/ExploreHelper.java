package ti4.helpers;

import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;

public class ExploreHelper {

    public static boolean checkForMech(String planetName, Game game, Player player) {
        Tile tile = game.getTile(AliasHandler.resolveTile(planetName));
        UnitHolder unitHolder = tile.getUnitHolders().get(planetName);
        UnitKey mechKey = Units.getUnitKey(UnitType.Mech, player.getColorID());
        return unitHolder.getUnitCount(mechKey) > 0;
    }

    public static boolean checkForInf(String planetName, Game game, Player player) {
        Tile tile = game.getTile(AliasHandler.resolveTile(planetName));
        UnitHolder unitHolder = tile.getUnitHolders().get(planetName);
        UnitKey infKey = Units.getUnitKey(UnitType.Infantry, player.getColorID());
        return unitHolder.getUnitCount(infKey) > 0;
    }

    public static String getUnitListEmojisOnPlanetForHazardousExplorePurposes(
            Game game, Player player, String planetID) {
        String message = "";
        Planet planet = game.getUnitHolderFromPlanet(planetID);
        if (planet != null) {
            String planetName = Mapper.getPlanet(planetID) == null
                    ? "`error?`"
                    : Mapper.getPlanet(planetID).getName();
            String unitList = planet.getPlayersUnitListEmojisOnHolder(player);
            if (unitList.isEmpty()) {
                message += "no units on " + planetName;
            } else {
                message += unitList + " on " + planetName;
            }
        }
        return message;
    }
}
