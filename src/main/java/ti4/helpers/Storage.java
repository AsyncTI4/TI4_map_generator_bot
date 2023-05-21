package ti4.helpers;

import org.jetbrains.annotations.Nullable;
import ti4.message.BotLogger;

import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

public class Storage {

    public static final String MAPS_UNDO = "/maps/undo/";
    public static final String MAPS = "/maps/";
    public static final String MAPS_JSON = "/maps_json/";
    public static final String DELETED_MAPS = "/deletedmaps/";
    public static final String TTPG_EXPORTS = "/ttpg_exports/";
    private static Font TI_FONT_8 = null;
    private static Font TI_FONT_12 = null;
    private static Font TI_FONT_14 = null;
    private static Font TI_FONT_16 = null;
    private static Font TI_FONT_18 = null;
    private static Font TI_FONT_20 = null;
    private static Font TI_FONT_24 = null;
    private static Font TI_FONT_26 = null;
    private static Font TI_FONT_28 = null;
    private static Font TI_FONT_30 = null;
    private static Font TI_FONT_32 = null;
    private static Font TI_FONT_35 = null;
    private static Font TI_FONT_40 = null;
    private static Font TI_FONT_50 = null;
    private static Font TI_FONT_64 = null;

    public static Font getFont8() {
        if (TI_FONT_8 != null) {
            return TI_FONT_8;
        }
        TI_FONT_8 = getFont(8f);
        return TI_FONT_8;
    }

    public static Font getFont12() {
        if (TI_FONT_12 != null) {
            return TI_FONT_12;
        }
        TI_FONT_12 = getFont(12f);
        return TI_FONT_12;
    }

    public static Font getFont14() {
        if (TI_FONT_14 != null) {
            return TI_FONT_14;
        }
        TI_FONT_14 = getFont(14f);
        return TI_FONT_14;
    }

    public static Font getFont16() {
        if (TI_FONT_16 != null) {
            return TI_FONT_16;
        }
        TI_FONT_16 = getFont(16f);
        return TI_FONT_16;
    }

    public static Font getFont18() {
        if (TI_FONT_18 != null) {
            return TI_FONT_18;
        }
        TI_FONT_18 = getFont(18f);
        return TI_FONT_18;
    }

    public static Font getFont20() {
        if (TI_FONT_20 != null) {
            return TI_FONT_20;
        }
        TI_FONT_20 = getFont(20f);
        return TI_FONT_20;
    }

    public static Font getFont26() {
        if (TI_FONT_26 != null) {
            return TI_FONT_26;
        }
        TI_FONT_26 = getFont(26f);
        return TI_FONT_26;
    }
    public static Font getFont28() {
        if (TI_FONT_28 != null) {
            return TI_FONT_28;
        }
        TI_FONT_28 = getFont(28f);
        return TI_FONT_28;
    }
    public static Font getFont30() {
        if (TI_FONT_30 != null) {
            return TI_FONT_30;
        }
        TI_FONT_30 = getFont(30f);
        return TI_FONT_30;
    }

    public static Font getFont24() {
        if (TI_FONT_24 != null) {
            return TI_FONT_24;
        }
        TI_FONT_24 = getFont(24f);
        return TI_FONT_24;
    }

    public static Font getFont32() {
        if (TI_FONT_32 != null) {
            return TI_FONT_32;
        }
        TI_FONT_32 = getFont(32f);
        return TI_FONT_32;
    }

    public static Font getFont35() {
        if (TI_FONT_35 != null) {
            return TI_FONT_35;
        }
        TI_FONT_35 = getFont(35f);
        return TI_FONT_35;
    }

    public static Font getFont64() {
        if (TI_FONT_64 != null) {
            return TI_FONT_64;
        }
        TI_FONT_64 = getFont(64f);
        return TI_FONT_64;
    }

    public static Font getFont40() {
        if (TI_FONT_40 != null) {
            return TI_FONT_40;
        }
        TI_FONT_40 = getFont(40f);
        return TI_FONT_40;
    }

    public static Font getFont50() {
        if (TI_FONT_50 != null) {
            return TI_FONT_50;
        }
        TI_FONT_50 = getFont(50f);
        return TI_FONT_50;
    }

    private static Font getFont(float size) {
        Font tiFont = null;
        String resource = getResourcePath();
        if (resource == null) return tiFont;
        File file = new File(resource + "/font/SLIDER.TTF");
        try (InputStream inputStream = new FileInputStream(file)) {
            tiFont = Font.createFont(Font.TRUETYPE_FONT, inputStream);
            tiFont = tiFont.deriveFont(size);
        } catch (Exception e) {
            BotLogger.log("Could not load font", e);
        }
        return tiFont;
    }

    @Nullable
    public static File getMapUndoStorage(String mapName) {
        return new File(getStoragePath() + MAPS_UNDO + mapName);
    }

    @Nullable
    public static File getMapUndoDirectory() {
        return new File(getStoragePath() + MAPS_UNDO);
    }

    @Nullable
    public static File getMapImageStorage(String mapName) {
        return new File(getStoragePath() + MAPS + mapName);
    }

    @Nullable
    public static File getMapImageDirectory() {
        return new File(getStoragePath() + MAPS);
    }

    @Nullable
    public static String getResourcePath() {
        return System.getenv("RESOURCE_PATH");
    }

    @Nullable
    public static File getMapStorage(String mapName) {
        return new File(getStoragePath() + MAPS + mapName);
    }

    @Nullable
    public static File getDeletedMapStorage(String mapName) {
        return new File(getStoragePath() + DELETED_MAPS + mapName);
    }

    @Nullable
    public static File getTTPGExportDirectory() {
        return new File(getStoragePath() + TTPG_EXPORTS);
    }

    @Nullable
    public static File getTTPGExportStorage(String fileName) {
        return new File(getStoragePath() + TTPG_EXPORTS + fileName);
    }

    @Nullable
    public static File getMapsJSONDirectory() {
        return new File(getStoragePath() + MAPS_JSON);
    }

    @Nullable
    public static File getMapsJSONStorage(String fileName) {
        return new File(getStoragePath() + MAPS_JSON + fileName);
    }

    public static void init() {
        String resource = getStoragePath();
        if(resource!=null) {
            createDirectory(resource, DELETED_MAPS);
            createDirectory(resource, MAPS);
            createDirectory(resource, MAPS_JSON);
            createDirectory(resource, TTPG_EXPORTS);
        }
    }

    private static void createDirectory(String resource, String directoryName) {
        File directory = new File(getStoragePath() + directoryName);
        if (!directory.exists()) {
            directory.mkdir();
        }
    }

    @Nullable
    public static File getLoggerFile() {
        String resource = getStoragePath();
        if (resource == null) return null;
        return new File(getStoragePath() + "/log/log.txt");
    }

    public static String getStoragePath() {
        return System.getenv("DB_PATH");
    }
}
