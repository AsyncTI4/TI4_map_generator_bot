package ti4.image.helpers;

import java.awt.Graphics;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.image.ImageHelper;
import ti4.image.PlayerAreaGenerator;
import ti4.image.PlayerAreaImageTest;
import ti4.map.Player;
import ti4.website.model.WebsiteOverlay;

@UtilityClass
public class PlayerAreaImageTestHelper {

    private static final String staticDir =
            String.join(File.separator, List.of(".", "src", "test", "java", "ti4", "image", "static", "playerAreas"));

    private static BufferedImage generateTestImage(Player p) {
        int w = 3000;
        int h = 450;
        BufferedImage main = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics g = main.getGraphics();
        List<WebsiteOverlay> overlays = new ArrayList<>();
        PlayerAreaGenerator gen = new PlayerAreaGenerator(g, p.getGame(), false, null, overlays, w, 54);
        System.out.println(overlays);
        gen.drawPlayerAreaOLD(p, new Point(5, 5));
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
