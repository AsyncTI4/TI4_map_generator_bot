package ti4.helpers;

import org.jetbrains.annotations.Nullable;

import javax.annotation.CheckForNull;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;

public class Storage {

    private static Font TI_FONT = null;
    private static Font TI_FONT_LARGE = null;

    public static Font getFont() {
        if (TI_FONT != null) {
            return TI_FONT;
        }
        TI_FONT = getFont(20f);
        return TI_FONT;
    }

    public static Font getLargeFont() {
        if (TI_FONT_LARGE != null) {
            return TI_FONT_LARGE;
        }
        TI_FONT_LARGE = getFont(26f);
        return TI_FONT_LARGE;
    }

    private static Font getFont(float size) {
        Font tiFont = null;
        URL resource = getURL("Could not find temp directories");
        if (resource == null) return tiFont;
        File file = new File(resource.getPath() + "/font/SLIDER.TTF");
        try (InputStream inputStream = new FileInputStream(file)) {
            tiFont = Font.createFont(Font.TRUETYPE_FONT, inputStream);
            tiFont = tiFont.deriveFont(size);
        } catch (Exception e) {
            LoggerHandler.log("Could not load font", e);
        }
        return tiFont;
    }

    @CheckForNull
    public static File getMapImageStorage(String mapName) {
        URL resource = getURL("Could not find temp directories");
        if (resource == null) return null;
        return new File(getPath(resource) + "/maps/" + mapName);
    }

    @CheckForNull
    public static File getMapImageDirectory() {
        URL resource = getURL("Could not find temp directories");
        if (resource == null) return null;
        return new File(getPath(resource) + "/maps/");
    }

    @Nullable
    private static URL getURL(String Could_not_find_temp_directories) {
        URL resource = ClassLoader.getSystemClassLoader().getResource(".");
        if (resource == null) {
            LoggerHandler.log(Could_not_find_temp_directories);
            return null;
        }
        return resource;
    }

    @CheckForNull
    public static File getMapStorage(String mapName) {
        URL resource = getURL("Could not find temp directories for maps");
        if (resource == null) return null;
        return new File(getPath(resource) + "/maps/" + mapName);
    }

    @CheckForNull
    public static File getDeletedMapStorage(String mapName) {
        URL resource = getURL("Could not find temp directories for maps");
        if (resource == null) return null;
        return new File(getPath(resource) + "/deletedmaps/" + mapName);
    }

    public static void init() {
        URL resource = getURL("Could not find temp directories for maps");
        createDirectory(resource, "/deletedmaps/");
        createDirectory(resource, "/maps/");
    }

    private static void createDirectory(URL resource, String directoryName) {
        File directory = new File(getPath(resource) + directoryName);
        if (!directory.exists()) {
            directory.mkdir();
        }
    }


    @CheckForNull
    public static File getLoggerFile() {
        URL resource = getURL("Could not find temp directories");
        if (resource == null) return null;
        return new File(getPath(resource) + "/log.txt");
    }

    private static String getPath(URL resource) {
        String envPath = System.getenv("PATH");
        return envPath != null ? envPath : resource.getPath();
    }
}
