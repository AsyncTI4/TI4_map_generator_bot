package ti4.ai.promise;

import lombok.experimental.UtilityClass;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.FoWHelper;

@UtilityClass
public class Presence {

    public static boolean at(Player player, Tile tile) {
        if (tile == null) return false;
        return FoWHelper.playerHasUnitsInSystem(player, tile)
                || tile.getPlanetUnitHolders().stream().map(Planet::getName).anyMatch(player.getPlanets()::contains);
    }
}
