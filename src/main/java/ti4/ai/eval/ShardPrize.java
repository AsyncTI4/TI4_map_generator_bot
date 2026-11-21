package ti4.ai.eval;

import lombok.experimental.UtilityClass;
import ti4.ai.legendary.LegendaryPlanets;
import ti4.ai.scoring.ObjectiveValue;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;

@UtilityClass
public class ShardPrize {

    public static final String SHARD = "shard";

    public static double stake(Game game, Player seat, Planet planet) {
        return fallsWithPlanet(game, seat, planet) ? ObjectiveValue.VICTORY_POINT_VALUE : 0;
    }

    public static double gain(Game game, Player seat, Planet planet) {
        Player holder = BoardView.controller(game, planet.getName());
        if (holder == null || holder == seat) return 0;
        return fallsWithPlanet(game, holder, planet) ? ObjectiveValue.VICTORY_POINT_VALUE : 0;
    }

    private static boolean fallsWithPlanet(Game game, Player holder, Planet planet) {
        if (!holder.hasRelic(SHARD) || !holder.getPlanets().contains(planet.getName())) return false;
        if (LegendaryPlanets.isLegendary(game, planet.getName())) return true;
        Tile home = holder.getHomeSystemTile();
        return home != null && game.getTileFromPlanet(planet.getName()) == home;
    }
}
