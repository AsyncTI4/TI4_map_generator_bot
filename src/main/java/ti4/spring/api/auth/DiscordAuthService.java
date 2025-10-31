package ti4.spring.api.auth;

import java.io.IOException;
import org.springframework.stereotype.Service;
import ti4.message.logging.BotLogger;

@Service
public class DiscordAuthService {
    private static final String DISCORD_REDIRECT_URI = System.getenv("DISCORD_REDIRECT_URI");

    private final RestDiscordClient restDiscordClient;

    public DiscordAuthService(RestDiscordClient restDiscordClient) {
        this.restDiscordClient = restDiscordClient;
    }

    /**
     * Exchange Discord authorization code for access token
     */
    public DiscordTokenResponse exchangeCodeForToken(String code) throws IOException, InterruptedException {
        try {
            return restDiscordClient.exchangeCodeForToken(code, DISCORD_REDIRECT_URI);
        } catch (IOException e) {
            BotLogger.error("Discord token exchange failed: " + e.getMessage());
            throw new RuntimeException("Failed to exchange code for token", e);
        }
    }

    /**
     * Get user information from Discord using bearer token
     */
    public DiscordUserInfo getUserInfo(String bearerToken) throws IOException, InterruptedException {
        try {
            return restDiscordClient.getUserInfo(bearerToken);
        } catch (IOException e) {
            BotLogger.error("Discord user info request failed: " + e.getMessage());
            throw new RuntimeException("Failed to get user info", e);
        }
    }

    /**
     * Refresh Discord access token using refresh token
     */
    public DiscordTokenResponse refreshAccessToken(String refreshToken) throws IOException, InterruptedException {
        try {
            return restDiscordClient.refreshAccessToken(refreshToken);
        } catch (IOException e) {
            BotLogger.error("Discord token refresh failed: " + e.getMessage());
            throw new RuntimeException("Failed to refresh token", e);
        }
    }

    /**
     * Full authentication flow: exchange code for token and get user info
     */
    public AuthResponse authenticate(String userId, String code) {
        try {
            // Exchange code for token
            DiscordTokenResponse tokenResponse = exchangeCodeForToken(code);

            // Get user info
            DiscordUserInfo userInfo = getUserInfo(tokenResponse.getAccessToken());

            // Build response
            return new AuthResponse(
                    userId,
                    userInfo.getId(),
                    userInfo.getFormattedName(),
                    tokenResponse.getAccessToken(),
                    tokenResponse.getRefreshToken(),
                    tokenResponse.getExpiresIn());

        } catch (Exception e) {
            BotLogger.error("Discord authentication failed for userId: " + userId, e);
            throw new RuntimeException("Authentication failed: " + e.getMessage(), e);
        }
    }

    /**
     * Refresh token flow: refresh access token and get updated user info
     */
    public AuthResponse refresh(String userId, String refreshToken) {
        try {
            // Refresh the token
            DiscordTokenResponse tokenResponse = refreshAccessToken(refreshToken);

            // Get user info with new token
            DiscordUserInfo userInfo = getUserInfo(tokenResponse.getAccessToken());

            // Build response
            return new AuthResponse(
                    userId,
                    userInfo.getId(),
                    userInfo.getFormattedName(),
                    tokenResponse.getAccessToken(),
                    tokenResponse.getRefreshToken(),
                    tokenResponse.getExpiresIn());

        } catch (Exception e) {
            BotLogger.error("Discord token refresh failed for userId: " + userId, e);
            throw new RuntimeException("Token refresh failed: " + e.getMessage(), e);
        }
    }
}
