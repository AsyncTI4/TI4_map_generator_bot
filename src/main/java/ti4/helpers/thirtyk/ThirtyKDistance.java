package ti4.helpers.thirtyk;

import java.util.Map;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.CheckDistanceHelper;

@UtilityClass
public class ThirtyKDistance {

    public static final int FAR_AWAY = 5;

    public static boolean isMoreThanFiveAway(Game game, String fromPosition, String toPosition) {
        if (fromPosition == null || toPosition == null) return false;
        Map<String, Integer> distances =
                CheckDistanceHelper.getTileDistances(game, null, fromPosition, FAR_AWAY + 1, true);
        Integer distance = distances.get(toPosition);
        return distance == null || distance > FAR_AWAY;
    }

    public static boolean isMoreThanFiveFromHome(Game game, Player player, String position) {
        Tile home = player.getHomeSystemTile();
        if (home == null) return false;
        return isMoreThanFiveAway(game, home.getPosition(), position);
    }

    public static boolean homesMoreThanFiveApart(Game game, Player player, Player other) {
        Tile otherHome = other.getHomeSystemTile();
        if (otherHome == null) return false;
        return isMoreThanFiveFromHome(game, player, otherHome.getPosition());
    }
}
