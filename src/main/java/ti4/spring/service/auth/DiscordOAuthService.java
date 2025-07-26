package ti4.spring.service.auth;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import ti4.message.BotLogger;
import ti4.spring.exception.ForbiddenException;
import ti4.spring.exception.UnauthorizedException;

@Service
public class DiscordOAuthService {

    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String ME_ENDPOINT = "https://discord.com/api/users/@me";

    public void authorize(String authorizationHeader, String userId) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException();
        }

        String token = authorizationHeader.substring("Bearer ".length()).trim();
        String tokenUserId = DiscordOAuthService.getUserId(token);
        if (tokenUserId == null) {
            throw new UnauthorizedException();
        }

        if (!tokenUserId.equals(userId)) {
            throw new ForbiddenException();
        }
    }

    private static String getUserId(String accessToken) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ME_ENDPOINT))
                .header("Authorization", "Bearer " + accessToken)
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode node = objectMapper.readTree(response.body());
                if (node.has("id")) {
                    return node.get("id").asText();
                }
            }
        } catch (Exception e) {
            BotLogger.error("Error retrieving Discord user id from token", e);
        }
        return null;
    }
}