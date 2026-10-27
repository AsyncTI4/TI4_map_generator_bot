package ti4.image.draw;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import ti4.helpers.RenderRandom;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;

public final class StarfieldPage {

    private static final int WIDTH = 2400;
    private static final int HEIGHT = 3600;

    private final BufferedImage canvas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
    private final Graphics2D graphics = canvas.createGraphics();
    private final int left;
    private final int top;

    public StarfieldPage(int contentWidth, int contentHeight) {
        left = randomOffset(WIDTH - contentWidth);
        top = randomOffset(HEIGHT - contentHeight);
        graphics.drawImage(ImageHelper.read(ImageResources.extra("starfield.png")), 0, 0, null);
    }

    public static int width() {
        return WIDTH;
    }

    public static int height() {
        return HEIGHT;
    }

    public Graphics2D graphics() {
        return graphics;
    }

    public int left() {
        return left;
    }

    public int top() {
        return top;
    }

    public BufferedImage crop(int right, int bottom) {
        int cropWidth = Math.max(1, Math.min(right, WIDTH) - left);
        int cropHeight = Math.max(1, Math.min(bottom, HEIGHT) - top);
        return canvas.getSubimage(left, top, cropWidth, cropHeight);
    }

    private static int randomOffset(int room) {
        return room > 0 ? RenderRandom.cosmeticInt(room) : 0;
    }
}
