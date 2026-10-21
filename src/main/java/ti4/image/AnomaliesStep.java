package ti4.image;

import ti4.ResourceHelper;
import ti4.game.Game;
import ti4.game.Tile;

final class AnomaliesStep implements TileStepPainter {

    private static final int CHEVRON_X = 33;
    private static final int CHEVRON_Y = 101;

    @Override
    public void paint(TileScene scene, TileLayer layer) {
        Game game = scene.game();
        Tile tile = layer.tile();
        if (HighlightGuards.isFogOrHyperlane(game, tile) || HighlightGuards.isTileImageMissing(tile)) {
            return;
        }
        if (!tile.isAnomaly(game, null)) {
            HighlightGuards.fogOut(layer);
            return;
        }
        layer.drawOnHex(
                ImageHelper.read(ResourceHelper.getTileFile("tile_anomaly_chevron.png")),
                layer.spiralOffsetX() + CHEVRON_X,
                layer.spiralOffsetY() + CHEVRON_Y);
    }
}
