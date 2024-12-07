package ti4.cache;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.experimental.UtilityClass;

@UtilityClass
public class CacheManager {

    private static final Map<String, Cache<?, ?>> cacheNameToCache = new ConcurrentHashMap<>();

    public static void registerCache(String name, Cache<?, ?> cache) {
        cacheNameToCache.put(name, cache);
    }

    public static Set<Map.Entry<String, Cache<?, ?>>> getNamesToCaches() {
        return cacheNameToCache.entrySet();
    }
}
