package ti4.image.tile;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.annotation.Nullable;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.image.io.Fonts;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.logging.BotLogger;
import ti4.model.BorderAnomalyHolder;
import ti4.model.BorderAnomalyModel;
import ti4.model.PositionMapper;

final class BorderDecorationsStep implements TileStepPainter {

    private static final int HEX_CENTER_X = 173;
    private static final int HEX_CENTER_Y = 150;

    @Override
    public void paint(TileScene scene, TileLayer layer) {
        Game game = scene.game();
        Tile tile = layer.tile();
        if (!scene.hidesFogged(tile)) {
            drawAdjacencyArrows(game, layer);
        }
        if (!game.getBorderAnomalies().isEmpty()) {
            drawBorderAnomalies(scene, layer);
        }
    }

    private static void drawAdjacencyArrows(Game game, TileLayer layer) {
        List<String> adj = game.getAdjacentTileOverrides(layer.tile().getPosition());
        int direction = 0;
        for (String secondaryTile : adj) {
            if (secondaryTile != null) {
                drawDecoration(direction, secondaryTile, layer.g(), adjacencyArrowImagePath());
            }
            direction++;
        }
    }

    private static void drawBorderAnomalies(TileScene scene, TileLayer layer) {
        Game game = scene.game();
        Tile tile = layer.tile();
        List<String> orderedAdjacentPositions = PositionMapper.getAdjacentTilePositions(tile.getPosition());
        game.getBorderAnomalies().forEach(borderAnomalyHolder -> {
            BorderAnomalyModel anomalyModel = borderAnomalyHolder.getModel();
            if (anomalyModel != null
                    && borderAnomalyHolder.getTile().equals(tile.getPosition())
                    && isVisible(scene, tile, borderAnomalyHolder, orderedAdjacentPositions)) {
                drawDecoration(
                        borderAnomalyHolder.getDirection(),
                        null,
                        layer.g(),
                        ImageResources.border(anomalyModel.getImagePath()));
            }
        });
    }

    private static boolean isVisible(
            TileScene scene, Tile tile, BorderAnomalyHolder anomaly, List<String> orderedAdjacentPositions) {
        return !scene.hidesFogged(tile)
                || scene.viewer().positionsInReach().contains(orderedAdjacentPositions.get(anomaly.getDirection()));
    }

    private static String adjacencyArrowImagePath() {
        return ImageResources.border("adjacency_arrow.png");
    }

    private static void drawDecoration(
            int direction, @Nullable String secondaryTile, Graphics2D tileGraphics, String decorationImagePath) {
        if (decorationImagePath == null) {
            return;
        }
        BufferedImage decoration;
        try {
            BufferedImage cached = ImageHelper.read(decorationImagePath);
            decoration = new BufferedImage(
                    cached.getColorModel(), cached.copyData(null), cached.isAlphaPremultiplied(), null);
        } catch (Exception e) {
            BotLogger.error("Could not find border decoration image! Decoration was " + decorationImagePath, e);
            return;
        }

        int imageCenterX = decoration.getWidth() / 2;
        int imageCenterY = decoration.getHeight() / 2;

        AffineTransform originalTileTransform = tileGraphics.getTransform();
        tileGraphics.translate(100, 100);
        int centerX = HEX_CENTER_X;

        if (secondaryTile != null) {
            labelArrow(decoration, secondaryTile, direction, imageCenterX, imageCenterY);
        }

        tileGraphics.rotate(Math.toRadians(direction * 60), centerX, HEX_CENTER_Y);
        if (secondaryTile != null) centerX -= 20;
        tileGraphics.drawImage(decoration, null, centerX - imageCenterX, -imageCenterY);
        tileGraphics.setTransform(originalTileTransform);
    }

    private static void labelArrow(
            BufferedImage arrowImage, String secondaryTile, int direction, int imageCenterX, int imageCenterY) {
        int textOffsetX = 12;
        int textOffsetY = 40;
        Graphics2D arrow = arrowImage.createGraphics();
        AffineTransform arrowTextTransform = arrow.getTransform();

        arrow.setFont(secondaryTile.length() > 3 ? Fonts.ti(14) : Fonts.ti(16));
        arrow.setColor(Color.BLACK);

        boolean pointsSouth = direction >= 2 && direction <= 4;
        if (pointsSouth) {
            arrow.rotate(3.141_592_653_589_793, imageCenterX, imageCenterY);
            textOffsetY = 25;
        }
        arrow.drawString(secondaryTile, textOffsetX, textOffsetY);
        arrow.setTransform(arrowTextTransform);
    }
}
