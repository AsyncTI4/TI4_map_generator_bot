package ti4.image;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.Emoji;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ti4.message.BotLogger;
import ti4.service.emoji.TI4Emoji;

@UtilityClass
public class ImageHelper {

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
    public static BufferedImage readEmojiImageScaled(TI4Emoji emoji, int size) {
        if (emoji.asEmoji() != null && emoji.asEmoji() instanceof CustomEmoji e)
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

    public static BufferedImage square(@NotNull BufferedImage originalImage) {
        int newSize = Math.max(originalImage.getWidth(), originalImage.getHeight());
        return square(originalImage, newSize);
    }

    public static BufferedImage square(@NotNull BufferedImage originalImage, int newSize) {
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
            BotLogger.error("Failed to read image '" + filePath + "': ", e);
        }
        return null;
    }

    private static BufferedImage readImage(InputStream inputStream) {
        try {
            return ImageIO.read(inputStream);
        } catch (IOException e) {
            BotLogger.error("Failed to read image: ", e);
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
            BotLogger.error("Failed to read image URL'" + imageURL + "': ", e);
        }
        return null;
    }

    @SneakyThrows
    public static byte[] writeJpg(BufferedImage image) {
        var imageWithoutAlpha = image.getColorModel().hasAlpha() ? redrawWithoutAlpha(image) : image;
        try (var byteArrayOutputStream = new ByteArrayOutputStream()) {
            ImageIO.write(imageWithoutAlpha, "jpg", byteArrayOutputStream);
            return byteArrayOutputStream.toByteArray();
        }
    }

    @SneakyThrows
    public static byte[] writePng(BufferedImage image) {
        try (var byteArrayOutputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", byteArrayOutputStream);
            return byteArrayOutputStream.toByteArray();
        }
    }

    private static BufferedImage redrawWithoutAlpha(BufferedImage image) {
        var imageWithoutAlpha = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = imageWithoutAlpha.createGraphics();
        g2d.drawImage(image, 0, 0, Color.BLACK, null);
        g2d.dispose();
        return imageWithoutAlpha;
    }
}
