package ti4.helpers;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import ti4.message.BotLogger;

public class ImageHelper {

  private static final Cache<String, BufferedImage> imageCache = CacheBuilder.newBuilder()
      .maximumSize(GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.IMAGE_CACHE_MAX_SIZE.toString(), Integer.class, 1000))
      .expireAfterAccess(24, TimeUnit.HOURS)
      .build();

  private ImageHelper() {}

  @Nullable
  public static BufferedImage read(String filePath) {
    if (filePath == null) return null;
    return getOrLoad(filePath, () -> readImage(filePath));
  }

  @Nullable
  public static Image readScaled(String key, InputStream inputStream, int width, int height) {
    return getOrLoad(key, () -> {
      BufferedImage image = readImage(inputStream);
      if (image == null) {
        return null;
      }
      BufferedImage newImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
      newImage.getGraphics().drawImage(image, 0, 0, width, height, null);
      return newImage;
    });
  }

  @Nullable
  public static BufferedImage readScaled(String filePath, float percent) {
    if (filePath == null) return null;
    return getOrLoad(percent + filePath, () -> {
      BufferedImage image = readImage(filePath);
      if (image == null) {
        return null;
      }
      return scale(image, percent);
    });
  }

  public static BufferedImage scale(BufferedImage originalImage, float percent) {
    int scaledWidth = (int) (originalImage.getWidth() * percent);
    int scaledHeight = (int) (originalImage.getHeight() * percent);
    Image resultingImage = originalImage.getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_FAST);
    BufferedImage outputImage = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_ARGB);
    outputImage.getGraphics().drawImage(resultingImage, 0, 0, null);
    return outputImage;
  }

  public static BufferedImage scale(BufferedImage originalImage, int scaledWidth, int scaledHeight) {
    Image resultingImage = originalImage.getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_FAST);
    BufferedImage outputImage = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_ARGB);
    outputImage.getGraphics().drawImage(resultingImage, 0, 0, null);
    return outputImage;
  }

  private static BufferedImage getOrLoad(String key, Callable<BufferedImage> loader) {
    try {
      return imageCache.get(key, loader);
    } catch (ExecutionException e) {
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

  public static BufferedImage readImageURL(String imageURL) {
    try {
      URL url = new URL(imageURL);
      InputStream inputStream = url.openStream();
      return readImage(inputStream);
    } catch (IOException e) {
      BotLogger.log("Failed to read image URL:" + Arrays.toString(e.getStackTrace()));
    }
    return null;
  }
}
