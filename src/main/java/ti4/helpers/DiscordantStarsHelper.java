package ti4.helpers;

import ti4.helpers.Helper;
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
    public static void checkOlradinMech(Map activeMap) {
        for (Player player : activeMap.getPlayers().values()) {
            int countP = countOlradinPolicies(player);
            //MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(),"Count was " + countP.toString());
            if (countP > 1) {                                 
                for (Tile tile : activeMap.getTileMap().values()) {
                    for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                        if (unitHolder != null && unitHolder instanceof Planet) {
                            Planet planet = (Planet) unitHolder;
                            if (player.getPlanets().contains(planet.getName())) {
                                                                
                                if (!Helper.oneMechCheck(planet.getName(), activeMap, player) && ((planet.getTokenList().contains(Constants.OLRADIN_MECH_INF_PNG)) || (planet.getTokenList().contains(Constants.OLRADIN_MECH_RES_PNG)))) {
                                    planet.removeToken(Constants.OLRADIN_MECH_INF_PNG);
                                    planet.removeToken(Constants.OLRADIN_MECH_RES_PNG);
                                } else if (Helper.oneMechCheck(planet.getName(), activeMap, player)) {
                                    planet.addToken(Constants.OLRADIN_MECH_INF_PNG);
                                    planet.removeToken(Constants.OLRADIN_MECH_RES_PNG);
                                }
                            }
                        }
                    }
                }
            }
            else if (countP < 1) {                               
                for (Tile tile : activeMap.getTileMap().values()) {
                    for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                        if (unitHolder != null && unitHolder instanceof Planet) {
                            Planet planet = (Planet) unitHolder;
                            if (player.getPlanets().contains(planet.getName())) {
                                                                
                                if (!Helper.oneMechCheck(planet.getName(), activeMap, player) && ((planet.getTokenList().contains(Constants.OLRADIN_MECH_INF_PNG)) || (planet.getTokenList().contains(Constants.OLRADIN_MECH_RES_PNG)))) {
                                    planet.removeToken(Constants.OLRADIN_MECH_INF_PNG);
                                    planet.removeToken(Constants.OLRADIN_MECH_RES_PNG);
                                } else if (Helper.oneMechCheck(planet.getName(), activeMap, player)){
                                    planet.addToken(Constants.OLRADIN_MECH_RES_PNG);
                                    planet.removeToken(Constants.OLRADIN_MECH_INF_PNG);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public static int countOlradinPolicies(Player player) 
    {   int total = 0;
        if (player.hasAbility("policy_the_people_connect"))
        {
            total++;
        }
        if (player.hasAbility("policy_the_environment_preserve"))
        {
            total++;
        }
        if (player.hasAbility("policy_the_economy_empower"))
        {
            total++;
        }
        if (player.hasAbility("policy_the_people_control"))
        {
            total--;
        }
        if (player.hasAbility("policy_the_environment_plunder"))
        {
            total--;
        }
        if (player.hasAbility("policy_the_economy_exploit"))
        {
            total--;
        }

        return total;
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
