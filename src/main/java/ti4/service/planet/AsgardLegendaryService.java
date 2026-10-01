package ti4.service.planet;

import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;

@UtilityClass
public class AsgardLegendaryService {

    private static final String BIFROST_BRIDGE = "asgardBifrostBridge_";

    public static void activateBifrostBridge(Game game, Player player) {
        game.setStoredValue(BIFROST_BRIDGE + player.getFaction(), "active");
    }

    public static void clearBifrostBridge(Game game, Player player) {
        game.removeStoredValue(BIFROST_BRIDGE + player.getFaction());
    }

    public static boolean isBifrostBridgeActive(Game game, Player player) {
        return player != null && "active".equals(game.getStoredValue(BIFROST_BRIDGE + player.getFaction()));
    }

    public static boolean isPrintedGravityRift(Tile tile) {
        return tile != null
                && tile.getTileModel() != null
                && tile.getTileModel().isGravityRift();
    }

    public static void addBifrostBridgeAdjacencies(
            Game game, Player player, String position, Set<String> adjacentPositions) {
        if (!isBifrostBridgeActive(game, player)) return;
        Tile tile = game.getTileByPosition(position);
        if (!isPrintedGravityRift(tile)) return;
        game.getTileMap().values().stream()
                .filter(AsgardLegendaryService::isPrintedGravityRift)
                .map(Tile::getPosition)
                .forEach(adjacentPositions::add);
    }
}
