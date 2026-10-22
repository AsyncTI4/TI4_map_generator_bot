package ti4;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.io.File;
import java.util.Optional;
import javax.annotation.Nullable;
import ti4.cache.CacheManager;
import ti4.helpers.Storage;

public final class ResourceHelper {

    private static final int MAX_REMEMBERED_PATHS = 10_000;
    private static final Cache<String, Optional<String>> RESOLVED_PATHS = Caffeine.newBuilder()
            .maximumSize(MAX_REMEMBERED_PATHS)
            .recordStats()
            .build();

    static {
        CacheManager.registerCache("resourcePathCache", RESOLVED_PATHS);
    }

    private ResourceHelper() {}

    @Nullable
    public static String getPositionFile(String name) {
        return getResourceFromFolder("positions/", name);
    }

    @Nullable
    public static String getResourceFromFolder(String folder, String name) {
        File resourceFile = getFile(folder, name);
        return RESOLVED_PATHS
                .get(resourceFile.getPath(), _ -> existingPath(resourceFile))
                .orElse(null);
    }

    private static Optional<String> existingPath(File file) {
        return file.exists() ? Optional.of(file.getAbsolutePath()) : Optional.empty();
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
