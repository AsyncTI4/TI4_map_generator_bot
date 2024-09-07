package ti4.model;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import ti4.ResourceHelper;
import ti4.generator.Mapper;
import ti4.helpers.Emojis;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

public class ColorModelTest extends BaseTi4Test {
    @Test
    void testColors() {
        beforeAll();

        List<ColorModel> models = Mapper.getColors();

        Set<String> aliasList = new HashSet<>();
        Set<String> nameList = new HashSet<>();
        Set<String> allAliases = new HashSet<>();
        for (ColorModel color : models) {
            assertTrue(aliasList.add(color.getAlias()), "Duplicate colorID detected: " + color.getAlias());
            assertTrue(nameList.add(color.getName()), "Duplicate colorName detected: " + color.getName());
            for (String alias : color.getAliases())
                assertTrue(allAliases.add(alias), "Duplicate color alias detected: " + alias);
            checkEmojisConfig(color);
            checkUnitImages(color);
            checkTokenImages(color);
        }
    }

    private static boolean isDefault(String emoji) {
        if (emoji == null) return true;
        if (Emojis.GoodDogs.contains(emoji)) return true;
        if (!emoji.contains("<")) return true;
        return false;
    }

    private static void checkEmojisConfig(ColorModel color) {
        // TOOD: Enable this when switching to Application Emojis
        // try {
        //     TI4Emoji.valueOf(color.getName());
        // } catch (Exception e) {
        //     assertTrue(false, "Color has no emoji configured: " + color.getName());
        // }

        // Verify the emoji file
        String r1 = Emojis.getColorEmoji(color.getAlias());
        String r2 = Emojis.getColorEmoji(color.getName());
        String r3 = Emojis.getColorEmojiWithName(color.getAlias());
        String r4 = Emojis.getColorEmojiWithName(color.getName());
        assertTrue(!isDefault(r1), color.getAlias() + " is missing configuration in `Emojis::getColorEmoji.");
        assertTrue(!isDefault(r2), color.getName() + " is missing configuration in `Emojis::getColorEmoji.");
        assertTrue(!isDefault(r3), color.getAlias() + " is missing configuration in `Emojis::getColorEmojiWithName.");
        assertTrue(!isDefault(r4), color.getName() + " is missing configuration in `Emojis::getColorEmojiWithName.");
    }

    private static String unitPath(UnitKey uk, boolean eyes) {
        String fileName = uk.getFileName(eyes);
        String path = ResourceHelper.getInstance().getResourceFromFolder("units/", fileName, "Could not find unit file");
        assertTrue(path != null, "Could not format path for " + uk.toString() + (eyes?" [eyes]":""));
        return path;
    }

    private static void checkUnitImages(ColorModel color) {
        List<UnitType> unitsToTest = List.of(UnitType.Spacedock, UnitType.Pds, UnitType.CabalSpacedock,
            UnitType.Infantry, UnitType.Fighter, UnitType.Mech,
            UnitType.Destroyer, UnitType.Cruiser, UnitType.Carrier, UnitType.Dreadnought,
            UnitType.Flagship, UnitType.Warsun);

        for (UnitType type : unitsToTest) {
            UnitKey uk = Units.getUnitKey(type.getValue(), color.getAlias());
            File f = new File(unitPath(uk, false));
            assertTrue(f.exists(), "Unit [" + color.getAlias() + " " + uk.asyncID() + "] does not have an associated file");

            if (type == UnitType.Destroyer) {
                f = new File(unitPath(uk, true));
                assertTrue(f.exists(), "Unit [" + color.getAlias() + " " + uk.asyncID() + " eyes] does not have an associated file");
            }
        }
    }

    private static void checkTokenImages(ColorModel color) {
        List<String> tokenIDs = List.of(
            Mapper.getCCID(color.getAlias()),
            Mapper.getFleetCCID(color.getAlias()),
            Mapper.getControlID(color.getAlias()),
            Mapper.getSweepID(color.getAlias()));
        for (String id : tokenIDs) {
            String path = Mapper.getCCPath(id);
            assertTrue(path != null, "Path bad for token: " + id);
            File f = new File(path);
            assertTrue(f.exists(), "Token [" + id + "] does not have an associated file");
        }
    }
}
