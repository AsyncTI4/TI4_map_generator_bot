package ti4.image.draw;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.logging.BotLogger;

public final class PaImage {

    private PaImage() {}

    public static Rectangle draw(Graphics2D g, int x, int y, String resourceName) {
        try {
            String resourcePath = ImageResources.playerArea(resourceName);
            BufferedImage resourceBufferedImage = ImageHelper.read(resourcePath);
            g.drawImage(resourceBufferedImage, x, y, null);
            if (resourceBufferedImage != null)
                return new Rectangle(x, y, resourceBufferedImage.getWidth(), resourceBufferedImage.getHeight());
        } catch (Exception e) {
            logFailure(resourceName, e);
        }
        return new Rectangle(new Point(x, y));
    }

    public static void drawGeneral(Graphics2D g2, int x, int y, String resourceName) {
        try {
            String resourcePath = ImageResources.general(resourceName);
            BufferedImage resourceBufferedImage = ImageHelper.read(resourcePath);
            g2.drawImage(resourceBufferedImage, x, y, null);
        } catch (Exception e) {
            logFailure(resourceName, e);
        }
    }

    public static void drawScaled(Graphics2D g, int x, int y, String resourceName, int width, int height) {
        try {
            String resourcePath = ImageResources.playerArea(resourceName);
            BufferedImage resourceBufferedImage = ImageHelper.readScaled(resourcePath, width, height);
            g.drawImage(resourceBufferedImage, x, y, null);
        } catch (Exception e) {
            logFailure(resourceName, e);
        }
    }

    public static void drawOpaque(Graphics2D g2, int x, int y, String resourceName, float opacity) {
        try {
            String resourcePath = ImageResources.playerArea(resourceName);
            BufferedImage resourceBufferedImage = ImageHelper.read(resourcePath);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
            g2.drawImage(resourceBufferedImage, x, y, null);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        } catch (Exception e) {
            logFailure(resourceName, e);
        }
    }

    private static void logFailure(String resourceName, Exception e) {
        BotLogger.error("Could not display play area: " + resourceName, e);
    }
}
