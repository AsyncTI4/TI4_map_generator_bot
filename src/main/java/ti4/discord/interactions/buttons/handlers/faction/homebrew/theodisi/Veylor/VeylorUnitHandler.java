package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Veylor;

import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;

@UtilityClass
public class VeylorUnitHandler {
    
    // Vox Sentinels
    public static void checkVeylorMech(Game activeMap) {
        for (Player player : activeMap.getPlayers().values()) {
            String tokenToAddOrRemove = Constants.VOX_SENTINELS_PNG;
            if (player.ownsUnit("veylor_mech")) {
                for (Tile tile : activeMap.getTileMap().values()) {
                for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                    if (unitHolder instanceof Planet planet) {
                        if (player.getPlanets().contains(planet.getName())) {
                                if (!oneMechCheck(planet.getName(), activeMap, player)
                                        && ((planet.getTokenList().contains(tokenToAddOrRemove)))) {
                                    planet.removeToken(tokenToAddOrRemove);
                                } else if (oneMechCheck(planet.getName(), activeMap, player)) {
                                    planet.addToken(tokenToAddOrRemove);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean oneMechCheck(String planetName, Game activeMap, Player player) {
        Tile tile = activeMap.getTile(AliasHandler.resolveTile(planetName));
        if (tile == null) return false;
        UnitHolder unitHolder = tile.getUnitHolders().get(planetName);
        int numMechs = 0;

        String colorID = Mapper.getColorID(player.getColor());
        if (unitHolder.getUnits() != null) {
            numMechs = unitHolder.getUnitCount(UnitType.Mech, colorID);
        }
        return numMechs >= 1;
    }
}
