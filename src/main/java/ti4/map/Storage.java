package ti4.map;

import ti4.helpers.LoggerHandler;

import javax.annotation.CheckForNull;
import java.io.File;
import java.net.URL;

public class Storage {

    @CheckForNull
    public static File getMapStorage(String mapName) {
        URL resource = ClassLoader.getSystemClassLoader().getResource(".");
        if (resource == null) {
            LoggerHandler.log("Could not find temp directories");
            return null;
        }
        return new File(resource.getPath() + "/" + mapName);
    }

    @CheckForNull
    public static File getLoggerFile() {
        URL resource = ClassLoader.getSystemClassLoader().getResource(".");
        if (resource == null) {
            LoggerHandler.log("Could not find temp directories");
            return null;
        }
        return new File(resource.getPath() + "/log.txt");
    }
}
