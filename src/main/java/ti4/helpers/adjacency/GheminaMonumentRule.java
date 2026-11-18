package ti4.helpers.adjacency;

import java.util.Set;
import java.util.stream.Collectors;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitKey;
import ti4.service.game.MonumentsService;

final class GheminaMonumentRule implements SymmetricAdjacencyRule {

    @Override
    public void from(AdjacencyContext context, String position, Set<String> adjacent) {
        Game game = context.getGame();
        Player player = context.getPlayer();
        if (player == null || !MonumentsService.isMonumentOnBoard(game, player, "ghemina_monument")) return;
        Tile monumentTile = MonumentsService.getMonumentTile(game, player, "ghemina_monument");
        if (monumentTile == null) return;

        Set<String> structureSystems = context.memo("gheminaStructureSystems", () -> structureSystems(game, player));
        if (position.equals(monumentTile.getPosition())) {
            adjacent.addAll(structureSystems);
        } else if (structureSystems.contains(position)) {
            adjacent.add(monumentTile.getPosition());
        }
    }

    private static Set<String> structureSystems(Game game, Player player) {
        return game.getTileMap().values().stream()
                .filter(tile -> hasTwoOfAStructure(tile, player))
                .map(Tile::getPosition)
                .collect(Collectors.toSet());
    }

    private static boolean hasTwoOfAStructure(Tile tile, Player player) {
        return tile.getUnitHolders().values().stream()
                .flatMap(holder -> holder.getUnitKeysForPlayer(player).stream())
                .filter(unitKey -> player.getUnitFromUnitKey(unitKey) != null)
                .filter(unitKey -> player.getUnitFromUnitKey(unitKey).getIsStructure())
                .map(UnitKey::unitType)
                .distinct()
                .anyMatch(unitType -> tile.getUnitHolders().values().stream()
                                .mapToInt(holder -> holder.getUnitCount(unitType, player))
                                .sum()
                        >= 2);
    }
}
