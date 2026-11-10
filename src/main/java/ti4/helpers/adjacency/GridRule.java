package ti4.helpers.adjacency;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.model.BorderAnomalyHolder;
import ti4.model.MapPositionMapper;
import ti4.service.fow.FOWPlusService;
import ti4.service.map.HyperlaneBridges;
import ti4.service.option.FOWOptionService.FOWOption;

public final class GridRule implements AdjacencyRule {
    private static final int START = -1;
    private static final int NATURAL_MAP_ONLY = -2;

    @Override
    public void from(AdjacencyContext context, String position, Set<String> adjacent) {
        adjacent.addAll(traverse(
                context.getGame(),
                context.getPlayer(),
                false,
                position,
                START,
                new HashSet<>(),
                null,
                lanesCountBothWays(context)));
    }

    @Override
    public void into(AdjacencyContext context, String position, Set<String> sources) {
        Game game = context.getGame();
        Player player = context.getPlayer();
        Set<String> explored = new HashSet<>();
        boolean bothWays = lanesCountBothWays(context);
        Set<Integer> laneSides = HyperlaneBridges.laneSides(game, position);
        for (int side = 0; side < 6; side++) {
            if (laneSides.contains(side)) continue;
            if (canBeEnteredFrom(game, position, side, bothWays)) {
                collectSources(game, player, position, side, explored, sources, bothWays);
            }
        }
        sources.addAll(bridgedSystemsTouching(game, player, position));
    }

    private static Set<String> bridgedSystemsTouching(Game game, Player player, String hyperlane) {
        Set<String> systems = new HashSet<>();
        Tile tile = game.getTileByPosition(hyperlane);
        List<String> neighbours = MapPositionMapper.getAdjacentTilePositions(hyperlane);
        if (tile == null
                || !HyperlaneBridges.isHyperlane(tile)
                || neighbours == null
                || neighbours.size() != 6
                || fogHidesHyperlanesAcrossLaneSides(game)) {
            return systems;
        }
        for (int side = 0; side < 6; side++) {
            String system = neighbours.get(side);
            int systemSide = (side + 3) % 6;
            if (!HyperlaneBridges.laneSides(game, system).contains(systemSide)) continue;
            List<String> systemNeighbours = MapPositionMapper.getAdjacentTilePositions(system);
            if (hyperlane.equals(step(game, player, false, system, systemSide, null, systemNeighbours))) {
                systems.add(system);
            }
        }
        return systems;
    }

    private static boolean lanesCountBothWays(AdjacencyContext context) {
        return !context.isMovement() && !context.getGame().usesOneWayAdjacency();
    }

    @Nullable
    private static List<Boolean> lanesFrom(Tile tile, int side, Game game, boolean bothWays) {
        List<Boolean> lanes = tile.getHyperlaneData(side, game);
        if (!bothWays || lanes == null || lanes.isEmpty()) return lanes;
        List<Boolean> combined = new ArrayList<>(lanes);
        for (int other = 0; other < 6; other++) {
            List<Boolean> otherLanes = tile.getHyperlaneData(other, game);
            if (otherLanes != null && otherLanes.get(side)) combined.set(other, true);
        }
        return combined;
    }

    public static Set<String> traverse(Game game, boolean naturalMapOnly, String position) {
        return traverse(game, null, naturalMapOnly, position, START, new HashSet<>());
    }

    public static Set<String> hyperlaneLanesFrom(Game game, Player player, String position) {
        Set<String> lanes = new HashSet<>();
        traverse(game, player, false, position, START, new HashSet<>(), lanes, false);
        return lanes;
    }

    private static Set<String> traverse(
            Game game,
            Player player,
            boolean naturalMapOnly,
            String position,
            int sourceDirection,
            Set<String> explored) {
        return traverse(game, player, naturalMapOnly, position, sourceDirection, explored, null, false);
    }

    private static Set<String> traverse(
            Game game,
            Player player,
            boolean naturalMapOnly,
            String position,
            int sourceDirection,
            Set<String> explored,
            @Nullable Set<String> lanes,
            boolean bothWays) {
        Set<String> tiles = new HashSet<>();
        if (!explored.add(position + sourceDirection)) {
            return tiles;
        }

        Tile currentTile = game.getTileByPosition(position);
        if (currentTile == null) {
            return tiles;
        }

        List<Boolean> hyperlaneData = lanesFrom(currentTile, sourceDirection, game, bothWays);
        if (hyperlaneData != null && hyperlaneData.isEmpty() && !naturalMapOnly) {
            return tiles;
        }

        tiles.add(position);

        if ((hyperlaneData == null || naturalMapOnly) && sourceDirection != START) {
            return tiles;
        }

        List<String> directlyAdjacentTiles = MapPositionMapper.getAdjacentTilePositions(position);
        if (directlyAdjacentTiles == null || directlyAdjacentTiles.size() != 6) {
            return tiles;
        }

        Set<Integer> laneOnlySides =
                sourceDirection == START && !naturalMapOnly ? HyperlaneBridges.laneSides(game, position) : Set.of();
        for (int direction = 0; direction < 6; direction++) {
            String next = step(game, player, naturalMapOnly, position, direction, hyperlaneData, directlyAdjacentTiles);
            if (next == null) continue;
            if (laneOnlySides.contains(direction)) {
                if (isHyperlaneAt(game, next) && !fogHidesHyperlanesAcrossLaneSides(game)) tiles.add(next);
                continue;
            }
            if (lanes != null && hyperlaneData != null && sourceDirection >= 0) {
                lanes.add(lane(position, sourceDirection, direction));
            }
            int enteredFrom = naturalMapOnly ? NATURAL_MAP_ONLY : (direction + 3) % 6;
            tiles.addAll(enter(game, player, naturalMapOnly, next, enteredFrom, explored, lanes, bothWays));
        }
        return tiles;
    }

    private static Set<String> enter(
            Game game,
            Player player,
            boolean naturalMapOnly,
            String next,
            int enteredFrom,
            Set<String> explored,
            @Nullable Set<String> lanes,
            boolean bothWays) {
        if (!naturalMapOnly) {
            Set<Integer> exits = HyperlaneBridges.exits(game, next, enteredFrom, bothWays);
            if (exits != null) {
                return passThrough(game, player, next, enteredFrom, exits, explored, lanes, bothWays);
            }
        }
        return traverse(game, player, naturalMapOnly, next, enteredFrom, explored, lanes, bothWays);
    }

    private static Set<String> passThrough(
            Game game,
            Player player,
            String system,
            int entered,
            Set<Integer> exits,
            Set<String> explored,
            @Nullable Set<String> lanes,
            boolean bothWays) {
        Set<String> tiles = new HashSet<>();
        List<String> neighbours = MapPositionMapper.getAdjacentTilePositions(system);
        if (!explored.add(system + "~" + entered) || neighbours == null || neighbours.size() != 6) {
            return tiles;
        }
        for (int exit : exits) {
            String next = bridgeExitTarget(game, system, exit, neighbours);
            int enteredFrom = (exit + 3) % 6;
            if (next == null || !FOWPlusService.shouldTraverseAdjacency(game, next, enteredFrom)) continue;
            if (!laneContinuesInto(game, next, enteredFrom)
                    && isBorderBlocked(game, player, system, exit, next, enteredFrom)) {
                continue;
            }
            if (lanes != null) lanes.add(lane(system, entered, exit));
            tiles.addAll(enter(game, player, false, next, enteredFrom, explored, lanes, bothWays));
        }
        return tiles;
    }

    @Nullable
    private static String bridgeExitTarget(Game game, String system, int exit, List<String> neighbours) {
        String override = game.getAdjacentTileOverride(system, exit);
        String next = override != null ? override : neighbours.get(exit);
        return next == null || "x".equals(next) ? null : next;
    }

    private static boolean laneContinuesInto(Game game, String next, int enteredFrom) {
        return isHyperlaneAt(game, next)
                || HyperlaneBridges.laneSides(game, next).contains(enteredFrom);
    }

    private static boolean fogHidesHyperlanesAcrossLaneSides(Game game) {
        return FOWPlusService.isActive(game) || game.getFowOption(FOWOption.HIDE_MAP);
    }

    private static boolean isHyperlaneAt(Game game, String position) {
        Tile tile = game.getTileByPosition(position);
        return tile != null && HyperlaneBridges.isHyperlane(tile);
    }

    public static String lane(String position, int side, int otherSide) {
        return position + ":" + Math.min(side, otherSide) + ":" + Math.max(side, otherSide);
    }

    private static String step(
            Game game,
            Player player,
            boolean naturalMapOnly,
            String position,
            int direction,
            List<Boolean> hyperlaneData,
            List<String> directlyAdjacentTiles) {
        int dirFrom = (direction + 3) % 6;
        String next = directlyAdjacentTiles.get(direction);
        String override = game.getAdjacentTileOverride(position, direction);
        boolean continuesALane = override == null
                && hyperlaneData != null
                && HyperlaneBridges.laneSides(game, next).contains(dirFrom);
        if (!naturalMapOnly && !continuesALane && isBorderBlocked(game, player, position, direction, next, dirFrom)) {
            return null;
        }

        if (override != null) {
            if (naturalMapOnly) return null;
            next = override;
        }

        if ("x".equals(next) || (hyperlaneData != null && !hyperlaneData.isEmpty() && !hyperlaneData.get(direction))) {
            return null;
        }

        if (!FOWPlusService.shouldTraverseAdjacency(game, next, dirFrom)) {
            return null;
        }
        return next;
    }

    public static boolean isBorderBlocked(
            Game game, @Nullable Player player, String position, int direction, String next, int dirFrom) {
        for (BorderAnomalyHolder border : game.getBorderAnomalies()) {
            if (border == null || border.getTile() == null || !border.isEnforced(game)) continue;
            if ((border.getTile().equals(position) && border.getDirection() == direction && border.blocksAdjacencyOut()
                            || border.getTile().equals(next)
                                    && border.getDirection() == dirFrom
                                    && border.blocksAdjacencyIn())
                    && !border.exempts(player)) {
                return true;
            }
        }
        return false;
    }

    private static boolean canBeEnteredFrom(Game game, String position, int side, boolean bothWays) {
        Tile tile = game.getTileByPosition(position);
        if (tile == null) return false;
        List<Boolean> hyperlaneData = lanesFrom(tile, side, game, bothWays);
        return hyperlaneData == null || !hyperlaneData.isEmpty();
    }

    private static void collectSources(
            Game game,
            Player player,
            String position,
            int enteredFrom,
            Set<String> explored,
            Set<String> sources,
            boolean bothWays) {
        if (!explored.add(position + enteredFrom)) {
            return;
        }
        int direction = (enteredFrom + 3) % 6;
        for (String previous : previousPositions(game, position, enteredFrom, direction)) {
            List<String> previousNeighbours = MapPositionMapper.getAdjacentTilePositions(previous);
            Tile previousTile = game.getTileByPosition(previous);
            if (previousTile == null || previousNeighbours == null || previousNeighbours.size() != 6) continue;

            List<Boolean> startData = previousTile.getHyperlaneData(START, game);
            if (startData == null && HyperlaneBridges.laneSides(game, previous).contains(direction)) {
                if (bridgeExitReaches(game, player, previous, direction, position, enteredFrom, previousNeighbours)) {
                    for (int entry : HyperlaneBridges.entriesLeadingTo(game, previous, direction, bothWays)) {
                        collectSources(game, player, previous, entry, explored, sources, bothWays);
                    }
                }
                continue;
            }
            if (startData == null
                    && position.equals(step(game, player, false, previous, direction, null, previousNeighbours))) {
                sources.add(previous);
            }

            for (int side = 0; side < 6; side++) {
                List<Boolean> hyperlaneData = lanesFrom(previousTile, side, game, bothWays);
                if (hyperlaneData == null || hyperlaneData.isEmpty()) continue;
                if (position.equals(
                        step(game, player, false, previous, direction, hyperlaneData, previousNeighbours))) {
                    collectSources(game, player, previous, side, explored, sources, bothWays);
                }
            }
        }
    }

    private static boolean bridgeExitReaches(
            Game game,
            Player player,
            String bridged,
            int exit,
            String position,
            int enteredFrom,
            List<String> bridgedNeighbours) {
        return position.equals(bridgeExitTarget(game, bridged, exit, bridgedNeighbours))
                && FOWPlusService.shouldTraverseAdjacency(game, position, enteredFrom)
                && (laneContinuesInto(game, position, enteredFrom)
                        || !isBorderBlocked(game, player, bridged, exit, position, enteredFrom));
    }

    // TODO: an override (incl. secondary x) leaves the natural neighbour's own edge pointing here, so it still links
    // one
    // way; Off mode's union then joins it both ways (e.g. Space Cannon across a cut edge). Treat it as cut both ways?
    private static Set<String> previousPositions(Game game, String position, int enteredFrom, int direction) {
        Set<String> previous = new HashSet<>();
        List<String> neighbours = MapPositionMapper.getAdjacentTilePositions(position);
        if (neighbours != null && neighbours.size() == 6) {
            previous.add(neighbours.get(enteredFrom));
        }
        for (var override : game.getAdjacentTileOverrides().entrySet()) {
            if (position.equals(override.getValue()) && override.getKey().getRight() == direction) {
                previous.add(override.getKey().getLeft());
            }
        }
        return previous;
    }
}
