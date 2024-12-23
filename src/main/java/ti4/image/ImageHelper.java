package ti4.image;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Arrays;

import com.sksamuel.scrimage.ImmutableImage;
import com.sksamuel.scrimage.nio.JpegWriter;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import org.jetbrains.annotations.Nullable;
import ti4.message.BotLogger;

@UtilityClass
public class ImageHelper {

    private static final JpegWriter JPG_WRITER = JpegWriter.Default;
    private static final Color transparencyReplacementColor;
    static {
        float[] hsb = Color.RGBtoHSB(34, 34, 34, new float[3]);
        transparencyReplacementColor = Color.getHSBColor(hsb[0], hsb[1], hsb[2]);
    }

    @Nullable
    public static BufferedImage read(String filePath) {
        if (filePath == null) {
            return null;
        }
        return ImageCache.getInstance().getOrLoadStaticImage(filePath, k -> readImage(filePath));
    }

    @Nullable
    public static BufferedImage readScaled(String filePath, float percent) {
        if (filePath == null) {
            return null;
        }
        return ImageCache.getInstance().getOrLoadStaticImage(percent + filePath, k -> {
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
        return ImageCache.getInstance().getOrLoadStaticImage(width + "x" + height + filePath, k -> {
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
    public static BufferedImage readURLScaled(String imageURL, int width, int height) {
        if (imageURL == null) {
            return null;
        }
        return ImageCache.getInstance().getOrLoadExpiringImage(width + "x" + height + imageURL, k -> {
            BufferedImage image = readImageURL(imageURL);
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

    private static BufferedImage readImage(String filePath) {
        try {
            return ImageIO.read(new File(filePath));
        } catch (IOException e) {
            BotLogger.log("Failed to read image '" + filePath + "': " + Arrays.toString(e.getStackTrace()));
        }
        return null;
    }

    private static BufferedImage readImage(InputStream inputStream) {
        try {
            return ImageIO.read(inputStream);
        } catch (IOException e) {
            BotLogger.log("Failed to read image: " + Arrays.toString(e.getStackTrace()));
        }
        return null;
    }

    @Nullable
    private static BufferedImage readImageURL(String imageURL) {
        if (imageURL == null) {
            return null;
        }
        try (InputStream inputStream = URI.create(imageURL).toURL().openStream()) {
            return readImage(inputStream);
        } catch (IOException e) {
            BotLogger.log("Failed to read image URL'" + imageURL + "': " + Arrays.toString(e.getStackTrace()));
        }
        return null;
    }

    @SneakyThrows
    public static byte[] writeJpg(BufferedImage image) {
        var immutableImage = ImmutableImage.fromAwt(image);
        if (immutableImage.hasAlpha()) {
            immutableImage = immutableImage.removeTransparency(transparencyReplacementColor);
        }
        return immutableImage.bytes(JPG_WRITER);
    }
}
