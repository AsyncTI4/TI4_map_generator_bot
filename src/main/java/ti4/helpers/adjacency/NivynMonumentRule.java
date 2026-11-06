package ti4.helpers.adjacency;

import java.util.Set;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.service.game.MonumentsService;

final class NivynMonumentRule implements SymmetricAdjacencyRule {

    @Override
    public void from(AdjacencyContext context, String position, Set<String> adjacent) {
        Game game = context.getGame();
        Player player = context.getPlayer();
        if (!context.isMovement()
                || player == null
                || !MonumentsService.isMonumentOnBoard(game, player, "nivyn_monument")) {
            return;
        }
        Tile monumentTile = MonumentsService.getMonumentTile(game, player, "nivyn_monument");
        Tile woundTile = game.getTileMap().values().stream()
                .filter(tile -> tile.getSpaceUnitHolder().getTokenList().contains("token_ds_wound.png"))
                .findFirst()
                .orElse(null);
        if (monumentTile == null || woundTile == null) return;

        if (position.equals(monumentTile.getPosition())) {
            adjacent.add(woundTile.getPosition());
        } else if (position.equals(woundTile.getPosition())) {
            adjacent.add(monumentTile.getPosition());
        }
    }
}
