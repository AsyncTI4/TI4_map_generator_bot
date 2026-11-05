package ti4.service.objectives.fog;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.FoWHelper;
import ti4.model.TileModel;

@UtilityClass
class FogSight {

    static Set<String> visible(Game game, Player player) {
        return FoWHelper.getTilePositionsToShow(game, player);
    }

    static Set<String> found(Game game, Player player) {
        Set<String> found = new HashSet<>(player.getFogTiles().keySet());
        found.addAll(visible(game, player));
        found.removeIf(position -> !isCountableSystem(game.getTileByPosition(position)));
        return found;
    }

    static Map<Player, Set<String>> visionOfOthers(Game game, Player player) {
        Map<Player, Set<String>> vision = new HashMap<>();
        for (Player other : others(game, player)) {
            vision.put(other, visible(game, other));
        }
        return vision;
    }

    static boolean seenByAny(Map<Player, Set<String>> visionOfOthers, String position) {
        return visionOfOthers.values().stream().anyMatch(seen -> seen.contains(position));
    }

    static boolean seenByAny(Map<Player, Set<String>> visionOfOthers, Collection<String> positions) {
        return positions.stream().anyMatch(position -> seenByAny(visionOfOthers, position));
    }

    static List<Player> others(Game game, Player player) {
        return game.getRealPlayers().stream().filter(other -> other != player).toList();
    }

    static List<Tile> countableSystems(Game game) {
        return game.getTileMap().values().stream()
                .filter(FogSight::isCountableSystem)
                .toList();
    }

    static boolean isCountableSystem(Tile tile) {
        if (tile == null) return false;
        TileModel model = tile.getTileModel();
        return model != null && model.getName() != null && !model.isHyperlane();
    }

    static String homePosition(Player player) {
        Tile home = player.getHomeSystemTile();
        return home == null ? null : home.getPosition();
    }
}
