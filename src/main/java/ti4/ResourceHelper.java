package ti4;

import java.io.File;
import javax.annotation.Nullable;
import ti4.helpers.Storage;
import ti4.helpers.Units.UnitKey;
import ti4.image.ImageResources;

public final class ResourceHelper {

    private static final ResourceHelper INSTANCE = new ResourceHelper();

    private ResourceHelper() {}

    // TODO: the instance and the image lookups below delegate to ImageResources for PlayerAreaGenerator,
    // TileGenerator and UnitRenderGenerator; remove them after their rewrite branches merge.
    public static ResourceHelper getInstance() {
        return INSTANCE;
    }

    @Nullable
    public static String getTileFile(String name) {
        return ImageResources.tile(name);
    }

    @Nullable
    public String getFactionFile(String name) {
        return ImageResources.faction(name);
    }

    @Nullable
    public String getGeneralFile(String name) {
        return ImageResources.general(name);
    }

    @Nullable
    public String getUnitFile(String name) {
        return ImageResources.unit(name);
    }

    @Nullable
    public String getUnitFile(UnitKey unit) {
        return ImageResources.unit(unit);
    }

    @Nullable
    public String getDecalFile(String name) {
        return ImageResources.decal(name);
    }

    @Nullable
    public String getSpoopyFile() {
        return ImageResources.spoopyFace();
    }

    @Nullable
    public String getPlanetResource(String name) {
        return ImageResources.planetCard(name);
    }

    @Nullable
    public static String getPAResource(String name) {
        return ImageResources.playerArea(name);
    }

    @Nullable
    public String getTokenFile(String name) {
        return ImageResources.token(name);
    }

    @Nullable
    public static String getPositionFile(String name) {
        return getResourceFromFolder("positions/", name);
    }

    @Nullable
    public static String getResourceFromFolder(String folder, String name) {
        File resourceFile = getFile(folder, name);
        if (resourceFile.exists()) {
            return resourceFile.getAbsolutePath();
        }
        return null;
    }

    public static File getFile(String folder, String name) {
        return new File(Storage.getResourcePath() + File.separator + folder + name);
    }

    @Nullable
    public static String getDataFile(String name) {
        return getResourceFromFolder("data/", name);
    }

    public static String getDataFolder(String name) {
        return Storage.getResourcePath() + File.separator + "data" + File.separator + name;
    }

    @Nullable
    public static String getWebFile(String name) {
        return getResourceFromFolder("web/", name);
    }

    @Nullable
    public static String getAliasFile(String name) {
        return getResourceFromFolder("alias/", name);
    }

    @Nullable
    public static String getHelpFile(String name) {
        return getResourceFromFolder("help/", name);
    }
}
