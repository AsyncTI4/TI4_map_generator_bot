package ti4.image.io;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import ti4.discord.JdaService;
import ti4.logging.BotLogger;
import ti4.service.emoji.TI4Emoji;
import ti4.website.EgressClientManager;

public final class RemoteImages {

    public static final int DISCORD_AVATAR_SIZE = 32;
    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(2);

    private RemoteImages() {}

    @Nullable
    public static BufferedImage readScaled(@Nullable String url, int width, int height) {
        if (url == null) {
            return null;
        }
        return ImageCache.getOrLoadExpiringImage(width + "x" + height + url, _ -> {
            BufferedImage image = fetch(url);
            if (image == null) {
                return null;
            }
            if (width == height) {
                image = ImageHelper.square(image);
            }
            return ImageHelper.scale(image, width, height);
        });
    }

    @Nullable
    public static BufferedImage emoji(String formattedEmoji, int size) {
        if (Emoji.fromFormatted(formattedEmoji) instanceof CustomEmoji custom) {
            return readScaled(custom.getImageUrl(), size, size);
        }
        return null;
    }

    @Nullable
    public static BufferedImage emoji(TI4Emoji emoji, int size) {
        return emoji(emoji.emojiString(), size);
    }

    public static void drawEmoji(Graphics2D g2, TI4Emoji emoji, int x, int y, int size) {
        g2.drawImage(emoji(emoji, size), x, y, null);
    }

    @Nullable
    public static Image discordAvatar(String userId) {
        try {
            User user = JdaService.jda.getUserById(userId);
            if (user == null) return null;
            return readScaled(user.getEffectiveAvatar().getUrl(), DISCORD_AVATAR_SIZE, DISCORD_AVATAR_SIZE);
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private static BufferedImage fetch(String url) {
        if (isBlank(url)) return null;
        String stillImageUrl = url.replace("animated=true", "animated=false");
        URI uri;
        try {
            uri = URI.create(stillImageUrl);
        } catch (Exception e) {
            BotLogger.error("Invalid image URL: " + stillImageUrl, e);
            return null;
        }
        HttpRequest request =
                HttpRequest.newBuilder().uri(uri).timeout(FETCH_TIMEOUT).GET().build();
        try {
            HttpResponse<byte[]> response = sendRetryingOnceAfterNetworkError(request);
            if (response.statusCode() != 200) {
                BotLogger.error("Failed to read image. URL: " + stillImageUrl + " Status: " + response.statusCode());
                return null;
            }
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(response.body()));
            if (image == null) {
                String contentType =
                        response.headers().firstValue("Content-Type").orElse("no content type");
                BotLogger.error("ImageIO could not decode " + response.body().length + " bytes (" + contentType
                        + ") from: " + stillImageUrl);
            }
            return image;
        } catch (HttpTimeoutException e) {
            BotLogger.spammyError("Timeout fetching image: " + stillImageUrl);
        } catch (IOException e) {
            BotLogger.spammyError("Network error fetching image: " + stillImageUrl + " (" + e.getMessage() + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return null;
    }

    private static HttpResponse<byte[]> sendRetryingOnceAfterNetworkError(HttpRequest request)
            throws IOException, InterruptedException {
        try {
            return send(request);
        } catch (HttpTimeoutException e) {
            throw e;
        } catch (IOException e) {
            return send(request);
        }
    }

    private static HttpResponse<byte[]> send(HttpRequest request) throws IOException, InterruptedException {
        return EgressClientManager.getHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
    }
}
