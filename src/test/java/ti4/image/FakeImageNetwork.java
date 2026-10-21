package ti4.image;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import javax.imageio.ImageIO;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import ti4.website.EgressClientManager;

/**
 * Replaces the bot's outbound HTTP client for image fetches (custom faction emoji, Discord emoji, avatars) in golden
 * tests. Mocking at the HTTP client keeps the fake below every image-loading class, so moving or renaming those
 * classes cannot silently route a render back onto the real network.
 *
 * <p>{@link #notFound()} answers every request with 404, which the loaders turn into a missing image: the same result
 * the map harness used to get by stubbing ImageHelper.readURLScaled to null. {@link #deterministicImages()} answers
 * with a small PNG whose colours derive from the URL, so pictures that draw emoji are pinned by position and size.
 */
public final class FakeImageNetwork implements AutoCloseable {

    private final MockedStatic<EgressClientManager> egress;

    private FakeImageNetwork(boolean serveImages) {
        HttpClient client = mock(HttpClient.class);
        try {
            when(client.send(any(HttpRequest.class), any())).thenAnswer(invocation -> {
                HttpRequest request = invocation.getArgument(0);
                return response(
                        serveImages ? 200 : 404, serveImages ? png(request.uri().toString()) : new byte[0]);
            });
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
        egress = mockStatic(EgressClientManager.class, Mockito.CALLS_REAL_METHODS);
        egress.when(EgressClientManager::getHttpClient).thenReturn(client);
    }

    public static FakeImageNetwork notFound() {
        return new FakeImageNetwork(false);
    }

    public static FakeImageNetwork deterministicImages() {
        return new FakeImageNetwork(true);
    }

    @Override
    public void close() {
        egress.close();
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<byte[]> response(int status, byte[] body) {
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of(), (_, _) -> true));
        return response;
    }

    private static byte[] png(String url) {
        int hash = url.hashCode();
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        g2.setColor(new Color(hash | 0xFF000000, true));
        g2.fillOval(0, 0, 64, 64);
        g2.setColor(new Color(Integer.reverse(hash) | 0xFF000000, true));
        g2.fillRect(16, 16, 32, 32);
        g2.dispose();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
