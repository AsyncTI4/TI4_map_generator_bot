package ti4.image.draft;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.helpers.DisplayType;
import ti4.image.io.Fonts;
import ti4.image.tile.TileLayers;
import ti4.image.tile.TileStep;
import ti4.logging.BotLogger;

public final class MantisImage {

    private static final DraftBoard BOARD = new DraftBoard(50, 150);
    private static final BasicStroke OUTLINE_STROKE = new BasicStroke(12.0f);
    private static final String PENDING_TILE_POSITION = "br";
    private static final Color PENDING_LABEL_COLOR = new Color(184, 141, 42);

    private MantisImage() {}

    @Nullable
    public static BufferedImage render(Game game, List<String> highlightedPositions, @Nullable String pendingTileId) {
        BufferedImage image = new BufferedImage(BOARD.width(game), BOARD.height(game), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        Map<String, Tile> tiles = DraftBoard.tilesOnBoard(game);
        TileLayers tileLayers = TileLayers.of(game, null, DisplayType.map);
        Map<String, Point> tilePoints = new HashMap<>();

        for (Tile tile : tiles.values()) {
            try {
                Point tilePosition = BOARD.tilePosition(game, tile.getPosition());
                BufferedImage tileImage = tileLayers.draw(tile, TileStep.TileUnannotated);
                graphics.drawImage(tileImage, tilePosition.x, tilePosition.y, null);
                tilePoints.put(tile.getPosition(), tilePosition);
            } catch (Exception e) {
                BotLogger.error("Failed to draw tile IMG " + tile.getTileID() + " at " + tile.getPosition(), e);
                return null;
            }
        }
        for (String position : highlightedPositions) {
            try {
                Tile tile = tiles.get(position);
                if (tile == null) {
                    BotLogger.warning("MantisImage: Could not find tile at position " + position
                            + " to render as a placement option.");
                    continue;
                }
                drawPositionName(graphics, tile.getPosition(), tilePoints.get(tile.getPosition()));
            } catch (Exception e) {
                BotLogger.error("Failed to draw tile AVAILABILITY at " + position, e);
                return null;
            }
        }
        if (pendingTileId != null) {
            try {
                if (tiles.get(PENDING_TILE_POSITION) != null) {
                    BotLogger.warning("MantisImage: There is already a tile at the pending tile position "
                            + PENDING_TILE_POSITION + "; cannot draw pending tile.");
                    return image;
                }
                drawPendingTile(graphics, game, new Tile(pendingTileId, PENDING_TILE_POSITION));
            } catch (Exception e) {
                BotLogger.error("Failed to draw tile OPTION " + pendingTileId + " at " + PENDING_TILE_POSITION, e);
                return null;
            }
        }
        return image;
    }

    private static void drawPositionName(Graphics2D graphics, String positionName, Point tilePosition) {
        Point base = DraftBoard.contentOrigin(tilePosition);
        graphics.setColor(Color.white);
        graphics.setFont(Fonts.ti(64));
        DraftText.centered(graphics, positionName, Color.white, base.x + 172, base.y + 150, OUTLINE_STROKE);
    }

    private static void drawPendingTile(Graphics2D graphics, Game game, Tile tile) {
        Point tilePosition = BOARD.tilePosition(game, PENDING_TILE_POSITION);
        Point base = DraftBoard.contentOrigin(tilePosition);
        base.translate(0, -150);
        TileLayers tileLayers = TileLayers.of(game, null, DisplayType.map);
        BufferedImage tileImage = tileLayers.draw(tile, TileStep.Tile);
        graphics.drawImage(tileImage, base.x - 100, base.y - 200, null);

        graphics.setColor(PENDING_LABEL_COLOR);
        graphics.setFont(Fonts.ti(64));
        DraftText.centered(graphics, "CURRENT", Color.white, base.x + 172, base.y - 80, OUTLINE_STROKE);
    }
}
