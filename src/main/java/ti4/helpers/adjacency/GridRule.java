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
import ti4.service.map.CustomHyperlaneService;
import ti4.service.map.HyperlaneBridgeEnds;

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
        Set<Integer> passThroughSides = HyperlaneBridgeEnds.passThroughSides(game, player, position);
        for (int side = 0; side < 6; side++) {
            if (passThroughSides.contains(side)) continue;
            if (canBeEnteredFrom(game, position, side, bothWays)) {
                collectSources(game, player, position, side, explored, sources, bothWays);
            }
        }
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

        Set<Integer> closedSides = sourceDirection == START && !naturalMapOnly
                ? HyperlaneBridgeEnds.passThroughSides(game, player, position)
                : Set.of();
        boolean onCustomLane =
                hyperlaneData != null && !naturalMapOnly && CustomHyperlaneService.isCustomHyperlaneTile(currentTile);
        for (int direction = 0; direction < 6; direction++) {
            if (closedSides.contains(direction)) continue;
            String next = step(game, player, naturalMapOnly, position, direction, hyperlaneData, directlyAdjacentTiles);
            if (next == null) continue;
            if (lanes != null && hyperlaneData != null && sourceDirection >= 0) {
                lanes.add(lane(position, sourceDirection, direction));
            }
            int enteredFrom = naturalMapOnly ? NATURAL_MAP_ONLY : (direction + 3) % 6;
            Set<Integer> passThroughExits = onCustomLane
                    ? HyperlaneBridgeEnds.passThroughExits(game, player, next, enteredFrom, bothWays)
                    : null;
            if (passThroughExits != null) {
                tiles.addAll(passThrough(game, player, next, passThroughExits, explored, lanes, bothWays));
                continue;
            }
            tiles.addAll(traverse(game, player, naturalMapOnly, next, enteredFrom, explored, lanes, bothWays));
        }
        return tiles;
    }

    private static Set<String> passThrough(
            Game game,
            Player player,
            String system,
            Set<Integer> exits,
            Set<String> explored,
            @Nullable Set<String> lanes,
            boolean bothWays) {
        Set<String> tiles = new HashSet<>();
        List<String> neighbours = MapPositionMapper.getAdjacentTilePositions(system);
        for (int exit : exits) {
            tiles.addAll(
                    traverse(game, player, false, neighbours.get(exit), (exit + 3) % 6, explored, lanes, bothWays));
        }
        return tiles;
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
        if (!naturalMapOnly && isBorderBlocked(game, player, position, direction, next, dirFrom)) {
            return null;
        }

        String override = game.getAdjacentTileOverride(position, direction);
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
            if (startData == null
                    && HyperlaneBridgeEnds.passThroughSides(game, player, previous)
                            .contains(direction)) {
                for (int entry :
                        HyperlaneBridgeEnds.passThroughEntriesLeadingTo(game, player, previous, direction, bothWays)) {
                    collectSources(game, player, previous, entry, explored, sources, bothWays);
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
