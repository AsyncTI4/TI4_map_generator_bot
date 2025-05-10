package ti4.image.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.junit.jupiter.api.Assertions;

import ti4.helpers.DisplayType;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.image.ImageHelper;
import ti4.image.Mapper;
import ti4.image.TileGenerator;
import ti4.image.TileImageTest;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;

@UtilityClass
public class TileImageTestHelper {

    private static final String staticDir = String.join(File.separator, List.of(".", "src", "test", "java", "ti4", "image", "static", "tileLayout"));

    public static BufferedImage generateTestImage(Tile tile) {
        TileGenerator gen = new TileGenerator(TileImageTest.testGame, null, DisplayType.all, 0, tile.getPosition());
        return gen.createMainImage();
    }

    public static void runTest(Tile t, String filename) {
        switch (TileImageTest.testMode) {
            case Compare -> compareToStatic(t, filename);
            case SaveStatic -> saveOverStatic(t, filename);
            case SaveTemp -> saveOverTemp(t, filename);
        }
    }

    public void addUnitsToUnitHolder(Player p, Tile t, String holderName, UnitType... units) {
        for (UnitType ut : units) {
            t.addUnit(holderName, Units.getUnitKey(ut, p.getColorID()), 1);
        }
    }

    public void addUnitsAndControlToPlanet(Player p, Tile t, String planetName, UnitType... units) {
        Planet planet = t.getUnitHolderFromPlanet(planetName);
        if (planet == null) return;
        p.addPlanet(planetName);
        planet.addControl(Mapper.getControlID(p.getColor()));
        addUnitsToUnitHolder(p, t, planetName, units);
    }

    public void addTokensToHolder(Tile t, String holderName, String... tokens) {
        UnitHolder uh = t.getUnitHolders().get(holderName);
        if (uh == null) return;
        for (String token : tokens)
            uh.addToken(token);
    }

    private void compareBufferedImages(BufferedImage a, BufferedImage reference) {
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

    public void compareToStatic(Tile tile, String fileName) {
        String referenceImagePath = staticDir + File.separator + fileName;
        BufferedImage ref = ImageHelper.read(referenceImagePath);
        BufferedImage test = generateTestImage(tile);
        compareBufferedImages(test, ref);
    }

    private void saveOverPath(BufferedImage img, String path) {
        Assertions.assertNotNull(img);
        try (FileOutputStream fileOutputStream = new FileOutputStream(path)) {
            long before = new File(path).lastModified();
            byte[] imageBytes = ImageHelper.writePng(img);
            fileOutputStream.write(imageBytes);
            long after = new File(path).lastModified();
            Assertions.assertNotEquals(before, after);
        } catch (IOException e) {
            assertThat("Could not create File at " + path);
        }
    }

    private void saveOverStatic(Tile tile, String fileName) {
        String referenceImagePath = staticDir + File.separator + fileName;
        saveOverPath(generateTestImage(tile), referenceImagePath);
    }

    private void saveOverTemp(Tile tile, String fileName) {
        String tempImagePath = staticDir + File.separator + "temp" + File.separator + fileName;
        saveOverPath(generateTestImage(tile), tempImagePath);
    }
}
