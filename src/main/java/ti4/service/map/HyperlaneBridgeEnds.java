package ti4.service.map;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.adjacency.GridRule;
import ti4.model.MapPositionMapper;
import ti4.model.Mapper;

@UtilityClass
public class HyperlaneBridgeEnds {

    public record BridgeEnd(int side, boolean entry, boolean exit) {

        public boolean isOneWay() {
            return entry != exit;
        }
    }

    @FunctionalInterface
    public interface LaneSource {
        @Nullable
        String lanesOf(Tile customHyperlane);
    }

    public static int facingSide(int systemSide) {
        return (systemSide + 3) % 6;
    }

    public static Optional<BridgeEnd> endOn(int systemSide, int[][] neighbourLanes) {
        int edge = facingSide(systemSide);
        boolean entry = false;
        boolean exit = false;
        for (int other = 0; other < 6; other++) {
            if (other == edge) continue;
            entry |= neighbourLanes[other][edge] == 1;
            exit |= neighbourLanes[edge][other] == 1;
        }
        return entry || exit ? Optional.of(new BridgeEnd(systemSide, entry, exit)) : Optional.empty();
    }

    public static List<BridgeEnd> bridgeable(Map<Integer, BridgeEnd> endsBySide) {
        List<BridgeEnd> ends = new ArrayList<>(new TreeMap<>(endsBySide).values());
        return ends.size() < 2 ? List.of() : List.copyOf(ends);
    }

    public static List<BridgeEnd> forSystem(Game game, @Nullable Player player, String position, LaneSource lanes) {
        Tile system = game.getTileByPosition(position);
        if (system == null || isHyperlane(system) || !HyperlaneStyleService.showsBridges(game)) {
            return List.of();
        }
        return forSystemTile(game, player, system, lanes);
    }

    public static List<BridgeEnd> forSystemTile(Game game, @Nullable Player player, Tile system, LaneSource lanes) {
        List<String> neighbours = MapPositionMapper.getAdjacentTilePositions(system.getPosition());
        if (isHyperlane(system) || neighbours == null || neighbours.size() != 6) {
            return List.of();
        }
        Map<Integer, BridgeEnd> ends = new HashMap<>();
        for (int side = 0; side < 6; side++) {
            BridgeEnd end = endTowards(game, player, system.getPosition(), side, neighbours.get(side), lanes);
            if (end != null) ends.put(side, end);
        }
        return bridgeable(ends);
    }

    @Nullable
    private static BridgeEnd endTowards(
            Game game, @Nullable Player player, String position, int side, String neighbourPosition, LaneSource lanes) {
        Tile neighbour = game.getTileByPosition(neighbourPosition);
        if (neighbour == null || !CustomHyperlaneService.isCustomHyperlaneTile(neighbour)) {
            return null;
        }
        String matrix = lanes.lanesOf(neighbour);
        if (matrix == null) {
            return null;
        }
        BridgeEnd end = endOn(side, CustomHyperlaneService.parseMatrix(matrix)).orElse(null);
        if (end == null) {
            return null;
        }
        int edge = facingSide(side);
        boolean entry = end.entry() && leadsStraightOver(game, player, neighbourPosition, edge, position, side);
        boolean exit = end.exit() && leadsStraightOver(game, player, position, side, neighbourPosition, edge);
        return entry || exit ? new BridgeEnd(side, entry, exit) : null;
    }

    private static boolean leadsStraightOver(
            Game game, @Nullable Player player, String from, int direction, String to, int arrivingSide) {
        return game.getAdjacentTileOverride(from, direction) == null
                && !GridRule.isBorderBlocked(game, player, from, direction, to, arrivingSide);
    }

    public static boolean isHyperlane(Tile tile) {
        return CustomHyperlaneService.isCustomHyperlaneTile(tile) || Mapper.getHyperlaneData(tile.getTileID()) != null;
    }

    public static Set<Integer> passThroughSides(Game game, @Nullable Player player, String position) {
        List<BridgeEnd> ends = rulesEnds(game, player, position);
        if (!carriesTraffic(ends)) return Set.of();
        Set<Integer> sides = new LinkedHashSet<>();
        ends.forEach(end -> sides.add(end.side()));
        return sides;
    }

    @Nullable
    public static Set<Integer> passThroughExits(
            Game game, @Nullable Player player, String position, int enteredSide, boolean bothWays) {
        List<BridgeEnd> ends = rulesEnds(game, player, position);
        if (!carriesTraffic(ends) || ends.stream().noneMatch(end -> end.side() == enteredSide)) {
            return null;
        }
        Set<Integer> exits = new LinkedHashSet<>();
        for (BridgeEnd end : ends) {
            if (end.side() != enteredSide && (end.exit() || bothWays)) exits.add(end.side());
        }
        return exits;
    }

    public static Set<Integer> passThroughEntriesLeadingTo(
            Game game, @Nullable Player player, String position, int exitSide, boolean bothWays) {
        List<BridgeEnd> ends = rulesEnds(game, player, position);
        Set<Integer> entries = new LinkedHashSet<>();
        if (!carriesTraffic(ends)) return entries;
        boolean exitOpen = ends.stream().anyMatch(end -> end.side() == exitSide && (end.exit() || bothWays));
        if (!exitOpen) return entries;
        for (BridgeEnd end : ends) {
            if (end.side() != exitSide && (end.entry() || bothWays)) entries.add(end.side());
        }
        return entries;
    }

    private static List<BridgeEnd> rulesEnds(Game game, @Nullable Player player, String position) {
        if (game.getCustomHyperlaneData().isEmpty()) return List.of();
        return forSystem(
                game,
                player,
                position,
                hyperlane -> game.getCustomHyperlaneData().get(hyperlane.getPosition()));
    }

    private static boolean carriesTraffic(List<BridgeEnd> ends) {
        for (BridgeEnd entry : ends) {
            if (!entry.entry()) continue;
            for (BridgeEnd exit : ends) {
                if (exit != entry && exit.exit()) return true;
            }
        }
        return false;
    }
}
