package ti4.image.draw;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.List;
import ti4.image.io.ImageHelper;

public final class ImageGrid {

    private ImageGrid() {}

    public record Item(BufferedImage image, String label) {}

    public record Layout(int left, int top, int columns, int cellWidth, int cellHeight) {

        public Point origin(int index) {
            return new Point(left + (index % columns) * cellWidth, top + (index / columns) * cellHeight);
        }
    }

    public record LabelStyle(
            Font titleFont, Font detailFont, BasicStroke stroke, int cellWidth, int labelTop, int lineHeight) {}

    public static BufferedImage squareTiles(List<Item> items, int columns, Font labelFont) {
        List<BufferedImage> tiles =
                items.stream().map(item -> labelledSquare(item, labelFont)).toList();
        // TODO: the column width is the first tile's width; it was meant to be the widest tile.
        int size = tiles.getFirst().getWidth();
        int rows = (tiles.size() + columns - 1) / columns;
        BufferedImage grid = new BufferedImage(size * columns, size * rows, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = grid.createGraphics();
        int index = 0;
        for (BufferedImage tile : tiles) {
            int x = index % columns;
            int y = index / columns;
            int xoff = (size - tile.getWidth()) / 2;
            g.drawImage(tile, x * size + xoff, y * size, null);
            index++;
        }
        return grid;
    }

    public static void drawLabels(
            Graphics2D g, LabelStyle style, int cellX, int cellY, List<String> titles, List<String> details) {
        int centerX = cellX + style.cellWidth() / 2;
        int y = cellY + style.labelTop();
        g.setFont(style.titleFont());
        for (String title : titles) {
            drawLabel(g, style, title, centerX, y);
            y += style.lineHeight();
        }
        g.setFont(style.detailFont());
        for (String detail : details) {
            drawLabel(g, style, detail, centerX, y);
            y += style.lineHeight();
        }
    }

    private static void drawLabel(Graphics2D g, LabelStyle style, String text, int centerX, int y) {
        OutlinedText.draw(
                g,
                text,
                centerX,
                y,
                Color.WHITE,
                HorizontalAlign.Center,
                VerticalAlign.Top,
                style.stroke(),
                Color.BLACK);
    }

    private static BufferedImage labelledSquare(Item item, Font labelFont) {
        BufferedImage square = ImageHelper.square(item.image());
        Graphics2D g = square.createGraphics();
        g.setFont(labelFont);
        OutlinedText.draw(
                g, item.label(), square.getWidth() / 2, 0, null, HorizontalAlign.Center, VerticalAlign.Top, null, null);
        return square;
    }
}
