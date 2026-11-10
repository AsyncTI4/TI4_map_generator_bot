package ti4.image.tile;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.FoWHelper;
import ti4.helpers.adjacency.GridRule;
import ti4.model.MapPositionMapper;
import ti4.service.fow.FOWPlusService;
import ti4.service.map.BridgeMatrix;
import ti4.service.map.CustomHyperlaneService;
import ti4.service.map.HyperlaneBridges;

final class BridgeEndsLookup {

    private BridgeEndsLookup() {}

    static BridgeMatrix drawnMatrix(TileScene scene, Tile system) {
        String position = system.getPosition();
        BridgeMatrix matrix = HyperlaneBridges.matrixOf(scene.game(), position);
        if (matrix == null) {
            return BridgeMatrix.NONE;
        }
        boolean systemSeen = !scene.hidesFogged(system);
        return scene.hyperlanes()
                .drawnBridge(position + ":" + systemSeen, () -> visiblePart(scene, position, matrix, systemSeen));
    }

    private static BridgeMatrix visiblePart(TileScene scene, String position, BridgeMatrix matrix, boolean seen) {
        BridgeMatrix known = lanesTheViewerKnows(scene, position, matrix, seen);
        if (!scene.isPrivateView() || !FOWPlusService.isActive(scene.game())) {
            return known;
        }
        List<String> neighbours = MapPositionMapper.getAdjacentTilePositions(position);
        return known.keepingPairs(
                (a, b) -> scene.seesPosition(neighbours.get(a)) && scene.seesPosition(neighbours.get(b)));
    }

    private static BridgeMatrix lanesTheViewerKnows(
            TileScene scene, String position, BridgeMatrix matrix, boolean seen) {
        HyperlaneView view = scene.hyperlanes();
        if (view.hidesUnseenLanes() && !view.revealedPositions().contains(position) && !viewerIsIn(scene, position)) {
            Set<String> visible = view.visibleLanes();
            return matrix.keepingPairs((a, b) -> visible.contains(GridRule.lane(position, a, b)));
        }
        if (seen) {
            return matrix;
        }
        List<String> neighbours = MapPositionMapper.getAdjacentTilePositions(position);
        return matrix.keepingPairs(
                (a, b) -> scene.seesPosition(neighbours.get(a)) && scene.seesPosition(neighbours.get(b)));
    }

    private static boolean viewerIsIn(TileScene scene, String position) {
        Player viewer = scene.fowPlayer();
        Tile system = scene.game().getTileByPosition(position);
        return viewer != null && system != null && FoWHelper.playerIsInSystem(scene.game(), system, viewer, false);
    }

    static Set<Integer> laneOnlySides(TileScene scene, Tile system) {
        return HyperlaneBridges.laneSides(scene.game(), system.getPosition());
    }

    static HyperlaneBridgeOverlay.Marks marks(TileScene scene, Tile system) {
        String position = system.getPosition();
        List<String> neighbours = MapPositionMapper.getAdjacentTilePositions(position);
        Set<Integer> laneSides = HyperlaneBridges.laneSides(scene.game(), position);
        Set<Integer> tunnels = new TreeSet<>();
        Set<Integer> laneEnds = new TreeSet<>();
        for (int side = 0; side < neighbours.size(); side++) {
            if (laneSides.contains(side) || !scene.seesPosition(neighbours.get(side))) continue;
            if (aLaneEndsHere(scene, side, neighbours.get(side))) {
                laneEnds.add(side);
            } else if (opensOntoSystem(scene.game(), position, side, neighbours.get(side))) {
                tunnels.add(side);
            }
        }
        return new HyperlaneBridgeOverlay.Marks(Set.copyOf(tunnels), Set.copyOf(laneEnds));
    }

    private static boolean aLaneEndsHere(TileScene scene, int side, String neighbourPosition) {
        Tile neighbour = scene.game().getTileByPosition(neighbourPosition);
        if (neighbour == null) {
            return false;
        }
        int facing = (side + 3) % 6;
        if (CustomHyperlaneService.isCustomHyperlaneTile(neighbour)) {
            String lanes = TileArtwork.drawnLanes(scene, neighbour);
            if (lanes == null || !CustomHyperlaneService.isValidStoredMatrix(lanes)) return false;
            int[][] grid = CustomHyperlaneService.parseMatrix(lanes);
            for (int other = 0; other < 6; other++) {
                if (other != facing && grid[other][facing] == 1) return true;
            }
            return false;
        }
        return drawnMatrix(scene, neighbour).exitsAt(facing);
    }

    private static boolean opensOntoSystem(Game game, String position, int side, String neighbourPosition) {
        Tile neighbour = game.getTileByPosition(neighbourPosition);
        int facing = (side + 3) % 6;
        return neighbour != null
                && neighbour.isValid()
                && !HyperlaneBridges.isHyperlane(neighbour)
                && !HyperlaneBridges.laneSides(game, neighbourPosition).contains(facing)
                && game.getAdjacentTileOverride(position, side) == null
                && game.getAdjacentTileOverride(neighbourPosition, facing) == null
                && !game.hasBorderAnomalyOn(position, side)
                && !game.hasBorderAnomalyOn(neighbourPosition, facing);
    }
}
