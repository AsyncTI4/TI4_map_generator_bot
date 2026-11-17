package ti4.service.map;

import java.util.Map;
import lombok.experimental.UtilityClass;
import ti4.game.Game;

@UtilityClass
public class TileBoundData {

    public static void move(Game game, String from, String to, boolean swap) {
        if (from.equals(to)) {
            return;
        }
        moveAll(game, swap ? Map.of(from, to, to, from) : Map.of(from, to));
    }

    public static void moveAll(Game game, Map<String, String> fromTo) {
        HyperlaneBridges.moveAll(game, fromTo);
        HyperlanePortals.moveAll(game, fromTo);
    }

    public static void clear(Game game, String position) {
        HyperlaneBridges.remove(game, position);
        HyperlanePortals.remove(game, position);
    }
}
