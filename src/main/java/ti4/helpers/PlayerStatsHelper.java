package ti4.helpers;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import ti4.image.PositionMapper;
import ti4.map.Game;
import ti4.map.Player;

public class PlayerStatsHelper {
    public static List<String> findThreeNearbyStatTiles(
            Game game, Player player, Set<String> taken, boolean isFoWPrivate, Player fowPlayer) {
        boolean fow = isFoWPrivate;
        boolean randomizeLocation = false;
        if (fow && player != fowPlayer) {
            if (FoWHelper.canSeeStatsOfPlayer(game, player, fowPlayer)) {
                if (!FoWHelper.hasHomeSystemInView(player, fowPlayer)) {
                    // if we can see a players stats, but we cannot see their home system - move
                    // their stats somewhere random
                    randomizeLocation = true;
                }
            }
        }

        String anchor = player.getPlayerStatsAnchorPosition();
        if (anchor == null) anchor = player.getHomeSystemPosition();
        if (anchor == null) return null;
        if (randomizeLocation) anchor = "000"; // just stick them on 000

        Set<String> validPositions = PositionMapper.getTilePositions().stream()
                .filter(pos -> tileRing(pos) <= (game.getRingCount() + 1))
                .filter(pos -> game.getTileByPosition(pos) == null)
                .filter(pos -> taken == null || !taken.contains(pos))
                .collect(Collectors.toSet());

        Point anchorRaw = PositionMapper.getTilePosition(anchor);
        if (anchorRaw == null) return null;
        Point anchorPt = PositionMapper.getScaledTilePosition(game, anchor, anchorRaw.x, anchorRaw.y);

        // BEGIN ALGORITHM
        // 1. Make a Priority Queue sorting on distance
        // 2. Take tiles from the PQ until we have a contiguous selection of 3 adj tiles
        // 3. Use those tiles :)

        // 1.
        boolean rand = randomizeLocation;
        PriorityQueue<String> pq = new PriorityQueue<>(Comparator.comparingDouble(pos -> {
            Point positionPoint = PositionMapper.getTilePosition(pos);
            if (positionPoint == null) return 100000000f;
            int ring = tileRing(pos);
            Point realPosition = PositionMapper.getScaledTilePosition(game, pos, positionPoint.x, positionPoint.y);
            double distance = realPosition.distance(anchorPt);
            distance = rand ? ThreadLocalRandom.current().nextInt(0, 200) : distance + ring * 75;
            return distance;
        }));
        pq.addAll(validPositions);

        // 2. Take tiles from the PQ until we have 3 adj
        // - - N*logN * 6
        List<String> closestTiles = new ArrayList<>();
        Map<String, Integer> numAdj = new HashMap<>();
        String next;
        while ((next = pq.poll()) != null) {
            if (closestTiles.contains(next)) continue;
            closestTiles.add(next);
            numAdj.put(next, 0);

            for (String pos : PositionMapper.getAdjacentTilePositions(next)) {
                if (numAdj.containsKey(pos)) {
                    numAdj.put(pos, numAdj.get(pos) + 1);
                    numAdj.put(next, numAdj.get(next) + 1);
                }
            }
            for (String pos : closestTiles) {
                if (numAdj.get(pos) == 2) {
                    List<String> adjOut = PositionMapper.getAdjacentTilePositions(pos);
                    List<String> output = new ArrayList<>();
                    output.add(pos);
                    output.addAll(CollectionUtils.intersection(adjOut, closestTiles));
                    return output;
                }
            }
        }
        return null;
    }

    private static int tileRing(String pos) {
        if (pos.replaceAll("\\d", "").isEmpty()) return Integer.parseInt(pos) / 100;
        return 100;
    }
}
