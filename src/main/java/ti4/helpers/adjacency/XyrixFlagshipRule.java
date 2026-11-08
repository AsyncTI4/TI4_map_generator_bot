package ti4.helpers.adjacency;

import java.util.HashSet;
import java.util.Set;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

final class XyrixFlagshipRule implements SymmetricAdjacencyRule {

    private static final String XYRIX_FLAGSHIP = "xyrix_flagship";
    private static final ThreadLocal<Boolean> RESOLVING = ThreadLocal.withInitial(() -> false);

    @Override
    public void from(AdjacencyContext context, String position, Set<String> adjacent) {
        if (!context.isMovement() || RESOLVING.get()) return;
        Player player = context.getPlayer();
        if (player == null) return;
        Game game = context.getGame();
        Set<String> flagshipPositions = game.getTileMap().values().stream()
                .filter(tile -> hasFlagship(tile, player))
                .map(Tile::getPosition)
                .collect(java.util.stream.Collectors.toSet());
        if (flagshipPositions.isEmpty()) return;

        RESOLVING.set(true);
        try {
            Set<String> linkedSystems = new HashSet<>();
            for (Tile shipTile : game.getTileMap().values()) {
                if (!hasPlayerShip(shipTile, player)) continue;
                linkedSystems.add(shipTile.getPosition());
                linkedSystems.addAll(Adjacency.of(game, player).forMovement().from(shipTile.getPosition()));
            }
            for (String flagshipPosition : flagshipPositions) {
                if (position.equals(flagshipPosition)) {
                    adjacent.addAll(linkedSystems);
                }
                if (linkedSystems.contains(position)) {
                    adjacent.add(flagshipPosition);
                }
            }
        } finally {
            RESOLVING.remove();
        }
    }

    private boolean hasPlayerShip(Tile tile, Player player) {
        return tile.getSpaceUnitHolder().getUnitKeysForPlayer(player).stream()
                .anyMatch(unitKey -> isShip(unitKey, player));
    }

    private boolean hasFlagship(Tile tile, Player player) {
        return player.hasUnit(XYRIX_FLAGSHIP)
                && tile.getSpaceUnitHolder().getUnitKeysForPlayer(player).stream()
                        .anyMatch(unitKey -> unitKey.unitType() == UnitType.Flagship);
    }

    private boolean isShip(UnitKey unitKey, Player player) {
        return switch (unitKey.unitType()) {
            case Fighter, Destroyer, Cruiser, Carrier, Dreadnought, Flagship, Warsun, Cavalry, TyrantsLament -> true;
            default -> {
                UnitModel unit = player.getUnitFromUnitKey(unitKey);
                yield unit != null && unit.getIsShip();
            }
        };
    }
}
