package ti4.image.tile;

import static ti4.image.tile.TileGeometry.TILE_PADDING;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.model.HyperlaneStyle;
import ti4.model.ShipPositionModel.ShipPosition;
import ti4.service.map.CustomHyperlaneService;
import ti4.service.map.HyperlaneBridgeEnds.BridgeEnd;
import ti4.service.map.HyperlaneStyleService;

final class TileArtwork {

    private static final Set<String> OFFSET_ANOMALY_BORDER_LAYOUTS = Set.of("TYPE09", "TYPE12", "TYPE15");

    private TileArtwork() {}

    static void draw(TileScene scene, TileLayer layer) {
        Game game = scene.game();
        Tile tile = layer.tile();
        layer.g().drawImage(baseImage(scene, tile), TILE_PADDING, TILE_PADDING, null);
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

    private static BufferedImage baseImage(TileScene scene, Tile tile) {
        if (CustomHyperlaneService.isCustomHyperlaneTile(tile)) {
            return hyperlaneImage(scene, tile);
        }
        String artPath = systemArtPath(scene.game(), tile);
        BufferedImage art = ImageHelper.read(artPath);
        if (art == null || !HyperlaneStyleService.showsBridges(scene.game())) {
            return art;
        }
        List<BridgeEnd> ends = BridgeEndsLookup.forSystem(scene, tile);
        if (ends.isEmpty()) {
            return art;
        }
        HyperlaneStyle style = scene.hyperlanes().style();
        if (isUnexplored(scene, tile)) {
            return HyperlaneBridgeOverlay.onUnexplored(
                    tile.getPosition(), () -> unexploredBackground(tile), ends, style);
        }
        return HyperlaneBridgeOverlay.onSystem(
                artPath,
                art,
                PlanetOcclusion.of(tile.getTileModel()),
                ends,
                BridgeEndsLookup.crossings(scene, tile, ends),
                style);
    }

    private static String systemArtPath(Game game, Tile tile) {
        if (game.isLiberationC4Mode() && "51".equals(tile.getTileID())) {
            return ImageResources.tile("51r_Creuss.png");
        }
        if (game.isLiberationC4Mode() && "17".equals(tile.getTileID())) {
            return ImageResources.tile("17r_DeltaWH.png");
        }
        return ImageResources.tilePath(tile);
    }

    private static boolean isUnexplored(TileScene scene, Tile tile) {
        return UnexploredBridges.UNEXPLORED_TILE_ID.equals(tile.getTileID()) && scene.hidesFogged(tile);
    }

    static boolean showsUnexploredBridge(TileScene scene, Tile tile) {
        return isUnexplored(scene, tile)
                && HyperlaneStyleService.showsBridges(scene.game())
                && !BridgeEndsLookup.forSystem(scene, tile).isEmpty();
    }

    private static BufferedImage unexploredBackground(Tile tile) {
        String noLanes = CustomHyperlaneService.formatMatrix(new int[6][6]);
        return HyperlaneTileGenerator.generateHyperlaneTile(
                tile, tile.getPosition(), noLanes, false, HyperlaneStyle.DASHED);
    }

    static BufferedImage hyperlaneImage(TileScene scene, Tile tile) {
        HyperlaneView view = scene.hyperlanes();
        String matrix = scene.game().getCustomHyperlaneData().get(tile.getPosition());
        String drawn = drawnLanes(scene, tile);
        String backgroundSeed = matrix;
        boolean lanesHidden = false;
        if (view.hidesUnseenLanes()) {
            backgroundSeed = tile.getPosition();
            lanesHidden = scene.hidesFogged(tile)
                    || (matrix != null
                            && !CustomHyperlaneService.formatMatrix(CustomHyperlaneService.parseMatrix(matrix))
                                    .equals(drawn));
        }
        HyperlaneStyle style = CustomHyperlaneService.hasOneWayLanes(drawn) ? view.style() : HyperlaneStyle.DASHED;
        return HyperlaneTileGenerator.generateHyperlaneTile(tile, backgroundSeed, drawn, lanesHidden, style);
    }

    @Nullable
    static String drawnLanes(TileScene scene, Tile tile) {
        HyperlaneView view = scene.hyperlanes();
        String matrix = scene.game().getCustomHyperlaneData().get(tile.getPosition());
        if (!view.hidesUnseenLanes()) {
            return matrix;
        }
        if (scene.hidesFogged(tile)) {
            return CustomHyperlaneService.formatMatrix(new int[6][6]);
        }
        return matrix == null ? null : lanesThePlayerSees(view, tile, matrix);
    }

    private static String lanesThePlayerSees(HyperlaneView view, Tile tile, String matrix) {
        if (view.revealedPositions().contains(tile.getPosition())) {
            return CustomHyperlaneService.formatMatrix(CustomHyperlaneService.parseMatrix(matrix));
        }
        return CustomHyperlaneService.onlyLanes(matrix, tile.getPosition(), view.visibleLanes());
    }
}
