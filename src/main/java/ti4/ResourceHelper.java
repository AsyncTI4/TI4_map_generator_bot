package ti4;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import ti4.helpers.Constants;
import ti4.helpers.RandomHelper;
import ti4.helpers.Storage;
import ti4.helpers.Units.UnitKey;

public final class ResourceHelper {

    private static final Pattern PATTERN = Pattern.compile(Constants.UNIT_DD);
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
        return getCachedResource(factionCache, "factions/", name);
    }

    @Nullable
    public String getGeneralFile(String name) {
        return getCachedResource(generalCache, "general/", name);
    }

    // @Deprecated
    @Nullable
    public String getUnitFile(String name) {
        if (name.endsWith(Constants.UNIT_DD) && RandomHelper.isOneInX(Constants.EYE_CHANCE)) {
            name = PATTERN.matcher(name).replaceFirst(Constants.UNIT_DD_EYE);
        }
        return getCachedResource(unitCache, "units/", name);
    }

    @Nullable
    public String getUnitFile(UnitKey unit) {
        String name = unit.getFileName();
        return getCachedResource(unitCache, "units/", name);
    }

    @Nullable
    public String getUnitFile(UnitKey unit, boolean eyes) {
        String name = unit.getFileName(eyes);
        return getCachedResource(unitCache, "units/", name);
    }

    @Nullable
    public String getDecalFile(String name) {
        return getCachedResource(decalCache, "decals/", name);
    }

    @Nullable
    public String getSpoopyFile() {
        // overlay_jackolantern_1
        int face = ThreadLocalRandom.current().nextInt(1, 4);
        String name = "overlay_jackolantern_" + face + ".png";
        return getCachedResource(spoopyCache, "decals/", name);
    }

    @Nullable
    public String getCCFile(String name) {
        return getCachedResource(ccCache, "command_token/", name);
    }

    @Nullable
    public String getPeekMarkerFile(String name) {
        return getCachedResource(peekMarkerCache, "peek_marker/", name);
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
        return getCachedResource(planetCache, "planet_cards/", name);
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

    private String getCachedResource(Map<String, String> cache, String folder, String name) {
        if (cache.containsKey(name)) {
            return cache.get(name);
        }
        String resourcePath = getResourceFromFolder(folder, name);
        cache.put(name, resourcePath);
        return resourcePath;
    }

    @Nullable
    public static String getResourceFromFolder(String folder, String name) {
        File resourceFile = getFile(folder, name);
        if (resourceFile.exists()) {
            return resourceFile.getAbsolutePath();
        }
        // BotLogger.log("Could not find resource file: " + resourceFile.getAbsolutePath());
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
