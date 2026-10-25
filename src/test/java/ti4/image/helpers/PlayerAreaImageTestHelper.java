package ti4.image.helpers;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.game.Player;
import ti4.image.draw.WebsiteOverlays;
import ti4.image.io.ImageHelper;
import ti4.image.playerarea.PlayerAreaContext;
import ti4.image.playerarea.PlayerAreaImageTest;
import ti4.image.playerarea.PlayerAreas;

@UtilityClass
public class PlayerAreaImageTestHelper {

    private static final String staticDir =
            String.join(File.separator, List.of(".", "src", "test", "java", "ti4", "image", "static", "playerAreas"));

    private static BufferedImage generateTestImage(Player p) {
        int w = 3000;
        int h = 450;
        BufferedImage main = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = main.createGraphics();
        WebsiteOverlays overlays = new WebsiteOverlays();
        System.out.println(overlays.asList());
        PlayerAreas.drawSingle(PlayerAreaContext.publicView(g, p.getGame(), overlays), p, new Point(5, 5), w);
        return main;
    }

    public static void runTest(Player p, String filename) {
        switch (PlayerAreaImageTest.testMode) {
            case Compare -> compareToStatic(p, filename);
            case SaveStatic -> saveOverStatic(p, filename);
            case SaveTemp -> saveOverTemp(p, filename);
        }
    }

    private void compareToStatic(Player p, String fileName) {
        String referenceImagePath = staticDir + File.separator + fileName;
        BufferedImage ref = ImageHelper.read(referenceImagePath);
        BufferedImage test = generateTestImage(p);
        ImageTestHelper.compareBufferedImages(test, ref);
    }

    private void saveOverStatic(Player p, String fileName) {
        String referenceImagePath = staticDir + File.separator + fileName;
        ImageTestHelper.saveOverPath(generateTestImage(p), referenceImagePath);
    }

    private void saveOverTemp(Player p, String fileName) {
        String tempImagePath = staticDir + File.separator + "temp" + File.separator + fileName;
        ImageTestHelper.saveOverPath(generateTestImage(p), tempImagePath);
    }
}
