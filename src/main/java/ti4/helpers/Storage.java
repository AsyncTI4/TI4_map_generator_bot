package ti4.helpers;

import org.jetbrains.annotations.Nullable;

import javax.annotation.CheckForNull;
import java.io.File;
import java.net.URL;

public class Storage {

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
        if (! directory.exists()){
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
