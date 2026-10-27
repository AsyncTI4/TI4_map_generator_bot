package ti4.image.tile;

import static ti4.image.tile.TileGeometry.TILE_HEIGHT;
import static ti4.image.tile.TileGeometry.TILE_WIDTH;

import java.awt.image.BufferedImage;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.FoWHelper;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;

final class ExileStep implements TileStepPainter {

    @Override
    public void paint(TileScene scene, TileLayer layer) {
        Game game = scene.game();
        Tile tile = layer.tile();
        if (HighlightGuards.isFogOrHyperlane(game, tile)) {
            return;
        }
        boolean inRangeOfExile = isInRangeOfAnyExile(game, tile);
        if (HighlightGuards.isTileImageMissing(tile)) {
            return;
        }
        if (!inRangeOfExile) {
            HighlightGuards.fogOut(layer);
            return;
        }
        String breachFile = ImageResources.token("token_breachActive.png");
        BufferedImage breach = ImageHelper.readScaled(breachFile, 2.0f);
        if (breach != null) {
            layer.drawOnHex(
                    breach,
                    layer.spiralOffsetX() + (TILE_WIDTH - breach.getWidth()) / 2,
                    layer.spiralOffsetY() + (TILE_HEIGHT - breach.getHeight()) / 2);
        }
    }

    private static boolean isInRangeOfAnyExile(Game game, Tile tile) {
        for (Player p : game.getRealPlayers()) {
            if (FoWHelper.isTileInExileRange(game, tile, p) || FoWHelper.isTileInUpgradedExileRange(game, tile, p)) {
                return true;
            }
        }
        return false;
    }
}
