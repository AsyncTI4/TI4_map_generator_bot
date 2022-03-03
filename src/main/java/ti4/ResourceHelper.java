package ti4;

import org.jetbrains.annotations.Nullable;
import ti4.helpers.LoggerHandler;

import javax.annotation.CheckForNull;
import java.io.File;
import java.net.URL;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ResourceHelper {
    private static ResourceHelper resourceHelper = null;

    private ResourceHelper() {
    }

    public static ResourceHelper getInstance() {
        if (resourceHelper == null) {
            resourceHelper = new ResourceHelper();
        }
        return resourceHelper;
    }

    public File getResource(String name) {
        File resourceFile = null;
        URL resource = getClass().getClassLoader().getResource(name);

        try {
            if (resource != null) {
                resourceFile = Paths.get(resource.toURI()).toFile();
            }

        } catch (Exception e) {
            LoggerHandler.log("Could not find asset", e);
        }
        return resourceFile;
    }

    @CheckForNull
    public String getPositionFile(String name)
    {
        return getResourceFromFolder("positions/", name, "Could not find position files");
    }

    @CheckForNull
    public String getTileFile(String name)
    {
        return getResourceFromFolder("tiles/", name, "Could not find tile file");
    }

    @CheckForNull
    public String getUnitFile(String name)
    {
        return getResourceFromFolder("units/", name, "Could not find tile file");
    }

    @Nullable
    private String getResourceFromFolder(String folder, String name, String Could_not_find_tile_file) {
        File resourceFile = null;
        URL resource = getClass().getClassLoader().getResource(folder + name);

        try {
            if (resource != null) {
                resourceFile = Paths.get(resource.toURI()).toFile();
            }

        } catch (Exception e) {
            LoggerHandler.log(Could_not_find_tile_file, e);
        }
        return resourceFile != null ? resourceFile.getAbsolutePath() : null;
    }

    @CheckForNull
    public String getInfoFile(String name)
    {
        return getResourceFromFolder("info/", name, "Could not find info file");
    }

    @CheckForNull
    public String getAliasFile(String name)
    {
        return getResourceFromFolder("alias/", name, "Could not find alias file");
    }
}
