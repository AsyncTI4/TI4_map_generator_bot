package ti4.helpers.adjacency;

import java.util.Set;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitType;
import ti4.service.game.MonumentsService;

final class SaarMonumentRule implements SymmetricAdjacencyRule {

    @Override
    public void from(AdjacencyContext context, String position, Set<String> adjacent) {
        Game game = context.getGame();
        Player player = context.getPlayer();
        if (player == null || !MonumentsService.isMonumentOnBoard(game, player, "saar_monument")) return;
        Tile monumentTile = MonumentsService.getMonumentTile(game, player, "saar_monument");
        String spaceDockPosition = game.getStoredValue("saarMonumentSpaceDock_" + player.getFaction());
        Tile spaceDockTile = game.getTileByPosition(spaceDockPosition);
        if (monumentTile == null
                || spaceDockTile == null
                || !ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Spacedock)
                        .contains(spaceDockTile)) {
            return;
        }
        if (position.equals(monumentTile.getPosition())) {
            adjacent.add(spaceDockPosition);
        } else if (position.equals(spaceDockPosition)) {
            adjacent.add(monumentTile.getPosition());
        }
    }
}
