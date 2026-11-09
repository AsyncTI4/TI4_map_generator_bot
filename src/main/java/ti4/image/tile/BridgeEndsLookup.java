package ti4.image.tile;

import java.util.List;
import ti4.game.Tile;
import ti4.service.map.HyperlaneBridgeEnds;
import ti4.service.map.HyperlaneBridgeEnds.BridgeEnd;

final class BridgeEndsLookup {

    private BridgeEndsLookup() {}

    static List<BridgeEnd> forSystem(TileScene scene, Tile system) {
        return HyperlaneBridgeEnds.forSystemTile(
                scene.game(),
                scene.fowPlayer(),
                system,
                hyperlane ->
                        scene.seesPosition(hyperlane.getPosition()) ? TileArtwork.drawnLanes(scene, hyperlane) : null);
    }
}
