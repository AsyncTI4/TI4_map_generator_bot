package ti4.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ti4.image.Mapper;
import ti4.image.TileHelper;
import ti4.map.Game;
import ti4.model.MapTemplateModel;
import ti4.model.TileModel;

/**
 * This will calculate the distance between two positions. It respects hyperlanes, but NOT wormholes.
 * Its purpose is to determine good places to put wormholes (when doing a Nucleus draft)
 * This use a brute force breadth-first search, so the caching is important.
 */
public class DistanceTool {
    private final Game game;
    private final Map<String, Map<String, Integer>> distanceCache = new HashMap<>();
    private final Set<String> legalPositions = new HashSet<>();
    private final int MAX_DISTANCE = 10;

    public DistanceTool(Game game) {
        this.game = game;

        reset();
    }

    public void reset() {
        distanceCache.clear();
        MapTemplateModel mapTemplate = Mapper.getMapTemplate(game.getMapTemplateID());
        if (mapTemplate == null) {
            throw new IllegalArgumentException("Map template not found for ID: " + game.getMapTemplateID());
        }
        legalPositions.clear();
        legalPositions.addAll(mapTemplate.getTemplateTiles().stream()
                .filter(t -> {
                    if (t.getStaticTileId() == null) return true;
                    String tileId = t.getStaticTileId();
                    tileId = AliasHandler.resolveTile(tileId);
                    TileModel tile = TileHelper.getTileById(tileId);
                    return !tile.isHyperlane();
                })
                .map(t -> t.getPos())
                .collect(HashSet::new, HashSet::add, HashSet::addAll));
    }

    /**
     * Get the distance between two positions on the Nucleus map template.
     * This method uses a breadth-first search to find the shortest path
     * between the two positions, respecting hyperlanes but not wormholes or other tile features.
     * Points of failure: If the map is giant (distance > MAX_DISTANCE) or if the map isn't contiguous
     * @param pos1
     * @param pos2
     * @return the move distance between the two positions, or -1 if they are not reachable.
     */
    public Integer getNattyDistance(String pos1, String pos2) {
        if (pos1.equals(pos2) || pos1 == null || pos2 == null) {
            return 0;
        }
        // Use the same ordering for path checks to double our cache hits
        // God help us if Dane adds one-way tiles/pathing to this game
        if (pos1.compareTo(pos2) > 0) {
            String tmp = pos1;
            pos1 = pos2;
            pos2 = tmp;
        }

        Integer cachedDistance = getFromCache(pos1, pos2);
        if (cachedDistance != null) {
            return cachedDistance;
        }

        Set<String> checkedPositions = new HashSet<>();
        List<String> positionsAtNextDistance = new LinkedList<>(getAdjacentPositions(pos1));
        for (int i = 1; i < MAX_DISTANCE; ++i) {
            // Build cache for this distance even if we'll find the destination here
            for (String position : positionsAtNextDistance) {
                if (getFromCache(pos1, position) == null) {
                    addToCache(pos1, pos2, i);
                }
                checkedPositions.add(position);
            }
            // Now look for destination at this distance
            for (String position : positionsAtNextDistance) {
                if (pos2.equals(position)) {
                    return i;
                }
            }

            // If not found, prepare for next distance
            Set<String> futureNextPositions = new HashSet<>();
            for (String position : positionsAtNextDistance) {
                List<String> adjacentPositions = getAdjacentPositions(position);
                futureNextPositions.addAll(adjacentPositions);
            }
            futureNextPositions.removeAll(checkedPositions); // don't revisit previously checked positions
            positionsAtNextDistance.clear();
            positionsAtNextDistance.addAll(futureNextPositions);
        }

        return null; // not reachable
    }

    private List<String> getAdjacentPositions(String position) {
        // Traverse adjacencies uses position mapping and hyperlane data to get adjacent positions
        // It also uses border anomalies. What are border anomalies? If you're using them, I don't care what happens to
        // you.
        Set<String> adjacentPositions = FoWHelper.traverseAdjacencies(game, false, position);

        // Only return positions that in the template, and that are not hyperlanes
        List<String> validAdjacentPositions = new ArrayList<>();
        for (String adjacentPosition : adjacentPositions) {
            if (legalPositions.contains(adjacentPosition)) {
                validAdjacentPositions.add(adjacentPosition);
            }
        }
        return validAdjacentPositions;
    }

    public Integer getFromCache(String pos1, String pos2) {
        if (pos1 == null || pos2 == null) {
            return null;
        }
        // Use the same ordering for path checks to double our cache hits
        // God help us if Dane adds one-way tiles/pathing to this game
        if (pos1.compareTo(pos2) > 0) {
            String tmp = pos1;
            pos1 = pos2;
            pos2 = tmp;
        }

        if (distanceCache.containsKey(pos1) && distanceCache.get(pos1).containsKey(pos2)) {
            return distanceCache.get(pos1).get(pos2);
        } else {
            return null;
        }
    }

    public void addToCache(String pos1, String pos2, int distance) {
        if (pos1 == null || pos2 == null) {
            return;
        }
        // Use the same ordering for path checks to double our cache hits
        // God help us if Dane adds one-way tiles/pathing to this game
        if (pos1.compareTo(pos2) > 0) {
            String tmp = pos1;
            pos1 = pos2;
            pos2 = tmp;
        }

        if (!distanceCache.containsKey(pos1)) {
            distanceCache.put(pos1, new HashMap<>());
        }
        distanceCache.get(pos1).put(pos2, distance);
    }
}
