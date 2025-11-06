package ti4.spring.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.stereotype.Component;
import ti4.message.logging.BotLogger;
import ti4.spring.api.auth.DiscordUserInfo;
import ti4.spring.api.auth.RestDiscordClient;

@Component
public class DiscordOpaqueTokenIntrospector implements OpaqueTokenIntrospector {

    private static final Cache<String, OAuth2AuthenticatedPrincipal> AUTH_CACHE = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .build();

    private final RestDiscordClient restDiscordClient;

    public DiscordOpaqueTokenIntrospector(RestDiscordClient restDiscordClient) {
        this.restDiscordClient = restDiscordClient;
    }

    @Override
    public OAuth2AuthenticatedPrincipal introspect(String token) {
        if (token == null) throw new OAuth2AuthenticationException("No token provided");

        try {
            return authenticate(token);
        } catch (OAuth2AuthenticationException e) {
            throw e;
        } catch (Exception e) {
            BotLogger.error("Error during token introspection and authentication", e);
        }

        throw newAuthenticationFailureException();
    }

    private OAuth2AuthenticatedPrincipal authenticate(String token) throws IOException, InterruptedException {
        OAuth2AuthenticatedPrincipal principal = AUTH_CACHE.getIfPresent(token);
        if (principal != null) return principal;

        DiscordUserInfo userInfo = restDiscordClient.getUserInfo(token);
        if (userInfo == null) {
            throw newAuthenticationFailureException();
        }

        principal = new DefaultOAuth2AuthenticatedPrincipal(
                userInfo.getId(),
                Map.of("id", userInfo.getId(), "username", userInfo.getUsername()),
                Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")));

        AUTH_CACHE.put(token, principal);

        return principal;
    }

    private OAuth2AuthenticationException newAuthenticationFailureException() {
        return new OAuth2AuthenticationException("Discord token provided, but failed to authenticate");
    }
}
