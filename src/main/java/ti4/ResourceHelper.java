package ti4;

import org.jetbrains.annotations.Nullable;
import ti4.helpers.LoggerHandler;

import javax.annotation.CheckForNull;
import java.io.File;
import java.net.URL;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ResourceHelper {
    private static ResourceHelper resourceHelper = null;
    private HashMap<String, String> unitCache = new HashMap<>();
    private HashMap<String, String> tileCache = new HashMap<>();
    private HashMap<String, String> ccCache = new HashMap<>();
    private HashMap<String, String> attachmentCache = new HashMap<>();
    private HashMap<String, String> tokenCache = new HashMap<>();

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
        String unitPath = tileCache.get(name);
        if (unitPath != null)
        {
            return unitPath;
        }
        String tile = getResourceFromFolder("tiles/", name, "Could not find tile file");
        tileCache.put(name, tile);
        return tile;
    }

    @CheckForNull
    public String getUnitFile(String name) {
        String unitPath = unitCache.get(name);
        if (unitPath != null) {
            return unitPath;
        }
        String unit = getResourceFromFolder("units/", name, "Could not find unit file");
        unitCache.put(name, unit);
        return unit;
    }
    @CheckForNull
    public String getCCFile(String name)
    {
        String ccPath = ccCache.get(name);
        if (ccPath != null)
        {
            return ccPath;
        }
        String cc = getResourceFromFolder("command_token/", name, "Could not find command token file");
        ccCache.put(name, cc);
        return cc;
    }

    @CheckForNull
    public String getAttachmentFile(String name)
    {
        String tokenPath = attachmentCache.get(name);
        if (tokenPath != null)
        {
            return tokenPath;
        }
        String token = getResourceFromFolder("attachment_token/", name, "Could not find attachment token file");
        attachmentCache.put(name, token);
        return token;
    }

    @CheckForNull
    public String getTokenFile(String name)
    {
        String tokenPath = tokenCache.get(name);
        if (tokenPath != null)
        {
            return tokenPath;
        }
        String token = getResourceFromFolder("tokens/", name, "Could not find token file");
        tokenCache.put(name, token);
        return token;
    }

    @Nullable
    private String getResourceFromFolder(String folder, String name, String errorDescription) {
        File resourceFile = null;
        URL resource = getClass().getClassLoader().getResource(folder + name);

        try {
            if (resource != null) {
                resourceFile = Paths.get(resource.toURI()).toFile();
            }

        } catch (Exception e) {
            LoggerHandler.log(errorDescription, e);
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

    @CheckForNull
    public String getHelpFile(String name)
    {
        return getResourceFromFolder("help/", name, "Could not find alias file");
    }
}
