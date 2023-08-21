package ti4.helpers;

import ti4.map.Map;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;

public class DiscordantStarsHelper {
    public static void checkGardenWorlds(Map activeMap) {
        for (Player player : activeMap.getPlayers().values()) {
            if (player.hasAbility(Constants.GARDEN_WORLDS)) {
                for (Tile tile : activeMap.getTileMap().values()) {
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
    public static void checkSigil(Map activeMap) { //Edyn Mech adds Sigil tokens under them
        for (Player player : activeMap.getPlayers().values()) {
            if (player.ownsUnit("edyn_mech")) {
                for (Tile tile : activeMap.getTileMap().values()) {
                    if (Helper.playerHasMechInSystem(tile, activeMap, player)) {
                        tile.addToken(Constants.SIGIL, Constants.SPACE);
                    } else {
                        tile.removeToken(Constants.SIGIL, Constants.SPACE);
                    }
                }
            }
        }
    }
}
