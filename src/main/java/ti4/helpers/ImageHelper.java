package ti4.helpers;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;

import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.Emoji;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import javax.imageio.ImageIO;
import org.jetbrains.annotations.Nullable;
import ti4.AsyncTI4DiscordBot;
import ti4.generator.MapGenerator;
import ti4.message.BotLogger;

public class ImageHelper {

    private static final int FILE_IMAGE_CACHE_SIZE = GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.FILE_IMAGE_CACHE_MAX_SIZE.toString(), Integer.class, 2000);
    private static final int FILE_IMAGE_CACHE_EXPIRE_TIME_MINUTES = GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.FILE_IMAGE_CACHE_EXPIRE_TIME_MINUTES.toString(), Integer.class, 60 * 8);
    private static final Cache<String, BufferedImage> fileImageCache = Caffeine.newBuilder()
        .maximumSize(FILE_IMAGE_CACHE_SIZE)
        .expireAfterAccess(FILE_IMAGE_CACHE_EXPIRE_TIME_MINUTES, TimeUnit.MINUTES)
        .recordStats()
        .build();

    private static final int URL_IMAGE_CACHE_SIZE = GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.URL_IMAGE_CACHE_MAX_SIZE.toString(), Integer.class, 2000);
    private static final int URL_IMAGE_CACHE_EXPIRE_TIME_MINUTES = GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.URL_IMAGE_CACHE_EXPIRE_TIME_MINUTES.toString(), Integer.class, 60 * 8);
    private static final Cache<String, BufferedImage> urlImageCache = Caffeine.newBuilder()
        .maximumSize(URL_IMAGE_CACHE_SIZE)
        .expireAfterWrite(URL_IMAGE_CACHE_EXPIRE_TIME_MINUTES, TimeUnit.MINUTES)
        .recordStats()
        .build();

    private static final int LOG_CACHE_STATS_INTERVAL_MINUTES = GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.LOG_CACHE_STATS_INTERVAL_MINUTES.toString(), Integer.class, 60 * 4);
    private static final AtomicReference<Instant> logStatsScheduledTime = new AtomicReference<>();
    private static final ThreadLocal<DecimalFormat> percentFormatter = ThreadLocal.withInitial(() -> new DecimalFormat("##.##%"));

    private ImageHelper() {
    }

    public static void resetCache() {
        fileImageCache.invalidateAll();
        urlImageCache.invalidateAll();
    }

    @Nullable
    public static BufferedImage read(String filePath) {
        if (filePath == null) {
            return null;
        }
        return getOrLoadStaticImage(filePath, k -> readImage(filePath));
    }

    @Nullable
    public static BufferedImage readURL(String imageURL) {
        if (imageURL == null) {
            return null;
        }
        return getOrLoadExpiringImage(imageURL, k -> readImageURL(imageURL));
    }

    @Nullable
    public static BufferedImage readScaled(String filePath, float percent) {
        if (filePath == null) {
            return null;
        }
        return getOrLoadStaticImage(percent + filePath, k -> {
            BufferedImage image = readImage(filePath);
            if (image == null) {
                return null;
            }
            return scale(image, percent);
        });
    }

    @Nullable
    public static BufferedImage readScaled(String filePath, int width, int height) {
        if (filePath == null) {
            return null;
        }
        return getOrLoadStaticImage(width + "x" + height + filePath, k -> {
            BufferedImage image = readImage(filePath);
            if (image == null) {
                return null;
            }
            if (width == height) {
                image = square(image);
            }
            return scale(image, width, height);
        });
    }

    @Nullable
    public static BufferedImage readEmojiImageScaled(String emoji, int size) {
        if (Emoji.fromFormatted(emoji) instanceof CustomEmoji e)
            return ImageHelper.readURLScaled(e.getImageUrl(), size, size);
        return null;
    }

    @Nullable
    public static BufferedImage readUnicodeScaled(String unicode, int width, int height) {
        if (unicode == null) {
            return null;
        }
        return getOrLoadExpiringImage(width + "x" + height + unicode, k -> {
            BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();
            g2.setFont(Storage.getEmojiFont());
            BasicStroke stroke4 = new BasicStroke(4.0f);
            MapGenerator.superDrawString(g2, unicode, 20, 60, Color.white, null, null, stroke4, Color.black);
            GlyphVector gv = g2.getFont().createGlyphVector(g2.getFontRenderContext(), unicode);
            Rectangle rect = gv.getGlyphPixelBounds(0, g2.getFontRenderContext(), 20, 60);
            int pad = 5;
            BufferedImage img2 = img.getSubimage(rect.x - pad, rect.y - pad, rect.width + pad * 2, rect.height + pad * 2);
            return ImageHelper.scale(ImageHelper.square(img2), width, height);
        });
    }

    @Nullable
    public static BufferedImage readURLScaled(String imageURL, int width, int height) {
        if (imageURL == null) {
            return null;
        }
        return getOrLoadExpiringImage(width + "x" + height + imageURL, k -> {
            BufferedImage image = readURL(imageURL);
            if (image == null) {
                return null;
            }
            if (width == height) {
                image = square(image);
            }
            return scale(image, width, height);
        });
    }

    public static BufferedImage scale(BufferedImage originalImage, float percent) {
        int scaledWidth = (int) (originalImage.getWidth() * percent);
        int scaledHeight = (int) (originalImage.getHeight() * percent);
        return scale(originalImage, scaledWidth, scaledHeight);
    }

    public static BufferedImage scale(BufferedImage originalImage, int scaledWidth, int scaledHeight) {
        Image resultingImage = originalImage.getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_SMOOTH);
        BufferedImage outputImage = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_ARGB);
        outputImage.getGraphics().drawImage(resultingImage, 0, 0, null);
        return outputImage;
    }

    public static BufferedImage square(BufferedImage originalImage) {
        int newSize = Math.max(originalImage.getWidth(), originalImage.getHeight());
        BufferedImage outputImage = new BufferedImage(newSize, newSize, BufferedImage.TYPE_INT_ARGB);
        int newX = (newSize - originalImage.getWidth()) / 2;
        int newY = (newSize - originalImage.getHeight()) / 2;
        outputImage.getGraphics().drawImage(originalImage, newX, newY, null);
        return outputImage;
    }

    public static BufferedImage createOrLoadCalculatedImage(String key, Function<String, BufferedImage> loader) {
        try {
            return fileImageCache.get(key, loader);
        } catch (Exception e) {
            BotLogger.log("Unable to load from image cache.", e);
        }
        return null;
    }

    private static BufferedImage getOrLoadStaticImage(String key, Function<String, BufferedImage> loader) {
        try {
            return fileImageCache.get(key, loader);
        } catch (Exception e) {
            BotLogger.log("Unable to load from image cache.", e);
        }
        return null;
    }

    private static BufferedImage getOrLoadExpiringImage(String key, Function<String, BufferedImage> loader) {
        try {
            return urlImageCache.get(key, loader);
        } catch (Exception e) {
            BotLogger.log("Unable to load from image cache.", e);
        }
        return null;
    }

    private static BufferedImage readImage(String filePath) {
        ImageIO.setUseCache(false);
        try {
            return ImageIO.read(new File(filePath));
        } catch (IOException e) {
            BotLogger.log("Failed to read image '" + filePath + "': " + Arrays.toString(e.getStackTrace()));
        }
        return null;
    }

    private static BufferedImage readImage(InputStream inputStream) {
        ImageIO.setUseCache(false);
        try {
            return ImageIO.read(inputStream);
        } catch (IOException e) {
            BotLogger.log("Failed to read image: " + Arrays.toString(e.getStackTrace()));
        }
        return null;
    }

    private static BufferedImage readImageURL(String imageURL) {
        ImageIO.setUseCache(false);
        try (InputStream inputStream = URI.create(imageURL).toURL().openStream()) {
            return readImage(inputStream);
        } catch (IOException e) {
            BotLogger.log("Failed to read image URL'" + imageURL + "': " + Arrays.toString(e.getStackTrace()));
        }
        return null;
    }

    public static Optional<String> getCacheStats() {
        Instant oldValue = logStatsScheduledTime.getAndUpdate(scheduledLogTime -> {
            Instant now = Instant.now();
            if (scheduledLogTime == null || scheduledLogTime.isBefore(now)) {
                return now.plus(LOG_CACHE_STATS_INTERVAL_MINUTES, ChronoUnit.MINUTES);
            }
            return scheduledLogTime;
        });
        if (logStatsScheduledTime.get().equals(oldValue)) {
            return Optional.empty();
        }
        return Optional.of(cacheStatsToString("fileImageCache", fileImageCache) + "\n\n " + cacheStatsToString("urlImageCache", urlImageCache));
    }

    private static String cacheStatsToString(String name, Cache<String, BufferedImage> cache) {
        CacheStats stats = cache.stats();
        return ToStringHelper.of(name)
            .add("liveTime", getLiveTime())
            .add("hitCount", stats.hitCount())
            .add("hitRate", formatPercent(stats.hitRate()))
            .add("loadCount", stats.loadCount())
            .add("loadFailureCount", stats.loadFailureCount())
            .add("averageLoadPenaltyMilliseconds", TimeUnit.MILLISECONDS.convert((long) stats.averageLoadPenalty(), TimeUnit.NANOSECONDS))
            .add("evictionCount", stats.evictionCount())
            .add("currentSize", cache.estimatedSize())
            .toString();
    }

    private static String getLiveTime() {
        long millisecondsSinceBotStarted = System.currentTimeMillis() - AsyncTI4DiscordBot.START_TIME_MILLISECONDS;
        long liveTimeHours = TimeUnit.HOURS.convert(millisecondsSinceBotStarted, TimeUnit.MILLISECONDS);
        long liveTimeMinutes = TimeUnit.MINUTES.convert(millisecondsSinceBotStarted, TimeUnit.MILLISECONDS) - liveTimeHours * 60;
        return liveTimeHours + "h" + liveTimeMinutes + "m";
    }

    private static String formatPercent(double d) {
        return percentFormatter.get().format(d);
    }
}
