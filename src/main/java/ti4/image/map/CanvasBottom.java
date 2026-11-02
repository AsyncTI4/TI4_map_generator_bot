package ti4.image.map;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

final class CanvasBottom {

    private static final int BLACK = 0;

    private CanvasBottom() {}

    static int ofDrawnContent(BufferedImage canvas, int fromRow) {
        int width = canvas.getWidth();
        int[] pixels = ((DataBufferInt) canvas.getRaster().getDataBuffer()).getData();
        for (int row = canvas.getHeight() - 1; row >= fromRow; row--) {
            int rowStart = row * width;
            for (int x = 0; x < width; x++) {
                if ((pixels[rowStart + x] & 0xFFFFFF) != BLACK) {
                    return row + 1;
                }
            }
        }
        return fromRow;
    }
}
