package ti4.helpers.adjacency;

import java.util.Set;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;

final class DeepFlagshipRule implements SymmetricAdjacencyRule {

    private static final String DEEP_FLAGSHIP = "deep_flagship";

    @Override
    public void from(AdjacencyContext context, String position, Set<String> adjacent) {
        Player player = context.getPlayer();
        if (!context.isMovement() || player == null) return;
        Game game = context.getGame();
        Tile homeSystem = player.getHomeSystemTile();
        Tile tile = game.getTileByPosition(position);
        if (homeSystem == null || tile == null) return;

        if (ButtonHelper.doesPlayerHaveFSHere(DEEP_FLAGSHIP, player, tile)) {
            adjacent.add(homeSystem.getPosition());
        }
        if (position.equals(homeSystem.getPosition())) {
            game.getTileMap().values().stream()
                    .filter(other -> ButtonHelper.doesPlayerHaveFSHere(DEEP_FLAGSHIP, player, other))
                    .map(Tile::getPosition)
                    .forEach(adjacent::add);
        }
    }
}
