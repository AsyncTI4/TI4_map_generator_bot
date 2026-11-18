package ti4.service.megagame;

import java.util.HashSet;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.adjacency.Adjacency;
import ti4.model.Mapper;

@UtilityClass
public class MegagamePlayerReach {

    public static Set<String> presence(Game game, Player player) {
        Set<String> positions = new HashSet<>();
        for (Tile tile : game.getTileMap().values()) {
            if (hasPresence(game, player, tile)) {
                positions.add(tile.getPosition());
            }
        }
        return positions;
    }

    public static Set<String> withinDistance(Game game, Player player, Set<String> seeds, int distance) {
        Adjacency adjacency = Adjacency.of(game, player).forVision();
        Set<String> reached = new HashSet<>(seeds);
        Set<String> frontier = new HashSet<>(seeds);
        for (int step = 0; step < distance && !frontier.isEmpty(); step++) {
            Set<String> next = new HashSet<>();
            for (String position : frontier) {
                for (String neighbour : adjacency.from(position)) {
                    if (reached.add(neighbour)) {
                        next.add(neighbour);
                    }
                }
            }
            frontier = next;
        }
        return reached;
    }

    private static boolean hasPresence(Game game, Player player, Tile tile) {
        return FoWHelper.playerIsInSystem(game, tile, player, false)
                || CommandCounterHelper.hasCC(player, tile)
                || hasControlToken(player, tile);
    }

    private static boolean hasControlToken(Player player, Tile tile) {
        String controlId = Mapper.getControlID(player.getColor());
        return controlId != null
                && tile.getUnitHolders().values().stream()
                        .anyMatch(holder -> holder.getControlList().contains(controlId));
    }
}
