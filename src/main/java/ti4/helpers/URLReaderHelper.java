package ti4.helpers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import ti4.message.MessageHelper;

public class URLReaderHelper {

    private static final int MAX_FILE_SIZE_BYTES = 500000; // 500 kB

    public static String readFromURL(String url, MessageChannel feedbackChannel) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                MessageHelper.sendMessageToChannel(feedbackChannel, "HTTP error: " + response.statusCode());
                return null;
            }

            byte[] body = response.body();
            if (body.length > MAX_FILE_SIZE_BYTES) {
                MessageHelper.sendMessageToChannel(
                        feedbackChannel, "File exceeds max allowed size (" + MAX_FILE_SIZE_BYTES + " bytes)");
                return null;
            }

            return new String(body, StandardCharsets.UTF_8);
        } catch (Exception e) {
            MessageHelper.sendMessageToChannel(feedbackChannel, "Error fetching URL: " + e.getMessage());
            return null;
        }
    }
}
