package ti4.image.map;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.helpers.DisplayType;
import ti4.image.draw.HorizontalAlign;
import ti4.image.draw.OutlinedText;
import ti4.image.draw.Strokes;
import ti4.image.draw.VerticalAlign;
import ti4.image.io.Fonts;
import ti4.image.io.ImageUploads;
import ti4.image.tile.TileGeometry;
import ti4.image.tile.TileLayers;
import ti4.image.tile.TileStep;
import ti4.logging.BotLogger;

@UtilityClass
public class MapOverviewGenerator {

    private static final int PADDING = 200;
    private static final int CORNER_MARGIN = 20;
    private static final int CORNER_COLUMN = TileGeometry.TILE_WIDTH + 2 * CORNER_MARGIN;
    private static final BasicStroke SECTOR_LABEL_OUTLINE = Strokes.STROKE_6;
    private static final List<TileStep> STEPS =
            List.of(TileStep.Tile, TileStep.Extras, TileStep.Units, TileStep.TileNumber);

    public static FileUpload createFileUpload(Game game, boolean sectorNames) {
        return ImageUploads.createFileUpload(render(game, sectorNames), game.getName() + "_overview", "webp");
    }

    public static BufferedImage render(Game game, boolean sectorNames) {
        Layout layout = Layout.of(game);
        BufferedImage image = new BufferedImage(layout.width(), layout.height(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = OverviewStyle.smoothGraphics(image);
        try {
            drawTiles(game, graphics, layout);
            if (sectorNames) {
                drawSectors(game, graphics, layout);
            }
            OverviewStyle.drawHeader(graphics, game.getName() + " - GM overview");
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static void drawTiles(Game game, Graphics2D graphics, Layout layout) {
        TileLayers tileLayers = TileLayers.of(game, null, DisplayType.map);
        Map<String, Tile> tiles = new TreeMap<>(game.getTileMap());
        int drawnSize = layout.scaled(OverviewStyle.TILE_IMAGE_SIZE);
        for (TileStep step : STEPS) {
            tiles.forEach((position, tile) -> {
                Rectangle hex = layout.hexBounds(game, position);
                if (hex == null) return;
                try {
                    BufferedImage tileImage = tileLayers.draw(tile, step);
                    int x = layout.toCanvasX(hex.x - OverviewStyle.TILE_PADDING);
                    int y = layout.toCanvasY(hex.y - OverviewStyle.TILE_PADDING);
                    graphics.drawImage(tileImage, x, y, drawnSize, drawnSize, null);
                } catch (Exception e) {
                    BotLogger.error("GM overview could not draw tile " + tile.getTileID() + " at " + position, e);
                }
            });
        }
    }

    private static void drawSectors(Game game, Graphics2D graphics, Layout layout) {
        List<MapSegment> segments = MapSegment.all(game);
        for (int index = 0; index < segments.size(); index++) {
            MapSegment segment = segments.get(index);
            Color color = OverviewStyle.color(index);
            Rectangle area = null;
            for (String position : segment.positions()) {
                Rectangle hex = layout.hexBounds(game, position);
                if (hex == null) continue;
                graphics.setColor(OverviewStyle.translucent(color, 70));
                graphics.fillPolygon(layout.hexPolygon(hex));
                area = area == null ? new Rectangle(hex) : area.union(hex);
            }
            if (area != null) {
                graphics.setFont(Fonts.ti(64));
                OutlinedText.draw(
                        graphics,
                        segment.name(),
                        layout.toCanvasX((int) area.getCenterX()),
                        layout.toCanvasY((int) area.getCenterY()),
                        color,
                        HorizontalAlign.Center,
                        VerticalAlign.Center,
                        SECTOR_LABEL_OUTLINE,
                        Color.BLACK);
            }
        }
    }

    private record Layout(
            Rectangle content, double scale, int fractureYbump, Rectangle leftColumn, Rectangle rightColumn) {

        static Layout of(Game game) {
            int fractureYbump = BoardLayout.fractureYbump(game);
            Set<String> gridPositions = new HashSet<>(game.getTileMap().keySet());
            gridPositions.removeIf(position -> BoardLayout.CORNER_POSITIONS.contains(position.toLowerCase()));
            Rectangle content = MapFrame.bounds(game, gridPositions, fractureYbump, 0, 0);
            if (content == null) {
                content = new Rectangle(0, 0, TileGeometry.TILE_WIDTH, TileGeometry.TILE_HEIGHT);
            }
            content.grow(PADDING, PADDING);
            Rectangle leftColumn = null;
            Rectangle rightColumn = null;
            if (hasCorner(game, "tl") || hasCorner(game, "bl")) {
                content.x -= CORNER_COLUMN;
                content.width += CORNER_COLUMN;
                leftColumn = new Rectangle(content.x, content.y, CORNER_COLUMN, content.height);
            }
            if (hasCorner(game, "tr") || hasCorner(game, "br")) {
                content.width += CORNER_COLUMN;
                rightColumn = new Rectangle(
                        content.x + content.width - CORNER_COLUMN, content.y, CORNER_COLUMN, content.height);
            }
            double scale = Math.min(
                    1.0,
                    Math.min(
                            (double) MapFrame.MAX_WIDTH / content.width,
                            (double) MapFrame.MAX_HEIGHT / content.height));
            return new Layout(content, scale, fractureYbump, leftColumn, rightColumn);
        }

        private static boolean hasCorner(Game game, String corner) {
            return game.getTileByPosition(corner) != null;
        }

        int width() {
            return Math.max(1, scaled(content.width));
        }

        int height() {
            return Math.max(1, scaled(content.height));
        }

        int scaled(int length) {
            return (int) Math.ceil(length * scale);
        }

        int toCanvasX(int x) {
            return (int) Math.round((x - content.x) * scale);
        }

        int toCanvasY(int y) {
            return (int) Math.round((y - content.y) * scale);
        }

        Rectangle hexBounds(Game game, String position) {
            String corner = position.toLowerCase();
            if (!BoardLayout.CORNER_POSITIONS.contains(corner)) {
                return MapFrame.hexBounds(game, position, fractureYbump, 0, 0);
            }
            Rectangle column = corner.endsWith("l") ? leftColumn : rightColumn;
            if (column == null) return null;
            int top = corner.startsWith("t")
                    ? column.y + PADDING
                    : column.y + column.height - PADDING - TileGeometry.TILE_HEIGHT;
            return new Rectangle(column.x + CORNER_MARGIN, top, TileGeometry.TILE_WIDTH, TileGeometry.TILE_HEIGHT);
        }

        Polygon hexPolygon(Rectangle hex) {
            int left = toCanvasX(hex.x);
            int top = toCanvasY(hex.y);
            return MapFrame.hexPolygon(
                    new Rectangle(left, top, toCanvasX(hex.x + hex.width) - left, toCanvasY(hex.y + hex.height) - top));
        }
    }
}
