package ti4.spring.api.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import ti4.website.EgressClientManager;

@Component
public class RestDiscordClient {
    private static final String DISCORD_TOKEN_URL = "https://discord.com/api/oauth2/token";
    private static final String DISCORD_USER_INFO_URL = "https://discord.com/api/v10/users/@me";
    private static final String DISCORD_CLIENT_ID = System.getenv("DISCORD_CLIENT_ID");
    private static final String DISCORD_CLIENT_SECRET = System.getenv("DISCORD_CLIENT_SECRET");

    private final ObjectMapper objectMapper = EgressClientManager.getObjectMapper();
    private final HttpClient httpClient = EgressClientManager.getHttpClient();

    public DiscordUserInfo getUserInfo(String bearerToken) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(DISCORD_USER_INFO_URL))
                .header("Authorization", "Bearer " + bearerToken)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == HttpStatus.OK.value()) {
            return objectMapper.readValue(response.body(), DiscordUserInfo.class);
        }

        if (response.statusCode() == HttpStatus.UNAUTHORIZED.value()
                || response.statusCode() == HttpStatus.FORBIDDEN.value()) {
            return null;
        }

        throw new RuntimeException(
                "Unexpected status from Discord: " + response.statusCode() + " - " + response.body());
    }

    DiscordTokenResponse exchangeCodeForToken(String code, String redirectUri)
            throws IOException, InterruptedException {
        Map<String, String> formData = Map.of(
                "client_id", DISCORD_CLIENT_ID,
                "client_secret", DISCORD_CLIENT_SECRET,
                "grant_type", "authorization_code",
                "code", code,
                "redirect_uri", redirectUri);

        return postTokenRequest(formData);
    }

    DiscordTokenResponse refreshAccessToken(String refreshToken) throws IOException, InterruptedException {
        Map<String, String> formData = Map.of(
                "client_id", DISCORD_CLIENT_ID,
                "client_secret", DISCORD_CLIENT_SECRET,
                "grant_type", "refresh_token",
                "refresh_token", refreshToken);

        return postTokenRequest(formData);
    }

    private DiscordTokenResponse postTokenRequest(Map<String, String> formData)
            throws IOException, InterruptedException {
        String formBody = formData.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                        + "="
                        + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(DISCORD_TOKEN_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != HttpStatus.OK.value()) {
            throw new RuntimeException(
                    "Discord token request failed: " + response.statusCode() + " - " + response.body());
        }

        return objectMapper.readValue(response.body(), DiscordTokenResponse.class);
    }
}
