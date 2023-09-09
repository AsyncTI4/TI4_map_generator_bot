package ti4.helpers;

import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;

public class DiscordantStarsHelper {
    public static void checkGardenWorlds(Game activeGame) {
        for (Player player : activeGame.getPlayers().values()) {
            if (player.hasAbility(Constants.GARDEN_WORLDS)) {
                for (Tile tile : activeGame.getTileMap().values()) {
                    for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                        if (unitHolder != null && unitHolder instanceof Planet planet) {
                            if (player.getPlanets().contains(planet.getName())) {
                                if (planet.hasGroundForces() && planet.getTokenList().contains(Constants.GARDEN_WORLDS_PNG)) {
                                    planet.removeToken(Constants.GARDEN_WORLDS_PNG);
                                } else {
                                    if(!planet.hasGroundForces()){
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
    public static void checkSigil(Game activeGame) { //Edyn Mech adds Sigil tokens under them
        for (Player player : activeGame.getPlayers().values()) {
            if (player.ownsUnit("edyn_mech")) {
                for (Tile tile : activeGame.getTileMap().values()) {
                    if (Helper.playerHasMechInSystem(tile, activeGame, player)) {
                        tile.addToken(Constants.SIGIL, Constants.SPACE);
                    } else {
                        tile.removeToken(Constants.SIGIL, Constants.SPACE);
                    }
                }
            }
        }
    }
}
