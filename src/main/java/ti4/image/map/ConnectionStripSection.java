package ti4.image.map;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Objects;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.image.draw.HorizontalAlign;
import ti4.image.draw.OutlinedText;
import ti4.image.draw.Strokes;
import ti4.image.draw.VerticalAlign;
import ti4.image.io.Fonts;
import ti4.image.tile.TileGeometry;
import ti4.image.tile.TileLayers;
import ti4.image.tile.TileStep;
import ti4.logging.BotLogger;

final class ConnectionStripSection {

    private static final int HEADER_HEIGHT = 60;
    private static final int LABEL_HEIGHT = 40;
    private static final int CELL_WIDTH = 320;
    private static final int ROW_HEIGHT = 230;
    private static final int MAX_ROWS = 3;
    private static final int SIDE_MARGIN = 40;
    private static final int BOTTOM_CLEARANCE = 240;
    private static final double TILE_SCALE = 0.5;

    private ConnectionStripSection() {}

    static int height(List<SectorConnections.Connection> connections, int stripWidth) {
        if (connections.isEmpty()) {
            return 0;
        }
        int rows = Math.min(MAX_ROWS, Math.ceilDiv(connections.size(), connectionsPerRow(stripWidth)));
        return HEADER_HEIGHT + rows * ROW_HEIGHT + BOTTOM_CLEARANCE;
    }

    static List<Tile> connectedTiles(RenderContext ctx) {
        return ctx.board().connections().stream()
                .map(connection -> ctx.fog().tiles().get(connection.position()))
                .filter(Objects::nonNull)
                .toList();
    }

    static void draw(RenderContext ctx, Graphics2D g2) {
        List<SectorConnections.Connection> connections = ctx.board().connections();
        if (connections.isEmpty()) {
            return;
        }
        int width = ctx.canvas().width();
        int stripTop = ctx.board().connectionStripTop();
        int perRow = connectionsPerRow(width);
        int capacity = perRow * MAX_ROWS;
        boolean overflow = connections.size() > capacity;
        List<SectorConnections.Connection> drawn = overflow ? connections.subList(0, capacity - 1) : connections;
        int cells = drawn.size() + (overflow ? 1 : 0);

        g2.setColor(Color.BLACK);
        g2.fillRect(0, stripTop, width, height(connections, width));
        g2.setFont(Fonts.ti(32));
        OutlinedText.draw(
                g2,
                "Connections to other maps",
                width / 2,
                stripTop + HEADER_HEIGHT / 2,
                Color.WHITE,
                HorizontalAlign.Center,
                VerticalAlign.Center,
                Strokes.STROKE_4,
                Color.BLACK);
        TileLayers tileLayers =
                TileLayers.forPreparedViewer(ctx.game(), ctx.fog().fogViewer(), ctx.displayType());
        for (int index = 0; index < cells; index++) {
            Point cell = cellOrigin(index, perRow, cells, width, stripTop);
            if (index < drawn.size()) {
                drawConnection(ctx, tileLayers, g2, drawn.get(index), cell);
            } else {
                String more = "+" + (connections.size() - drawn.size()) + " more";
                drawLabel(g2, more, cell, ROW_HEIGHT / 2, Fonts.ti(28));
            }
        }
        g2.setFont(Fonts.ti(32));
    }

    private static int connectionsPerRow(int stripWidth) {
        return Math.max(1, (stripWidth - 2 * SIDE_MARGIN) / CELL_WIDTH);
    }

    private static Point cellOrigin(int index, int perRow, int cells, int width, int stripTop) {
        int row = index / perRow;
        int column = index % perRow;
        int cellsInRow = Math.min(perRow, cells - row * perRow);
        int rowLeft = (width - cellsInRow * CELL_WIDTH) / 2;
        return new Point(rowLeft + column * CELL_WIDTH, stripTop + HEADER_HEIGHT + row * ROW_HEIGHT);
    }

    private static void drawConnection(
            RenderContext ctx,
            TileLayers tileLayers,
            Graphics2D g2,
            SectorConnections.Connection connection,
            Point cell) {
        Tile tile = ctx.fog().tiles().get(connection.position());
        if (tile == null) {
            return;
        }
        drawLabel(g2, connection.sectorName(), cell, LABEL_HEIGHT / 2, Fonts.ti(28));
        int size = (int) (600 * TILE_SCALE);
        int padding = (int) (TileGeometry.TILE_PADDING * TILE_SCALE);
        int hexHeight = (int) (TileGeometry.TILE_HEIGHT * TILE_SCALE);
        BufferedImage tileImage;
        try {
            tileImage = tileImage(tileLayers, tile);
        } catch (Exception e) {
            Game game = ctx.game();
            BotLogger.error("Connection strip error in `" + game.getName() + "`, tile: " + tile.getTileID(), e);
            return;
        }
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(tileImage, cell.x + (CELL_WIDTH - size) / 2, cell.y + LABEL_HEIGHT - padding, size, size, null);
        drawLabel(g2, connection.position(), cell, LABEL_HEIGHT + hexHeight + 18, Fonts.ti(24));
    }

    private static void drawLabel(Graphics2D g2, String text, Point cell, int centreY, Font font) {
        g2.setFont(font);
        OutlinedText.draw(
                g2,
                text,
                cell.x + CELL_WIDTH / 2,
                cell.y + centreY,
                Color.WHITE,
                HorizontalAlign.Center,
                VerticalAlign.Center,
                Strokes.STROKE_3,
                Color.BLACK);
    }

    private static BufferedImage tileImage(TileLayers tileLayers, Tile tile) {
        BufferedImage image =
                new BufferedImage(TileGeometry.LAYER_SIZE, TileGeometry.LAYER_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D tileGraphics = image.createGraphics();
        for (TileStep step : List.of(TileStep.Tile, TileStep.Units)) {
            tileLayers.paint(tile, step, tileGraphics, 0, 0);
        }
        tileGraphics.dispose();
        return image;
    }
}
