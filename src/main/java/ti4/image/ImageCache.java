package ti4.image;

import java.awt.image.BufferedImage;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import ti4.cache.CacheManager;
import ti4.message.BotLogger;
import ti4.settings.GlobalSettings;

public class ImageCache {

    private static final int FILE_IMAGE_CACHE_SIZE = GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.FILE_IMAGE_CACHE_MAX_SIZE.toString(), Integer.class, 2000);
    private static final int FILE_IMAGE_CACHE_EXPIRE_TIME_MINUTES = GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.FILE_IMAGE_CACHE_EXPIRE_TIME_MINUTES.toString(), Integer.class, 60 * 8);
    private final Cache<String, BufferedImage> fileImageCache;

    private static final int URL_IMAGE_CACHE_SIZE = GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.URL_IMAGE_CACHE_MAX_SIZE.toString(), Integer.class, 2000);
    private static final int URL_IMAGE_CACHE_EXPIRE_TIME_MINUTES = GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.URL_IMAGE_CACHE_EXPIRE_TIME_MINUTES.toString(), Integer.class, 60 * 8);
    private final Cache<String, BufferedImage> urlImageCache;

    private static volatile ImageCache instance;

    private ImageCache() {
        fileImageCache = Caffeine.newBuilder()
                .maximumSize(FILE_IMAGE_CACHE_SIZE)
                .expireAfterAccess(FILE_IMAGE_CACHE_EXPIRE_TIME_MINUTES, TimeUnit.MINUTES)
                .recordStats()
                .build();
        urlImageCache = Caffeine.newBuilder()
                .maximumSize(URL_IMAGE_CACHE_SIZE)
                .expireAfterWrite(URL_IMAGE_CACHE_EXPIRE_TIME_MINUTES, TimeUnit.MINUTES)
                .recordStats()
                .build();
        CacheManager.registerCache("fileImageCache", fileImageCache);
        CacheManager.registerCache("urlImageCache", urlImageCache);
    }

    public static ImageCache getInstance() {
        if (instance == null) {
            synchronized (ImageCache.class) {
                if (instance == null) {
                    instance = new ImageCache();
                }
            }
        }
        return instance;
    }

    public BufferedImage getOrLoadStaticImage(String key, Function<String, BufferedImage> loader) {
        try {
            return fileImageCache.get(key, loader);
        } catch (Exception e) {
            BotLogger.error("Unable to load from image cache.", e);
        }
        return null;
    }

    public BufferedImage getOrLoadExpiringImage(String key, Function<String, BufferedImage> loader) {
        try {
            return urlImageCache.get(key, loader);
        } catch (Exception e) {
            BotLogger.error("Unable to load from image cache.", e);
        }
        return null;
    }
}
