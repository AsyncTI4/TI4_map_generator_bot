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
import org.springframework.stereotype.Component;
import ti4.website.EgressClientManager;

/**
 * Low-level REST client for Discord API calls.
 * Handles HTTP communication and JSON deserialization.
 */
@Component
public class RestDiscordClient {
    private static final String DISCORD_TOKEN_URL = "https://discord.com/api/oauth2/token";
    private static final String DISCORD_USER_INFO_URL = "https://discord.com/api/v10/users/@me";
    private static final String DISCORD_CLIENT_ID = System.getenv("DISCORD_CLIENT_ID");
    private static final String DISCORD_CLIENT_SECRET = System.getenv("DISCORD_CLIENT_SECRET");

    private final ObjectMapper objectMapper = EgressClientManager.getObjectMapper();
    private final HttpClient httpClient = EgressClientManager.getHttpClient();

    /**
     * Fetch Discord user information using a bearer token.
     * Returns null if authentication fails (401/403), throws IOException for other errors.
     *
     * @param bearerToken OAuth2 access token
     * @return User info response, or null if unauthorized
     * @throws IOException If network or deserialization fails
     * @throws InterruptedException If HTTP request is interrupted
     */
    public DiscordUserInfo getUserInfo(String bearerToken) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(DISCORD_USER_INFO_URL))
                .header("Authorization", "Bearer " + bearerToken)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(), DiscordUserInfo.class);
        }

        if (response.statusCode() == 401 || response.statusCode() == 403) {
            return null; // Authentication failed
        }

        throw new IOException("Unexpected status from Discord: " + response.statusCode() + " - " + response.body());
    }

    /**
     * Exchange authorization code for access token.
     *
     * @param code Authorization code from OAuth2 flow
     * @param redirectUri Redirect URI used in authorization
     * @return Token response with access token and refresh token
     * @throws IOException If network or deserialization fails
     * @throws InterruptedException If HTTP request is interrupted
     */
    public DiscordTokenResponse exchangeCodeForToken(String code, String redirectUri)
            throws IOException, InterruptedException {
        Map<String, String> formData = Map.of(
                "client_id", DISCORD_CLIENT_ID,
                "client_secret", DISCORD_CLIENT_SECRET,
                "grant_type", "authorization_code",
                "code", code,
                "redirect_uri", redirectUri);

        return postTokenRequest(formData);
    }

    /**
     * Refresh access token using refresh token.
     *
     * @param refreshToken Refresh token from previous token response
     * @return New token response with refreshed access token
     * @throws IOException If network or deserialization fails
     * @throws InterruptedException If HTTP request is interrupted
     */
    public DiscordTokenResponse refreshAccessToken(String refreshToken) throws IOException, InterruptedException {
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

        if (response.statusCode() != 200) {
            throw new IOException("Discord token request failed: " + response.statusCode() + " - " + response.body());
        }

        return objectMapper.readValue(response.body(), DiscordTokenResponse.class);
    }
}
