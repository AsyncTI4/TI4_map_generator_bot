package ti4.spring.api.auth;

import org.springframework.stereotype.Service;

@Service
class DiscordAuthService {

    private static final String DISCORD_REDIRECT_URI = System.getenv("DISCORD_REDIRECT_URI");

    private final RestDiscordClient restDiscordClient;

    DiscordAuthService(RestDiscordClient restDiscordClient) {
        this.restDiscordClient = restDiscordClient;
    }

    AuthResponse authenticate(String userId, String code) {
        try {
            DiscordTokenResponse tokenResponse = restDiscordClient.exchangeCodeForToken(code, DISCORD_REDIRECT_URI);

            DiscordUserInfo userInfo = restDiscordClient.getUserInfo(tokenResponse.getAccessToken());

            return new AuthResponse(
                    userId,
                    userInfo.getId(),
                    userInfo.getFormattedName(),
                    tokenResponse.getAccessToken(),
                    tokenResponse.getRefreshToken(),
                    tokenResponse.getExpiresIn());
        } catch (Exception e) {
            throw new RuntimeException("Failed to authenticate: " + e.getMessage());
        }
    }

    AuthResponse refresh(String userId, String refreshToken) {
        try {
            DiscordTokenResponse tokenResponse = restDiscordClient.refreshAccessToken(refreshToken);

            DiscordUserInfo userInfo = restDiscordClient.getUserInfo(tokenResponse.getAccessToken());

            return new AuthResponse(
                    userId,
                    userInfo.getId(),
                    userInfo.getFormattedName(),
                    tokenResponse.getAccessToken(),
                    tokenResponse.getRefreshToken(),
                    tokenResponse.getExpiresIn());
        } catch (Exception e) {
            throw new RuntimeException("Failed to refresh: " + e.getMessage());
        }
    }
}
