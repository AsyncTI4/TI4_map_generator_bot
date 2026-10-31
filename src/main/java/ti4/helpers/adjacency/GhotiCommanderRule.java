package ti4.helpers.adjacency;

import java.util.HashSet;
import java.util.Set;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.FoWHelper;

final class GhotiCommanderRule implements SymmetricAdjacencyRule {

    @Override
    public void from(AdjacencyContext context, String position, Set<String> adjacent) {
        Set<Tile> emptyTiles = linkedEmptyTiles(context);
        Tile tile = context.getGame().getTileByPosition(position);
        if (tile == null || !emptyTiles.contains(tile)) return;
        emptyTiles.forEach(emptyTile -> adjacent.add(emptyTile.getPosition()));
    }

    private static Set<Tile> linkedEmptyTiles(AdjacencyContext context) {
        return context.memo("ghotiEmptyTiles", () -> computeLinkedEmptyTiles(context));
    }

    private static Set<Tile> computeLinkedEmptyTiles(AdjacencyContext context) {
        Game game = context.getGame();
        Player player = context.getPlayer();
        if (!context.isActivePlayer()
                || !context.hasActivatedSystem()
                || !game.playerHasLeaderUnlockedOrAlliance(player, "ghoticommander")) {
            return Set.of();
        }
        Set<Player> playersToCheck = new HashSet<>();
        playersToCheck.add(player);
        if (game.isAllianceMode()) {
            game.getRealPlayers().stream()
                    .filter(alliancePlayer -> player.getAllianceMembers().contains(alliancePlayer.getFaction()))
                    .forEach(playersToCheck::add);
        }
        Set<Tile> emptyTiles = FoWHelper.getEmptyTiles(game);
        boolean containsUnits =
                emptyTiles.stream().anyMatch(tile -> playersToCheck.stream().anyMatch(tile::containsPlayersUnits));
        return containsUnits ? emptyTiles : Set.of();
    }
}
