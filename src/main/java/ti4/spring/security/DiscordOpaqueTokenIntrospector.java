package ti4.spring.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.stereotype.Component;
import ti4.message.logging.BotLogger;
import ti4.website.EgressClientManager;

@Component
public class DiscordOpaqueTokenIntrospector implements OpaqueTokenIntrospector {

    private static final String ME_ENDPOINT = "https://discord.com/api/v10/users/@me";
    private static final Cache<String, OAuth2AuthenticatedPrincipal> AUTH_CACHE = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .build();

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

        HttpResponse<String> response = authenticateWithDiscord(token);
        if (response.statusCode() == HttpStatus.OK.value()) {
            return handleSuccessfulAuthenticate(response, token);
        }
        if (response.statusCode() == HttpStatus.UNAUTHORIZED.value()
                || response.statusCode() == HttpStatus.FORBIDDEN.value()) {
            throw newAuthenticationFailureException();
        }
        BotLogger.error(String.format(
                "Received an unexpected status code from Discord during authentication: %s", response.body()));
        throw newAuthenticationFailureException();
    }

    private HttpResponse<String> authenticateWithDiscord(String token) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(ME_ENDPOINT))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + token)
                .build();

        return EgressClientManager.getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private OAuth2AuthenticatedPrincipal handleSuccessfulAuthenticate(HttpResponse<String> response, String token)
            throws JsonProcessingException {
        JsonNode node = EgressClientManager.getObjectMapper().readTree(response.body());

        OAuth2AuthenticatedPrincipal principal = new DefaultOAuth2AuthenticatedPrincipal(
                node.get("id").asText(),
                Map.of(
                        "id", node.get("id").asText(),
                        "username", node.get("username").asText()),
                Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")));

        AUTH_CACHE.put(token, principal);

        return principal;
    }

    private OAuth2AuthenticationException newAuthenticationFailureException() {
        return new OAuth2AuthenticationException("Discord token provided, but failed to authenticate");
    }
}
