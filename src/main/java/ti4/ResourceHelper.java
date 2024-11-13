package ti4;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.jetbrains.annotations.Nullable;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.helpers.Units.UnitKey;

public class ResourceHelper {
    private static ResourceHelper resourceHelper;
    private final Map<String, String> unitCache = new HashMap<>();
    private final Map<String, String> decalCache = new HashMap<>();
    private final Map<String, String> spoopyCache = new HashMap<>();
    private final Map<String, String> tileCache = new HashMap<>();
    private final Map<String, String> ccCache = new HashMap<>();
    private final Map<String, String> peekMarkerCache = new HashMap<>();
    private final Map<String, String> attachmentCache = new HashMap<>();
    private final Map<String, String> tokenCache = new HashMap<>();
    private final Map<String, String> factionCache = new HashMap<>();
    private final Map<String, String> generalCache = new HashMap<>();
    private final Map<String, String> planetCache = new HashMap<>();
    private final Map<String, String> paCache = new HashMap<>();

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

    //@Deprecated
    @Nullable
    public String getUnitFile(String name) {
        if (name.endsWith(Constants.UNIT_DD)) {
            if (ThreadLocalRandom.current().nextInt(Constants.EYE_CHANCE) == 0) {
                return getResourceFromFolder("units/", name.replaceFirst(Constants.UNIT_DD, Constants.UNIT_DD_EYE), "Could not find eye file");
            }
        }
        String unitPath = unitCache.get(name);
        if (unitPath != null) {
            return unitPath;
        }
        String unit = getResourceFromFolder("units/", name, "Could not find unit file");
        unitCache.put(name, unit);
        return unit;
    }

    @Nullable
    public String getUnitFile(UnitKey unit) {
        String name = unit.getFileName();
        String unitPath = unitCache.get(name);
        if (unitPath != null) {
            return unitPath;
        }
        String filePath = getResourceFromFolder("units/", name, "Could not find unit file");
        unitCache.put(name, filePath);
        return filePath;
    }

    @Nullable
    public String getDecalFile(String name) {
        String decalPath = decalCache.get(name);
        if (decalPath != null) {
            return decalPath;
        }
        String unit = getResourceFromFolder("decals/", name, "Could not find decal file");
        decalCache.put(name, unit);
        return unit;
    }

    @Nullable
    public String getSpoopyFile() {
        // overlay_jackolantern_1
        int face = ThreadLocalRandom.current().nextInt(1, 4);
        String name = "overlay_jackolantern_" + face + ".png";
        String spoopyPath = spoopyCache.get(name);
        if (spoopyPath != null) {
            return spoopyPath;
        }
        String unit = getResourceFromFolder("decals/", name, "Could not find decal file");
        spoopyCache.put(name, unit);
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
    public String getPeekMarkerFile(String name) {
        String markerPath = peekMarkerCache.get(name);
        if (markerPath != null) {
            return markerPath;
        }
        String marker = getResourceFromFolder("peek_marker/", name, "Could not find peek marker file");
        peekMarkerCache.put(name, marker);
        return marker;
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
    public String getExtraFile(String name) {
        String tokenPath = tokenCache.get(name);
        if (tokenPath != null) {
            return tokenPath;
        }
        String token = getResourceFromFolder("extra/", name, "Could not find token file");
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
    public String getDataFile(String name) {
        return getResourceFromFolder("data/", name, "Could not find data file");
    }

    public String getDataFolder(String name) {
        return Storage.getResourcePath() + File.separator + "data" + File.separator + name;
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
