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

    public static Font getFont() {
        if (TI_FONT != null) {
            return TI_FONT;
        }
        URL resource = getURL("Could not find temp directories");
        if (resource == null) return null;
        File file = new File(resource.getPath() + "/font/SLIDER.TTF");
        try (InputStream inputStream = new FileInputStream(file)) {
            TI_FONT = Font.createFont(Font.TRUETYPE_FONT, inputStream);
            TI_FONT = TI_FONT.deriveFont(20f);
        } catch (Exception e) {
            LoggerHandler.log("Could not load font", e);
        }
        return TI_FONT;
    }

    @CheckForNull
    public static File getMapImageStorage(String mapName) {
        URL resource = getURL("Could not find temp directories");
        if (resource == null) return null;
        return new File(resource.getPath() + "/maps/" + mapName);
    }

    @CheckForNull
    public static File getMapImageDirectory() {
        URL resource = getURL("Could not find temp directories");
        if (resource == null) return null;
        return new File(resource.getPath() + "/maps/");
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
        return new File(resource.getPath() + "/maps/" + mapName);
    }

    @CheckForNull
    public static File getDeletedMapStorage(String mapName) {
        URL resource = getURL("Could not find temp directories for maps");
        if (resource == null) return null;
        return new File(resource.getPath() + "/deletedmaps/" + mapName);
    }

    public static void init() {
        URL resource = getURL("Could not find temp directories for maps");
        createDirectory(resource, "/deletedmaps/");
        createDirectory(resource, "/maps/");
    }

    private static void createDirectory(URL resource, String directoryName) {
        File directory = new File(resource.getPath() + directoryName);
        if (!directory.exists()) {
            directory.mkdir();
        }
    }


    @CheckForNull
    public static File getLoggerFile() {
        URL resource = getURL("Could not find temp directories");
        if (resource == null) return null;
        return new File(resource.getPath() + "/log.txt");
    }
}
