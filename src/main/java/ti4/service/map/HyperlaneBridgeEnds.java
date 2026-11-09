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
import ti4.game.Tile;
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

    public static List<BridgeEnd> forSystem(Game game, String position, LaneSource lanes) {
        if (!HyperlaneStyleService.showsBridges(game)) {
            return List.of();
        }
        Tile system = game.getTileByPosition(position);
        if (system == null || isHyperlane(system)) {
            return List.of();
        }
        return forSystemTile(game, system, lanes, true);
    }

    public static List<BridgeEnd> forSystemTile(Game game, Tile system, LaneSource lanes, boolean systemSideKnown) {
        List<String> neighbours = MapPositionMapper.getAdjacentTilePositions(system.getPosition());
        if (isHyperlane(system) || neighbours.size() != 6) {
            return List.of();
        }
        Map<Integer, BridgeEnd> ends = new HashMap<>();
        for (int side = 0; side < 6; side++) {
            BridgeEnd end = endTowards(game, system.getPosition(), side, neighbours.get(side), lanes, systemSideKnown);
            if (end != null) ends.put(side, end);
        }
        List<BridgeEnd> bridge = bridgeable(ends);
        return carriesTraffic(bridge) ? bridge : List.of();
    }

    @Nullable
    private static BridgeEnd endTowards(
            Game game, String position, int side, String neighbourPosition, LaneSource lanes, boolean systemSideKnown) {
        Tile neighbour = game.getTileByPosition(neighbourPosition);
        if (neighbour == null || !CustomHyperlaneService.isCustomHyperlaneTile(neighbour)) {
            return null;
        }
        String matrix = lanes.lanesOf(neighbour);
        if (matrix == null || !CustomHyperlaneService.isValidStoredMatrix(matrix)) {
            return null;
        }
        BridgeEnd end = endOn(side, CustomHyperlaneService.parseMatrix(matrix)).orElse(null);
        if (end == null) {
            return null;
        }
        int edge = facingSide(side);
        boolean entry = end.entry() && notRedirected(game, neighbourPosition, edge);
        boolean exit = end.exit() && (!systemSideKnown || notRedirected(game, position, side));
        return entry || exit ? new BridgeEnd(side, entry, exit) : null;
    }

    private static boolean notRedirected(Game game, String from, int direction) {
        return game.getAdjacentTileOverride(from, direction) == null;
    }

    public static boolean isHyperlane(Tile tile) {
        return CustomHyperlaneService.isCustomHyperlaneTile(tile) || Mapper.getHyperlaneData(tile.getTileID()) != null;
    }

    public static Set<Integer> passThroughSides(Game game, String position) {
        List<BridgeEnd> ends = rulesEnds(game, position);
        Set<Integer> sides = new LinkedHashSet<>();
        for (BridgeEnd end : ends) {
            if (arrivingLanePassesOn(ends, end, false) || isExitOnlyBridgeExit(ends, end)) sides.add(end.side());
        }
        return sides;
    }

    @Nullable
    public static Set<Integer> passThroughExits(Game game, String position, int enteredSide, boolean bothWays) {
        List<BridgeEnd> ends = rulesEnds(game, position);
        BridgeEnd entered = endOnSide(ends, enteredSide);
        if (entered == null || !arrivingLanePassesOn(ends, entered, bothWays)) {
            return null;
        }
        Set<Integer> exits = new LinkedHashSet<>();
        for (BridgeEnd end : ends) {
            if (end.side() != enteredSide && (end.exit() || bothWays)) exits.add(end.side());
        }
        return exits;
    }

    public static Set<Integer> passThroughEntriesLeadingTo(Game game, String position, int exitSide, boolean bothWays) {
        List<BridgeEnd> ends = rulesEnds(game, position);
        BridgeEnd exit = endOnSide(ends, exitSide);
        Set<Integer> entries = new LinkedHashSet<>();
        if (exit == null || !(exit.exit() || bothWays)) {
            return entries;
        }
        for (BridgeEnd end : ends) {
            if (end.side() != exitSide && arrivingLanePassesOn(ends, end, bothWays)) entries.add(end.side());
        }
        return entries;
    }

    public static Set<Integer> sidesWhereArrivingLanesPassOn(List<BridgeEnd> ends) {
        Set<Integer> sides = new LinkedHashSet<>();
        for (BridgeEnd end : ends) {
            if (arrivingLanePassesOn(ends, end, false)) sides.add(end.side());
        }
        return sides;
    }

    private static boolean arrivingLanePassesOn(List<BridgeEnd> ends, BridgeEnd arrival, boolean bothWays) {
        if (!(arrival.entry() || bothWays)) return false;
        return ends.stream().anyMatch(other -> other != arrival && (other.exit() || bothWays));
    }

    private static boolean isExitOnlyBridgeExit(List<BridgeEnd> ends, BridgeEnd end) {
        return end.exit() && !end.entry() && ends.stream().anyMatch(other -> other != end && other.entry());
    }

    @Nullable
    private static BridgeEnd endOnSide(List<BridgeEnd> ends, int side) {
        return ends.stream().filter(end -> end.side() == side).findFirst().orElse(null);
    }

    private static List<BridgeEnd> rulesEnds(Game game, String position) {
        if (game.getCustomHyperlaneData().isEmpty()) return List.of();
        return forSystem(
                game, position, hyperlane -> game.getCustomHyperlaneData().get(hyperlane.getPosition()));
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
