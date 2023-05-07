package ti4.helpers;

import ti4.map.Map;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;

public class DiscordantStarsHelper {
    public static void checkGardenWorlds(Map map) {
        for (Player player : map.getPlayers().values()) {
            if (player.getFactionAbilities().contains(Constants.GARDEN_WORLDS)) {
                for (Tile tile : map.getTileMap().values()) {
                    for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                        if (unitHolder != null && unitHolder instanceof Planet) {
                            Planet planet = (Planet) unitHolder;
                            if (player.getPlanets().contains(planet.getName())) {
                                if (planet.hasGroundForces() && planet.getTokenList().contains(Constants.GARDEN_WORLDS_PNG)) {
                                    planet.removeToken(Constants.GARDEN_WORLDS_PNG);
                                } else {
                                    planet.addToken(Constants.GARDEN_WORLDS_PNG);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
