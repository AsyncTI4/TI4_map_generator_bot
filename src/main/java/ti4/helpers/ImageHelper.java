package ti4.helpers;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.jetbrains.annotations.Nullable;
import javax.imageio.ImageIO;
import ti4.message.BotLogger;

public class ImageHelper {

  private static final Cache<String, BufferedImage> imageCache = Caffeine.newBuilder()
      .maximumSize(GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.IMAGE_CACHE_MAX_SIZE.toString(), Integer.class, 1000))
      .expireAfterAccess(24, TimeUnit.HOURS)
      .recordStats() //for tuning cache size - comment out when done
      .build();

  private ImageHelper() {}

  @Nullable
  public static BufferedImage read(String filePath) {
    if (filePath == null) return null;
    return getOrLoad(filePath, k -> readImage(filePath));
  }

  @Nullable
  public static BufferedImage readURL(String imageURL) {
    if (imageURL == null) return null;
    return getOrLoad(imageURL, k -> readImageURL(imageURL));
  }

  @Nullable
  public static BufferedImage readScaled(String filePath, float percent) {
    if (filePath == null) return null;
    return getOrLoad(percent + filePath, k -> {
      BufferedImage image = readImage(filePath);
      if (image == null) {
        return null;
      }
      return scale(image, percent);
    });
  }

  @Nullable
  public static BufferedImage readScaled(String filePath, int width, int height) {
    if (filePath == null) return null;
    return getOrLoad(width + "x" + height + filePath, k -> {
      BufferedImage image = readImage(filePath);
      if (image == null) {
        return null;
      }
      return scale(image, width, height);
    });
  }

  @Nullable
  public static BufferedImage readURLScaled(String imageURL, int width, int height) {
    if (imageURL == null) return null;
    return getOrLoad(width + "x" + height + imageURL, k -> {
      BufferedImage image = readURL(imageURL);
      if (image == null) {
        return null;
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

  private static BufferedImage getOrLoad(String key, Function<String, BufferedImage> loader) {
    try {
      return imageCache.get(key, loader);
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
      BotLogger.log("Failed to read image: " + Arrays.toString(e.getStackTrace()));
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
      BotLogger.log("Failed to read image URL: " + Arrays.toString(e.getStackTrace()));
    }
    return null;
  }

  public static String getCacheStats() {
    return imageCache.stats() + "\n" + "CacheSize: " + imageCache.estimatedSize();
  }
}
