package ti4.image.tile;

import static ti4.image.tile.TileGeometry.TILE_PADDING;

import java.awt.image.BufferedImage;
import java.util.Set;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.model.ShipPositionModel.ShipPosition;
import ti4.service.map.CustomHyperlaneService;

final class TileArtwork {

    private static final Set<String> OFFSET_ANOMALY_BORDER_LAYOUTS = Set.of("TYPE09", "TYPE12", "TYPE15");

    private TileArtwork() {}

    static void draw(Game game, TileLayer layer) {
        Tile tile = layer.tile();
        layer.g().drawImage(baseImage(game, tile), TILE_PADDING, TILE_PADDING, null);
        ShipPosition shipPositions = layer.shipPositions();
        if (tile.isAnomaly(game, null) && shipPositions != null) {
            BufferedImage anomalyImage = ImageHelper.read(ImageResources.tile("tile_anomaly.png"));
            if (OFFSET_ANOMALY_BORDER_LAYOUTS.contains(shipPositions.toString().toUpperCase())) {
                layer.drawOnHex(anomalyImage, TileLayer.SPIRAL_OFFSET_X, TileLayer.SPIRAL_OFFSET_Y);
            } else {
                layer.drawOnHex(anomalyImage, 0, 0);
            }
        }
    }

    private static BufferedImage baseImage(Game game, Tile tile) {
        if (CustomHyperlaneService.isCustomHyperlaneTile(tile)) {
            return HyperlaneTileGenerator.generateHyperlaneTile(tile, game);
        }
        if (game.isLiberationC4Mode() && "51".equals(tile.getTileID())) {
            return ImageHelper.read(ImageResources.tile("51r_Creuss.png"));
        }
        if (game.isLiberationC4Mode() && "17".equals(tile.getTileID())) {
            return ImageHelper.read(ImageResources.tile("17r_DeltaWH.png"));
        }
        return ImageHelper.read(ImageResources.tilePath(tile));
    }
}
