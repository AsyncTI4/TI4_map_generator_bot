package ti4.image.tile;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.model.MapPositionMapper;
import ti4.service.map.HyperlaneBridgeEnds;
import ti4.service.map.HyperlaneBridgeEnds.BridgeEnd;
import ti4.service.map.HyperlaneStyleService;

final class BridgeEndsLookup {

    private BridgeEndsLookup() {}

    static List<BridgeEnd> forSystem(TileScene scene, Tile system) {
        boolean systemSideKnown = !scene.hidesFogged(system);
        String key = system.getPosition() + ":" + system.getTileID() + ":" + systemSideKnown;
        return scene.hyperlanes()
                .bridgeEnds(
                        key,
                        () -> HyperlaneBridgeEnds.forSystemTile(
                                scene.game(),
                                system,
                                hyperlane -> scene.seesPosition(hyperlane.getPosition())
                                        ? TileArtwork.drawnLanes(scene, hyperlane)
                                        : null,
                                systemSideKnown));
    }

    static Set<Integer> sidesWhereArrivingLanesPassOn(TileScene scene, Tile system) {
        if (!HyperlaneStyleService.showsBridges(scene.game())) {
            return Set.of();
        }
        return HyperlaneBridgeEnds.sidesWhereArrivingLanesPassOn(forSystem(scene, system));
    }

    static Set<Integer> crossings(TileScene scene, Tile system, List<BridgeEnd> ends) {
        List<String> neighbours = MapPositionMapper.getAdjacentTilePositions(system.getPosition());
        Set<Integer> crossings = new TreeSet<>();
        for (int side = 0; side < neighbours.size(); side++) {
            if (isLaneEnd(ends, side)) continue;
            if (opensOntoSystem(scene, system.getPosition(), side, neighbours.get(side))) crossings.add(side);
        }
        return crossings;
    }

    private static boolean isLaneEnd(List<BridgeEnd> ends, int side) {
        return ends.stream().anyMatch(end -> end.side() == side);
    }

    private static boolean opensOntoSystem(TileScene scene, String position, int side, String neighbourPosition) {
        Game game = scene.game();
        Tile neighbour = game.getTileByPosition(neighbourPosition);
        int facing = HyperlaneBridgeEnds.facingSide(side);
        return neighbour != null
                && neighbour.isValid()
                && !HyperlaneBridgeEnds.isHyperlane(neighbour)
                && scene.seesPosition(neighbourPosition)
                && game.getAdjacentTileOverride(position, side) == null
                && game.getAdjacentTileOverride(neighbourPosition, facing) == null
                && !game.hasBorderAnomalyOn(position, side)
                && !game.hasBorderAnomalyOn(neighbourPosition, facing);
    }
}
