package ti4.image;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import ti4.ResourceHelper;
import ti4.logging.BotLogger;

final class GeneralImage {

    private GeneralImage() {}

    static void draw(Graphics2D g2, int x, int y, String resourceName) {
        try {
            String resourcePath = ResourceHelper.getInstance().getGeneralFile(resourceName);
            BufferedImage resourceBufferedImage = ImageHelper.read(resourcePath);
            g2.drawImage(resourceBufferedImage, x, y, null);
        } catch (Exception e) {
            BotLogger.error("Could not display play area: " + resourceName, e);
        }
    }
}
