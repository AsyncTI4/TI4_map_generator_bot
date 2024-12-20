package ti4.helpers;

import ti4.helpers.Units.UnitKey;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;

public class ExploreHelper {

    public static String checkForMechOrRemoveInf(String planetName, Game game, Player player) {
        String message;
        Tile tile = game.getTile(AliasHandler.resolveTile(planetName));
        UnitHolder unitHolder = tile.getUnitHolders().get(planetName);
        int numMechs = 0;
        int numInf = 0;
        String colorID = Mapper.getColorID(player.getColor());
        UnitKey mechKey = Mapper.getUnitKey("mf", colorID);
        UnitKey infKey = Mapper.getUnitKey("gf", colorID);
        if (unitHolder.getUnits() != null) {

            if (unitHolder.getUnits().get(mechKey) != null) {
                numMechs = unitHolder.getUnits().get(mechKey);
            }
            if (unitHolder.getUnits().get(infKey) != null) {
                numInf = unitHolder.getUnits().get(infKey);
            }
        }
        if (numMechs > 0 || numInf > 0) {
            if (numMechs > 0) {
                message = planetName + " has a mech. ";
            } else {
                message = planetName + " does not have a mech, so 1 infantry is being removed (" + numInf + "->" + (numInf - 1) + "). ";
                tile.removeUnit(planetName, infKey, 1);
            }
        } else {
            message = planetName + " did not have a mech or an infantry. Please try again.";
        }
        return message;
    }

    public static String getUnitListEmojisOnPlanetForHazardousExplorePurposes(Game game, Player player, String planetID) {
        String message = "";
        Planet planet = game.getUnitHolderFromPlanet(planetID);
        if (planet != null) {
            String planetName = Mapper.getPlanet(planetID) == null ? "`error?`" : Mapper.getPlanet(planetID).getName();
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
