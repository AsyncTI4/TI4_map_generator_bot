package ti4.image.tile;

import static ti4.image.tile.TileGeometry.TILE_PADDING;

import java.awt.image.BufferedImage;
import java.util.Set;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.helpers.FoWHelper;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.model.Mapper;
import ti4.model.WormholeModel.Wormhole;

final class WormholesStep implements TileStepPainter {

    @Override
    public void paint(TileScene scene, TileLayer layer) {
        Game game = scene.game();
        Tile tile = layer.tile();
        if (HighlightGuards.isHiddenOrHyperlane(scene, tile) || HighlightGuards.isTileImageMissing(tile)) {
            return;
        }
        Set<Wormhole> wormholes = FoWHelper.getTileWHs(game, tile.getPosition());
        if (wormholes.isEmpty()) {
            HighlightGuards.fogOut(layer);
            return;
        }
        int count = wormholes.size();
        int x = TILE_PADDING + layer.spiralOffsetX() + (count == 1 ? 76 : 30);
        int y = TILE_PADDING + layer.spiralOffsetY() + (count == 1 ? 52 : 26);
        for (Wormhole wormhole : wormholes) {
            String wormholeFile = wormholeTokenFile(wormhole);
            if (wormholeFile != null) {
                BufferedImage bufferedImage = ImageHelper.readScaled(wormholeFile, 3);
                layer.g().drawImage(bufferedImage, x, y, null);
            }
            x += (count <= 1 ? 0 : 91 / (count - 1));
            y += (count <= 1 ? 0 : 52 / (count - 1));
        }
    }

    private static String wormholeTokenFile(Wormhole wormhole) {
        String file = ImageResources.token("token_wh" + wormhole + ".png");
        if (file != null) return file;
        String dataImage = Mapper.getWormholeTokenImagePath(wormhole);
        return dataImage == null ? null : ImageResources.token(dataImage);
    }
}
