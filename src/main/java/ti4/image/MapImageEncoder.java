package ti4.image;

import java.awt.image.BufferedImage;

final class MapImageEncoder {

    private static final int WEBP_MAX_DIMENSION = 16_383;

    private MapImageEncoder() {}

    static EncodedImage encode(BufferedImage canvas) {
        if (canvas.getWidth() > WEBP_MAX_DIMENSION || canvas.getHeight() > WEBP_MAX_DIMENSION) {
            return new EncodedImage(ImageHelper.writeJpg(canvas), "jpg");
        }
        return new EncodedImage(ImageHelper.writeWebp(canvas), "webp");
    }
}
