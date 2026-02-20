package ti4.image.helpers;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import lombok.experimental.UtilityClass;
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

    private static final String staticDir =
            String.join(File.separator, List.of(".", "src", "test", "java", "ti4", "image", "static", "tileLayout"));

    private static BufferedImage generateTestImage(Tile tile) {
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
        for (String token : tokens) uh.addToken(token);
    }

    private void compareToStatic(Tile tile, String fileName) {
        String referenceImagePath = staticDir + File.separator + fileName;
        BufferedImage ref = ImageHelper.read(referenceImagePath);
        BufferedImage test = generateTestImage(tile);
        ImageTestHelper.compareBufferedImages(test, ref);
    }

    private void saveOverStatic(Tile tile, String fileName) {
        String referenceImagePath = staticDir + File.separator + fileName;
        ImageTestHelper.saveOverPath(generateTestImage(tile), referenceImagePath);
    }

    private void saveOverTemp(Tile tile, String fileName) {
        String tempImagePath = staticDir + File.separator + "temp" + File.separator + fileName;
        ImageTestHelper.saveOverPath(generateTestImage(tile), tempImagePath);
    }
}
