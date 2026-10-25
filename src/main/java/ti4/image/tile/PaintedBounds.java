package ti4.image.tile;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import javax.annotation.Nullable;

final class PaintedBounds {

    private PaintedBounds() {}

    @Nullable
    static Rectangle of(BufferedImage argb) {
        int[] pixels = ((DataBufferInt) argb.getRaster().getDataBuffer()).getData();
        int width = argb.getWidth();
        int height = argb.getHeight();
        int top = 0;
        while (top < height && isRowClear(pixels, top * width, width)) top++;
        if (top == height) {
            return null;
        }
        int bottom = height - 1;
        while (isRowClear(pixels, bottom * width, width)) bottom--;
        int left = width;
        int right = -1;
        for (int y = top; y <= bottom; y++) {
            int row = y * width;
            for (int x = 0; x < left; x++) {
                if (pixels[row + x] >>> 24 != 0) {
                    left = x;
                    break;
                }
            }
            for (int x = width - 1; x > right; x--) {
                if (pixels[row + x] >>> 24 != 0) {
                    right = x;
                    break;
                }
            }
        }
        return new Rectangle(left, top, right - left + 1, bottom - top + 1);
    }

    private static boolean isRowClear(int[] pixels, int start, int width) {
        for (int i = start; i < start + width; i++) {
            if (pixels[i] >>> 24 != 0) {
                return false;
            }
        }
        return true;
    }
}
