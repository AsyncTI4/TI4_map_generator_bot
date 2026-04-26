package ti4.image.helpers;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import lombok.experimental.UtilityClass;
import org.junit.jupiter.api.Assertions;
import ti4.image.ImageHelper;

@UtilityClass
public class ImageTestHelper {

    public enum TestMode {
        Compare,
        SaveStatic,
        SaveTemp
    }

    public static void compareBufferedImages(BufferedImage a, BufferedImage reference) {
        long totalDist = 0;
        Assertions.assertNotNull(a);
        Assertions.assertNotNull(reference, "Missing reference image");
        Assertions.assertEquals(a.getWidth(), reference.getWidth());
        Assertions.assertEquals(a.getHeight(), reference.getHeight());
        for (int x = 0; x < a.getWidth(); x++) {
            for (int y = 0; y < a.getHeight(); y++) {
                totalDist += Math.abs(a.getRGB(x, y) - reference.getRGB(x, y));
            }
        }
        Assertions.assertTrue(totalDist < 10);
    }

    public static void saveOverPath(BufferedImage img, String path) {
        Assertions.assertNotNull(img);
        try (FileOutputStream fileOutputStream = new FileOutputStream(path)) {
            long before = new File(path).lastModified();
            byte[] imageBytes = ImageHelper.writePng(img);
            fileOutputStream.write(imageBytes);
            long after = new File(path).lastModified();
            Assertions.assertNotEquals(before, after);
        } catch (IOException e) {
            Assertions.fail("Could not create File at " + path);
        }
    }
}
