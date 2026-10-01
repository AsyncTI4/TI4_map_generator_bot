package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.dream;

import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;

@UtilityClass
public class DreamFactionTechHandler {
    private static final String NON_EUCLIDEAN_GEOMETRIES = "bedreamneg";

    public static boolean getsNonEuclideanMoveBonus(Game game, Player player, Tile tile) {
        return isDuringPlayersTacticalAction(game, player)
                && DreamAbilitiesHandler.hasNexusTokenOrDreamFlagship(game, tile);
    }

    public static boolean treatsNebulasAsAdjacent(Game game, Player player, Tile tile) {
        return isDuringPlayersTacticalAction(game, player) && tile != null && tile.isNebula(game);
    }

    public static Set<String> getNebulaAdjacencies(Game game, Player player, Tile tile) {
        if (!treatsNebulasAsAdjacent(game, player, tile)) {
            return Set.of();
        }

        return game.getTileMap().values().stream()
                .filter(candidate -> !candidate.getPosition().equals(tile.getPosition()))
                .filter(candidate -> candidate.isNebula(game))
                .map(Tile::getPosition)
                .collect(Collectors.toSet());
    }

    private static boolean isDuringPlayersTacticalAction(Game game, Player player) {
        return game != null
                && player != null
                && player.equals(game.getActivePlayer())
                && "action".equalsIgnoreCase(game.getPhaseOfGame())
                && game.getActiveSystem() != null
                && !game.getActiveSystem().isBlank()
                && player.hasTech(NON_EUCLIDEAN_GEOMETRIES);
    }
}
