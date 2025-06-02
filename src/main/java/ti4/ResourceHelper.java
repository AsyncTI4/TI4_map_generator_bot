package ti4;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.jetbrains.annotations.Nullable;

import ti4.helpers.Constants;
import ti4.helpers.RandomHelper;
import ti4.helpers.Storage;
import ti4.helpers.Units.UnitKey;

public class ResourceHelper {
    private static ResourceHelper resourceHelper;
    private final Map<String, String> unitCache = new HashMap<>();
    private final Map<String, String> decalCache = new HashMap<>();
    private final Map<String, String> spoopyCache = new HashMap<>();
    private final Map<String, String> ccCache = new HashMap<>();
    private final Map<String, String> peekMarkerCache = new HashMap<>();
    private final Map<String, String> factionCache = new HashMap<>();
    private final Map<String, String> generalCache = new HashMap<>();
    private final Map<String, String> planetCache = new HashMap<>();

    private ResourceHelper() {}

    public static ResourceHelper getInstance() {
        if (resourceHelper == null) {
            resourceHelper = new ResourceHelper();
        }
        return resourceHelper;
    }

    @Nullable
    public String getPositionFile(String name) {
        return getResourceFromFolder("positions/", name);
    }

    @Nullable
    public String getTileFile(String name) {
        return getResourceFromFolder("tiles/", name);
    }

    @Nullable
    public String getFactionFile(String name) {
        String unitPath = factionCache.get(name);
        if (unitPath != null) {
            return unitPath;
        }
        String tile = getResourceFromFolder("factions/", name);
        factionCache.put(name, tile);
        return tile;
    }

    @Nullable
    public String getGeneralFile(String name) {
        String unitPath = generalCache.get(name);
        if (unitPath != null) {
            return unitPath;
        }
        String tile = getResourceFromFolder("general/", name);
        generalCache.put(name, tile);
        return tile;
    }

    //@Deprecated
    @Nullable
    public String getUnitFile(String name) {
        if (name.endsWith(Constants.UNIT_DD)) {
            if (RandomHelper.isOneInX(Constants.EYE_CHANCE)) {
                return getResourceFromFolder("units/", name.replaceFirst(Constants.UNIT_DD, Constants.UNIT_DD_EYE));
            }
        }
        String unitPath = unitCache.get(name);
        if (unitPath != null) {
            return unitPath;
        }
        String unit = getResourceFromFolder("units/", name);
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
        String filePath = getResourceFromFolder("units/", name);
        unitCache.put(name, filePath);
        return filePath;
    }

    @Nullable
    public String getUnitFile(UnitKey unit, boolean eyes) {
        String name = unit.getFileName(eyes);
        String unitPath = unitCache.get(name);
        if (unitPath != null) {
            return unitPath;
        }
        String filePath = getResourceFromFolder("units/", name);
        unitCache.put(name, filePath);
        return filePath;
    }

    @Nullable
    public String getDecalFile(String name) {
        String decalPath = decalCache.get(name);
        if (decalPath != null) {
            return decalPath;
        }
        String unit = getResourceFromFolder("decals/", name);
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
        String unit = getResourceFromFolder("decals/", name);
        spoopyCache.put(name, unit);
        return unit;
    }

    @Nullable
    public String getCCFile(String name) {
        String ccPath = ccCache.get(name);
        if (ccPath != null) {
            return ccPath;
        }
        String cc = getResourceFromFolder("command_token/", name);
        ccCache.put(name, cc);
        return cc;
    }

    @Nullable
    public String getPeekMarkerFile(String name) {
        String markerPath = peekMarkerCache.get(name);
        if (markerPath != null) {
            return markerPath;
        }
        String marker = getResourceFromFolder("peek_marker/", name);
        peekMarkerCache.put(name, marker);
        return marker;
    }

    @Nullable
    public String getAttachmentFile(String name) {
        File attachmentFile = getFile("attachment_token/", name);
        if (attachmentFile.exists()) {
            return attachmentFile.getAbsolutePath();
        }
        return getTokenFile(name);
    }

    @Nullable
    public String getPlanetResource(String name) {
        String planetInfoPath = planetCache.get(name);
        if (planetInfoPath != null) {
            return planetInfoPath;
        }
        String token = getResourceFromFolder("planet_cards/", name);
        planetCache.put(name, token);
        return token;
    }

    @Nullable
    public String getPAResource(String name) {
        return getResourceFromFolder("player_area/", name);
    }

    @Nullable
    public String getTokenFile(String name) {
        return getResourceFromFolder("tokens/", name);
    }

    @Nullable
    public String getExtraFile(String name) {
        return getResourceFromFolder("extra/", name);
    }

    @Nullable
    public static String getResourceFromFolder(String folder, String name) {
        File resourceFile = getFile(folder, name);
        if (resourceFile.exists()) {
            return resourceFile.getAbsolutePath();
        }
        //BotLogger.log("Could not find resource file: " + resourceFile.getAbsolutePath());
        return null;
    }

    public static File getFile(String folder, String name) {
        return new File(Storage.getResourcePath() + File.separator + folder + name);
    }

    @Nullable
    public String getDataFile(String name) {
        return getResourceFromFolder("data/", name);
    }

    public String getDataFolder(String name) {
        return Storage.getResourcePath() + File.separator + "data" + File.separator + name;
    }

    @Nullable
    public String getWebFile(String name) {
        return getResourceFromFolder("web/", name);
    }

    @Nullable
    public String getAliasFile(String name) {
        return getResourceFromFolder("alias/", name);
    }

    @Nullable
    public String getHelpFile(String name) {
        return getResourceFromFolder("help/", name);
    }

}
