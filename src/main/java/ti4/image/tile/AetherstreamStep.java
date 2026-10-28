package ti4.image.tile;

import static ti4.image.tile.TileGeometry.TILE_HEIGHT;
import static ti4.image.tile.TileGeometry.TILE_WIDTH;

import java.awt.image.BufferedImage;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.helpers.FoWHelper;
import ti4.helpers.RenderRandom;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;

final class AetherstreamStep implements TileStepPainter {

    private static final int SHINY_ODDS = 4096;

    @Override
    public void paint(TileScene scene, TileLayer layer) {
        Game game = scene.game();
        Tile tile = layer.tile();
        if (HighlightGuards.isFogOrHyperlane(game, tile) || HighlightGuards.isTileImageMissing(tile)) {
            return;
        }
        if (!FoWHelper.isTileAdjacentToAnAnomaly(game, tile.getPosition(), null)) {
            HighlightGuards.fogOut(layer);
            return;
        }
        String batFile = ImageResources.general(
                "zobat" + (RenderRandom.oneIn("tile.shinyZobat", SHINY_ODDS) ? "_shiny" : "") + ".png");
        BufferedImage bat = ImageHelper.read(batFile);
        if (bat != null) {
            layer.drawOnHex(
                    bat,
                    layer.spiralOffsetX() + (TILE_WIDTH - bat.getWidth()) / 2,
                    layer.spiralOffsetY() + (TILE_HEIGHT - bat.getHeight()) / 2);
        }
    }
}
