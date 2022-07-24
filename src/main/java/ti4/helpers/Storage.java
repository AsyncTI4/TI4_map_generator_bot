package ti4.helpers;

import org.jetbrains.annotations.Nullable;

import javax.annotation.CheckForNull;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

public class Storage {

    public static final String MAPS_UNDO = "/maps/undo/";
    public static final String MAPS = "/maps/";
    public static final String DELETED_MAPS = "/deletedmaps/";
    private static Font TI_FONT_20 = null;
    private static Font TI_FONT_24 = null;
    private static Font TI_FONT_26 = null;
    private static Font TI_FONT_28 = null;
    private static Font TI_FONT_30 = null;
    private static Font TI_FONT_32 = null;
    private static Font TI_FONT_50 = null;
    private static Font TI_FONT_64 = null;

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

    public static Font getFont64() {
        if (TI_FONT_64 != null) {
            return TI_FONT_64;
        }
        TI_FONT_64 = getFont(64f);
        return TI_FONT_64;
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
            LoggerHandler.log("Could not load font", e);
        }
        return tiFont;
    }

    @CheckForNull
    public static File getMapUndoStorage(String mapName) {
        return new File(getStoragePath() + MAPS_UNDO + mapName);
    }

    @CheckForNull
    public static File getMapUndoDirectory() {
        return new File(getStoragePath() + MAPS_UNDO);
    }

    @CheckForNull
    public static File getMapImageStorage(String mapName) {
        return new File(getStoragePath() + MAPS + mapName);
    }

    @CheckForNull
    public static File getMapImageDirectory() {
        return new File(getStoragePath() + MAPS);
    }

    @Nullable
    public static String getResourcePath() {
        return System.getenv("RESOURCE_PATH");
    }

    @CheckForNull
    public static File getMapStorage(String mapName) {
        return new File(getStoragePath() + MAPS + mapName);
    }

    @CheckForNull
    public static File getDeletedMapStorage(String mapName) {
        return new File(getStoragePath() + DELETED_MAPS + mapName);
    }

    public static void init() {
        String resource = getStoragePath();
        if(resource!=null) {
            createDirectory(resource, DELETED_MAPS);
            createDirectory(resource, MAPS);
        }
    }

    private static void createDirectory(String resource, String directoryName) {
        File directory = new File(getStoragePath() + directoryName);
        if (!directory.exists()) {
            directory.mkdir();
        }
    }


    @CheckForNull
    public static File getLoggerFile() {
        String resource = getStoragePath();
        if (resource == null) return null;
        return new File(getStoragePath() + "/log.txt");
    }

    private static String getStoragePath() {
        String db_path = System.getenv("DB_PATH");
        System.out.println("db_path: " + db_path );
        return db_path;
    }
}
