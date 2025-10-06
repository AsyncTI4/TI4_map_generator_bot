package ti4.service.draft;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.helpers.DisplayType;
import ti4.helpers.Storage;
import ti4.image.DrawingUtil;
import ti4.image.MapGenerator.HorizontalAlign;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.image.TileGenerator;
import ti4.image.TileStep;
import ti4.map.Game;
import ti4.map.Tile;
import ti4.message.logging.BotLogger;
import ti4.model.MapTemplateModel;
import ti4.service.image.FileUploadService;

@UtilityClass
public class MantisBuildImageGeneratorService {

    private static final int RING_MAX_COUNT = 8;
    private static final int RING_MIN_COUNT = 3;
    private static final int TILE_PADDING = 100;
    private static final int EXTRA_X = 0; // padding at left/right of map
    private static final int EXTRA_Y = 0; // padding at top/bottom of map
    private static final int SPACE_FOR_TILE_HEIGHT = 300; // space to calculate tile image height with
    private static final BasicStroke outlineStroke = new BasicStroke(12.0f);

    private static final String PENDING_TILE_POS = "br"; // bottom right corner

    /**
     * Draw a simple image of the current map, and if optionally a tile which has been drawn
     * for a player to place.
     * @param draftManager The draft manager for the draft; also contains the Game object.
     * @param uniqueKey A unique key to include in the file name.
     * @param currentPositions A list of tile positions to highlight on the map as available for the next tile.
     * @param pendingTileId A Tile ID to draw separately as pending placement by the current player.
     * @return A FileUpload containing the image, or null if the image could not be generated.
     */
    public FileUpload tryGenerateImage(
            DraftManager draftManager, String uniqueKey, List<String> currentPositions, String pendingTileId) {

        Game game = draftManager.getGame();
        String mapTemplateId = game.getMapTemplateID();
        MapTemplateModel mapTemplate = Mapper.getMapTemplate(mapTemplateId);
        if (mapTemplate == null) {
            return null;
        }

        currentPositions = currentPositions == null ? List.of() : currentPositions;
        BufferedImage mapImage = generateImage(draftManager, mapTemplate, currentPositions, pendingTileId);
        if (mapImage == null) {
            return null;
        }
        FileUpload fileUpload = FileUploadService.createFileUpload(mapImage, uniqueKey);

        return fileUpload;
    }

    private BufferedImage generateImage(
            DraftManager draftManager,
            MapTemplateModel mapTemplate,
            List<String> currentPositions,
            String pendingTileId) {
        int width = getMapWidth(draftManager.getGame());
        int height = getMapHeight(draftManager.getGame());
        BufferedImage mainImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics graphicsMain = mainImage.getGraphics();

        Game game = draftManager.getGame();

        Map<String, Tile> tileMap = getRelevantTiles(draftManager);

        TileGenerator tileGenerator = new TileGenerator(game, null, DisplayType.map);
        Map<String, Point> tileImagePoints = new HashMap<>();

        // Add all on-board game tiles to the image
        for (Tile tile : tileMap.values()) {
            try {
                Point tilePos = getTilePosition(game, tile.getPosition());
                BufferedImage tileImage = tileGenerator.draw(tile, TileStep.Tile);
                graphicsMain.drawImage(tileImage, tilePos.x, tilePos.y, null);
                tileImagePoints.put(tile.getPosition(), tilePos);
            } catch (Exception e) {
                BotLogger.error("Failed to draw tile IMG " + tile.getTileID() + " at " + tile.getPosition(), e);
                return null;
            }
        }
        for (String tilePos : currentPositions) {
            try {
                Tile tile = tileMap.get(tilePos);
                if (tile == null) {
                    BotLogger.warning("MantisBuildImageGeneratorService: Could not find tile at position " + tilePos
                            + " to highlight.");
                    continue;
                }
                Point tilePoint = tileImagePoints.get(tile.getPosition());
                drawAvailability(graphicsMain, tile.getPosition(), tilePoint, game);
            } catch (Exception e) {
                BotLogger.error("Failed to draw tile AVAILABILITY at " + tilePos, e);
                return null;
            }
        }
        if (pendingTileId != null) {
            try {
                Tile tile = tileMap.get(PENDING_TILE_POS);
                if (tile != null) {
                    BotLogger.warning(
                            "MantisBuildImageGeneratorService: There is already a tile at the pending tile position "
                                    + PENDING_TILE_POS + "; cannot draw pending tile.");
                    return mainImage;
                }
                tile = new Tile(pendingTileId, PENDING_TILE_POS);
                drawPendingTile(graphicsMain, game, tile);
            } catch (Exception e) {
                BotLogger.error("Failed to draw tile OPTION " + pendingTileId + " at " + PENDING_TILE_POS, e);
                return null;
            }
        }

        return mainImage;
    }

    private void drawAvailability(Graphics graphics, String positionName, Point positionPoint, Game game) {
        Point base = new Point(positionPoint.x + TILE_PADDING, positionPoint.y + TILE_PADDING + 20);

        HorizontalAlign hCenter = HorizontalAlign.Center;
        graphics.setColor(Color.white);
        graphics.setFont(Storage.getFont64());
        DrawingUtil.superDrawString(
                graphics, positionName, base.x + 172, base.y + 150, Color.white, hCenter, null, outlineStroke, Color.black);
    }

    private void drawPendingTile(Graphics graphics, Game game, Tile tile) {
        Point tilePoint = getTilePosition(game, PENDING_TILE_POS);
        Point base = new Point(tilePoint.x + TILE_PADDING, tilePoint.y + TILE_PADDING + 20 - 150);
        TileGenerator tileGenerator = new TileGenerator(game, null, DisplayType.map);
        BufferedImage tileImage = tileGenerator.draw(tile, TileStep.Tile);
        graphics.drawImage(tileImage, base.x - 100, base.y - 200, null);

        HorizontalAlign hCenter = HorizontalAlign.Center;
        graphics.setColor(new Color(184, 141, 42));
        graphics.setFont(Storage.getFont64());
        DrawingUtil.superDrawString(
                graphics,
                "CURRENT",
                base.x + 172,
                base.y - 80,
                Color.white,
                hCenter,
                null,
                outlineStroke,
                Color.black);
    }

    private Point getTilePosition(Game game, String position) {
        Point positionPoint = PositionMapper.getTilePosition(position);
        if (positionPoint == null) {
            return null;
        }

        int x = positionPoint.x;
        int y = positionPoint.y;

        positionPoint = PositionMapper.getScaledTilePosition(game, position, x, y);
        int tileX = positionPoint.x + EXTRA_X - TILE_PADDING;
        int tileY = positionPoint.y + EXTRA_Y - TILE_PADDING;

        return new Point(tileX, tileY);
    }

    private Map<String, Tile> getRelevantTiles(DraftManager draftManager) {
        Game game = draftManager.getGame();
        Map<String, Tile> tileMap = new HashMap<>(game.getTileMap());
        tileMap.remove("tl");
        tileMap.remove("tr");
        tileMap.remove("bl");
        tileMap.remove("br");

        tileMap.entrySet().removeIf(e -> e.getValue() == null || e.getValue().getTileID() == null);
        tileMap.entrySet()
                .removeIf(e -> PositionMapper.getTilePosition(e.getValue().getPosition()) == null);

        return tileMap;
    }

    /**
     * Gives the number of rings of the map
     *
     * @param game
     * @return between 3 and 8 (bounds based on constants)
     */
    private int getRingCount(Game game) {
        return Math.max(Math.min(game.getRingCount(), RING_MAX_COUNT), RING_MIN_COUNT);
    }

    /**
     * Gives the height of the map part of the image
     *
     * @param game
     * @return space for the (number of rings + 1) + 2 * EXTRA_Y
     */
    private int getMapHeight(Game game) {
        return (getRingCount(game) + 1) * SPACE_FOR_TILE_HEIGHT * 2 + EXTRA_Y * 2;
    }

    /**
     * Gives the width of the map part of the image
     * TO DO:
     * - fix the "ringCount == minRingCount" ternary (see comment)
     * - some variables are never used...
     *
     * @param game
     * @return space for ring count + 2 * EXTRA_X + potential EXTRA_X
     */
    private int getMapWidth(Game game) {
        float ringCount = getRingCount(game);
        ringCount += ringCount == RING_MIN_COUNT ? 1.5f : 1;
        int mapWidth = (int) (ringCount * 520 + EXTRA_X * 2);
        // mapWidth += hasExtraRow(game) ? EXTRA_X : 0;
        return mapWidth;
    }
}
