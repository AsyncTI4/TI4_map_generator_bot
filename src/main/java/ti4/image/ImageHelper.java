package ti4.image;

import static org.apache.commons.lang3.StringUtils.isBlank;

import com.luciad.imageio.webp.CompressionType;
import com.luciad.imageio.webp.WebPWriteParam;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import javax.annotation.Nullable;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import org.jetbrains.annotations.NotNull;
import ti4.message.logging.BotLogger;
import ti4.service.emoji.TI4Emoji;
import ti4.website.EgressClientManager;

@UtilityClass
public class ImageHelper {

    @Nullable
    public static BufferedImage read(String filePath) {
        if (filePath == null) {
            return null;
        }
        return ImageCache.getOrLoadStaticImage(filePath, k -> readImage(filePath));
    }

    @Nullable
    public static BufferedImage readScaled(String filePath, float percent) {
        if (filePath == null) {
            return null;
        }
        return ImageCache.getOrLoadStaticImage(percent + filePath, k -> {
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
        return ImageCache.getOrLoadStaticImage(width + "x" + height + filePath, k -> {
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
        Emoji em = Emoji.fromFormatted(emoji);
        if (em instanceof CustomEmoji e) return readURLScaled(e.getImageUrl(), size, size);
        return null;
    }

    @Nullable
    public static BufferedImage readEmojiImageScaled(TI4Emoji emoji, int size) {
        return readEmojiImageScaled(emoji.emojiString(), size);
    }

    @Nullable
    public static BufferedImage readURLScaled(String imageURL, int width, int height) {
        if (imageURL == null) {
            return null;
        }
        return ImageCache.getOrLoadExpiringImage(width + "x" + height + imageURL, k -> {
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

    private static BufferedImage square(@NotNull BufferedImage originalImage, int newSize) {
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

    @Nullable
    private static BufferedImage readImageURL(String imageURL) {
        if (isBlank(imageURL)) return null;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(imageURL))
                .timeout(Duration.ofSeconds(5))
                .header("User-Agent", "ti4bot")
                .GET()
                .build();

        try {
            HttpResponse<InputStream> response =
                    EgressClientManager.getHttpClient().send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                BotLogger.error("Failed to read image. URL: " + imageURL + " Status: " + response.statusCode());
                return null;
            }

            try (InputStream inputStream = response.body()) {
                BufferedImage image = ImageIO.read(inputStream);
                if (image == null) {
                    BotLogger.error("ImageIO could not decode stream from: " + imageURL);
                }
                return image;
            }
        } catch (HttpTimeoutException e) {
            BotLogger.error("Timeout fetching image: " + imageURL);
            return null;
        } catch (IOException e) {
            BotLogger.error("Network error fetching image: " + imageURL, e);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    @SneakyThrows
    public static byte[] writeJpg(BufferedImage image) {
        image = image.getColorModel().hasAlpha() ? redrawWithoutAlpha(image) : image;
        return writeImage(image, "jpg");
    }

    @SneakyThrows
    public static byte[] writePng(BufferedImage image) {
        return writeImage(image, "png");
    }

    @SneakyThrows
    private static byte[] writeImage(BufferedImage image, String format) {
        try (var byteArrayOutputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, format, byteArrayOutputStream);
            return byteArrayOutputStream.toByteArray();
        }
    }

    @SneakyThrows
    public static byte[] writeWebp(BufferedImage image) {
        image = image.getColorModel().hasAlpha() ? redrawWithoutAlpha(image) : image;
        ImageWriter writer = null;
        try (var byteArrayOutputStream = new ByteArrayOutputStream();
                var imageOutputStream = ImageIO.createImageOutputStream(byteArrayOutputStream)) {
            writer = ImageIO.getImageWritersByMIMEType("image/webp").next();
            writer.setOutput(imageOutputStream);

            WebPWriteParam writeParam = ((WebPWriteParam) writer.getDefaultWriteParam());
            writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            writeParam.setCompressionType(CompressionType.Lossy);
            writeParam.setUseSharpYUV(false);
            writeParam.setAlphaCompressionAlgorithm(0);

            writer.write(null, new IIOImage(image, null, null), writeParam);
            imageOutputStream.flush();
            return byteArrayOutputStream.toByteArray();
        } finally {
            if (writer != null) writer.dispose();
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
