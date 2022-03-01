package ti4;

import javax.annotation.CheckForNull;
import java.io.File;
import java.net.URL;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ResourceHelper {

    private Logger logger = Logger.getLogger(ResourceHelper.class.getName());

    public File getResource(String name) {
        File resourceFile = null;
        URL resource = getClass().getClassLoader().getResource(name);

        try {
            if (resource != null) {
                resourceFile = Paths.get(resource.toURI()).toFile();
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Could not find asset", e);
        }
        return resourceFile;
    }

    @CheckForNull
    public String getPositionFile(String name)
    {
        File resourceFile = null;
        URL resource = getClass().getClassLoader().getResource("positions/" + name);

        try {
            if (resource != null) {
                resourceFile = Paths.get(resource.toURI()).toFile();
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Could not find position files", e);
        }
        return resourceFile != null ? resourceFile.getAbsolutePath() : null;
    }
}
