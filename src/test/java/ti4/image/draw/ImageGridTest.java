package ti4.image.draw;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Font;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ImageGridTest {

    @Test
    void layoutWrapsIndexesIntoRows() {
        ImageGrid.Layout layout = new ImageGrid.Layout(10, 20, 3, 100, 50);

        assertEquals(new Point(10, 20), layout.origin(0));
        assertEquals(new Point(210, 20), layout.origin(2));
        assertEquals(new Point(10, 70), layout.origin(3));
        assertEquals(new Point(110, 120), layout.origin(7));
    }

    @Test
    void squareTilesFillWholeColumnsAndRows() {
        BufferedImage image = new BufferedImage(40, 30, BufferedImage.TYPE_INT_ARGB);
        List<ImageGrid.Item> items = IntStream.range(0, 7)
                .mapToObj(i -> new ImageGrid.Item(image, "tile " + i))
                .toList();

        BufferedImage grid = ImageGrid.squareTiles(items, 5, new Font(Font.SANS_SERIF, Font.PLAIN, 12));

        assertEquals(5 * 40, grid.getWidth());
        assertEquals(2 * 40, grid.getHeight());
    }
}
