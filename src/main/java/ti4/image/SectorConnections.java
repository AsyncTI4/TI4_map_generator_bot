package ti4.image;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.FoWHelper;

final class SectorConnections {

    record Connection(String position, String sectorName) {}

    private SectorConnections() {}

    // TODO: adjacency is read from the live map, so a change on a remembered target (e.g. a removed wormhole
    // token) can add or drop that target from the strip before the player sees it again
    static List<Connection> find(
            Game game,
            Player player,
            @Nullable MapSegment shown,
            Collection<String> visibleSources,
            Set<String> known) {
        List<MapSegment> segments = MapSegment.all(game);
        Set<String> uncoveredMain = MapSegment.uncoveredMainPositions(game, segments);
        Set<String> shownPositions = shown == null ? mainViewPositions(game) : shown.positions();
        Map<String, String> sectorByPosition = sectorByPosition(game, segments, shown, uncoveredMain);
        Map<String, Connection> connections = new TreeMap<>(MapFrame.POSITION_ORDER);
        for (String source : visibleSources) {
            if (!shownPositions.contains(source)) {
                continue;
            }
            for (String target : FoWHelper.getAdjacentTiles(game, source, player, true, false)) {
                String sector = sectorByPosition.get(target);
                if (sector != null && !shownPositions.contains(target) && known.contains(target)) {
                    connections.putIfAbsent(target, new Connection(target, sector));
                }
            }
        }
        return List.copyOf(connections.values());
    }

    private static Set<String> mainViewPositions(Game game) {
        return game.getTileMap().keySet().stream()
                .filter(position -> !MapSegment.isDetachedPosition(game, position))
                .collect(Collectors.toSet());
    }

    private static Map<String, String> sectorByPosition(
            Game game, List<MapSegment> segments, @Nullable MapSegment shown, Set<String> uncoveredMain) {
        Map<String, String> sectorByPosition = new HashMap<>();
        for (MapSegment segment : segments) {
            if (shown != null && segment.name().equals(shown.name())) {
                continue;
            }
            String label = segment.displayName(game);
            for (String position : segment.positions()) {
                sectorByPosition.putIfAbsent(position, label);
            }
        }
        if (shown != null) {
            String mainName = MapSegment.mainDisplayName(game);
            uncoveredMain.forEach(position -> sectorByPosition.putIfAbsent(position, mainName));
        }
        return sectorByPosition;
    }
}
