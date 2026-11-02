package ti4.helpers.adjacency;

import java.util.List;
import java.util.Set;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitType;

final class SpaceDockWebwayRule implements SymmetricAdjacencyRule {

    @Override
    public void from(AdjacencyContext context, String position, Set<String> adjacent) {
        Game game = context.getGame();
        Player player = context.getPlayer();
        // TODO: the webway says "your ships"; should structures moved by Nano-Link Permit be excluded?
        if (!context.isMovement() || !context.isActivePlayer() || !context.hasActivatedSystem()) return;
        if (!game.playerHasLeaderUnlockedOrAlliance(player, "celdauricommander")
                && !player.hasTech("tf-starbasewebway")) {
            return;
        }
        List<Tile> spaceDockTiles = ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Spacedock);
        if (!spaceDockTiles.contains(game.getTileByPosition(position))) return;
        for (Tile tile : spaceDockTiles) {
            if (!tile.getPosition().equalsIgnoreCase(position)) {
                adjacent.add(tile.getPosition());
            }
        }
    }
}
