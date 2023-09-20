package ti4;

import java.util.concurrent.ThreadLocalRandom;
import ti4.helpers.Constants;
import ti4.helpers.Storage;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.HashMap;
import java.util.Random;

public class ResourceHelper {
    private static ResourceHelper resourceHelper;
    private final HashMap<String, String> unitCache = new HashMap<>();
    private final HashMap<String, String> tileCache = new HashMap<>();
    private final HashMap<String, String> ccCache = new HashMap<>();
    private final HashMap<String, String> attachmentCache = new HashMap<>();
    private final HashMap<String, String> tokenCache = new HashMap<>();
    private final HashMap<String, String> factionCache = new HashMap<>();
    private final HashMap<String, String> generalCache = new HashMap<>();
    private final HashMap<String, String> planetCache = new HashMap<>();
    private final HashMap<String, String> paCache = new HashMap<>();

    private ResourceHelper() {
    }

    public static ResourceHelper getInstance() {
        if (resourceHelper == null) {
            resourceHelper = new ResourceHelper();
        }
        return resourceHelper;
    }

    @Nullable
    public String getPositionFile(String name) {
        return getResourceFromFolder("positions/", name, "Could not find position files");
    }

    public String getTileJsonFile(String name) {
        return getResourceFromFolder("systems/", name, "Could not find tile JSON!");
    }

    @Nullable
    public String getTileFile(String name) {
        String unitPath = tileCache.get(name);
        if (unitPath != null) {
            return unitPath;
        }
        String tile = getResourceFromFolder("tiles/", name, "Could not find tile file");
        tileCache.put(name, tile);
        return tile;
    }

    @Nullable
    public String getFactionFile(String name) {
        String unitPath = factionCache.get(name);
        if (unitPath != null) {
            return unitPath;
        }
        String tile = getResourceFromFolder("factions/", name, "Could not find faction file");
        factionCache.put(name, tile);
        return tile;
    }

    @Nullable
    public String getGeneralFile(String name) {
        String unitPath = generalCache.get(name);
        if (unitPath != null) {
            return unitPath;
        }
        String tile = getResourceFromFolder("general/", name, "Could not find general file");
        generalCache.put(name, tile);
        return tile;
    }

    @Nullable
    public String getUnitFile(String name) {
        if (name.endsWith(Constants.UNIT_DD)) {
            if (ThreadLocalRandom.current().nextInt(Constants.EYE_CHANCE) == 0) {
                return getResourceFromFolder("units/new_units/", name.replaceFirst(Constants.UNIT_DD, Constants.UNIT_DD_EYE), "Could not find eye file");
            }
        }
        String unitPath = unitCache.get(name);
        if (unitPath != null) {
            return unitPath;
        }
        String unit = getResourceFromFolder("units/new_units/", name, "Could not find unit file");
        unitCache.put(name, unit);
        return unit;
    }

    @Nullable
    public String getCCFile(String name) {
        String ccPath = ccCache.get(name);
        if (ccPath != null) {
            return ccPath;
        }
        String cc = getResourceFromFolder("command_token/", name, "Could not find command token file");
        ccCache.put(name, cc);
        return cc;
    }

    @Nullable
    public String getAttachmentFile(String name) {
        String tokenPath = attachmentCache.get(name);
        if (tokenPath != null) {
            return tokenPath;
        }
        String token = getResourceFromFolder("attachment_token/", name, "Could not find attachment token file: " + name);
        attachmentCache.put(name, token);
        return token;
    }

    @Nullable
    public String getPlanetResource(String name) {
        String planetInfoPath = planetCache.get(name);
        if (planetInfoPath != null) {
            return planetInfoPath;
        }
        String token = getResourceFromFolder("planet_cards/", name, "Could not find planet token file");
        planetCache.put(name, token);
        return token;
    }

    @Nullable
    public String getPAResource(String name) {
        String paInfoPath = paCache.get(name);
        if (paInfoPath != null) {
            return paInfoPath;
        }
        String token = getResourceFromFolder("player_area/", name, "Could not find player area token file");
        paCache.put(name, token);
        return token;
    }

    @Nullable
    public String getTokenFile(String name) {
        String tokenPath = tokenCache.get(name);
        if (tokenPath != null) {
            return tokenPath;
        }
        String token = getResourceFromFolder("tokens/", name, "Could not find token file");
        tokenCache.put(name, token);
        return token;
    }

    @Nullable
    public String getResourceFromFolder(String folder, String name, String errorDescription) {
        File resourceFile = new File(Storage.getResourcePath() + File.separator + folder + name);
        if (resourceFile.exists()) {
            return resourceFile.getAbsolutePath();
        } else {
            // System.out.println("Could not find resource file " + name + " in folder " + folder);
            // System.out.println(errorDescription);
        }
        return null;
    }

    @Nullable
    public String getInfoFile(String name) {
        return getResourceFromFolder("info/", name, "Could not find info file");
    }

    @Nullable
    public String getWebFile(String name) {
        return getResourceFromFolder("web/", name, "Could not find web file");
    }

    @Nullable
    public String getAliasFile(String name) {
        return getResourceFromFolder("alias/", name, "Could not find alias file");
    }

    @Nullable
    public String getHelpFile(String name) {
        return getResourceFromFolder("help/", name, "Could not find alias file");
    }
}
