package ti4.image.tile;

import javax.annotation.Nullable;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.service.fow.FogViewer;

public final class UnexploredBridges {

    private static final String UNEXPLORED_TILE_ID = "0b";

    private final TileScene scene;

    public UnexploredBridges(FogViewer viewer) {
        scene = new TileScene(viewer.game(), null, viewer, new RenderWarnings());
    }

    public boolean shows(Tile tile) {
        return TileArtwork.showsUnexploredBridge(scene, tile);
    }

    @Nullable
    public Tile fogTile(Player player, String position) {
        Tile remembered = player.buildFogTile(position, player);
        if (remembered != null) {
            return remembered;
        }
        Tile unexplored = new Tile(UNEXPLORED_TILE_ID, position, player, true, "");
        return shows(unexplored) ? unexplored : null;
    }
}
