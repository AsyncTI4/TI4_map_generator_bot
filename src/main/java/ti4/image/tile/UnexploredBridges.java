package ti4.image.tile;

import javax.annotation.Nullable;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.service.map.HyperlaneStyleService;

public final class UnexploredBridges {

    public static final String UNEXPLORED_TILE_ID = "0b";

    private final TileScene scene;

    public UnexploredBridges(TileLayers layers) {
        scene = layers.scene();
    }

    public boolean shows(Tile tile) {
        return TileArtwork.showsUnexploredBridge(scene, tile);
    }

    @Nullable
    public Tile fogTile(Player player, String position) {
        Tile remembered = player.buildFogTile(position, player);
        if (remembered != null || !HyperlaneStyleService.showsBridges(scene.game())) {
            return remembered;
        }
        Tile unexplored = new Tile(UNEXPLORED_TILE_ID, position, player, true, "");
        return shows(unexplored) ? unexplored : null;
    }
}
