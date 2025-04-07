package ti4.model;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import ti4.ResourceHelper;
import ti4.image.Mapper;
import ti4.service.emoji.ColorEmojis;
import ti4.service.emoji.TI4Emoji;
import ti4.service.emoji.MiscEmojis;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        for (TI4Emoji doggy : MiscEmojis.goodDogs())
            if (emoji.contains(doggy.toString())) return true;
        return !emoji.contains("<");
    }

    private static void checkEmojisConfig(ColorModel color) {
        // TOOD: Enable this when switching to Application Emojis
        // try {
        //     TI4Emoji.valueOf(color.getName());
        // } catch (Exception e) {
        //     assertTrue(false, "Color has no emoji configured: " + color.getName());
        // }

        // Verify the emoji file
        String r1 = ColorEmojis.getColorEmoji(color.getAlias()).toString();
        String r2 = ColorEmojis.getColorEmoji(color.getName()).toString();
        String r3 = ColorEmojis.getColorEmojiWithName(color.getAlias());
        String r4 = ColorEmojis.getColorEmojiWithName(color.getName());
        assertFalse(isDefault(r1), color.getAlias() + " is missing configuration in `ColorEmojis.getColorEmoji.");
        assertFalse(isDefault(r2), color.getName() + " is missing configuration in `ColorEmojis.getColorEmoji.");
        assertFalse(isDefault(r3), color.getAlias() + " is missing configuration in `ColorEmojis.getColorEmojiWithName.");
        assertFalse(isDefault(r4), color.getName() + " is missing configuration in `ColorEmojis.getColorEmojiWithName.");
    }

    private static String unitPath(UnitKey uk, boolean eyes) {
        String fileName = uk.getFileName(eyes);
        String path = ResourceHelper.getResourceFromFolder("units/", fileName);
        assertNotNull(path, "Could not find unit file: " + fileName);
        return path;
    }

    private static void checkUnitImages(ColorModel color) {
        List<UnitType> unitsToTest = List.of(UnitType.Spacedock, UnitType.Pds,
            UnitType.Infantry, UnitType.Fighter, UnitType.Mech,
            UnitType.Destroyer, UnitType.Cruiser, UnitType.Carrier, UnitType.Dreadnought,
            UnitType.Flagship, UnitType.Warsun);

        for (UnitType type : unitsToTest) {
            UnitKey uk = Units.getUnitKey(type.getValue(), color.getAlias());
            unitPath(uk, false);
            if (type == UnitType.Destroyer) {
                unitPath(uk, true);
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
            assertNotNull(path, "Could not find token file: " + id);
        }
    }
}
