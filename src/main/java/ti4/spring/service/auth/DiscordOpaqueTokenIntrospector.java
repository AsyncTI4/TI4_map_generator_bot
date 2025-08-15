package ti4.spring.service.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.stereotype.Component;
import ti4.message.BotLogger;
import ti4.website.EgressClientManager;

@Component
public class DiscordOpaqueTokenIntrospector implements OpaqueTokenIntrospector {

    private static final String ME_ENDPOINT = "https://discord.com/api/users/@me";
    private static final Cache<String, OAuth2AuthenticatedPrincipal> CACHE =
            Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).build();

    @Override
    public OAuth2AuthenticatedPrincipal introspect(String token) {
        if (token == null) throw new OAuth2AuthenticationException("No token provided");

        try {
            OAuth2AuthenticatedPrincipal principal = CACHE.getIfPresent(token);
            if (principal != null) return principal;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ME_ENDPOINT))
                    .header("Authorization", String.format("Bearer %s", token))
                    .build();

            HttpResponse<String> response =
                    EgressClientManager.getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode node = EgressClientManager.getObjectMapper().readTree(response.body());
                principal = new DefaultOAuth2AuthenticatedPrincipal(
                        node.get("id").asText(),
                        Map.of(
                                "id", node.get("id").asText(),
                                "username", node.get("username").asText()),
                        Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")));
                CACHE.put(token, principal);
                return principal;
            } else {
                BotLogger.error(String.format(
                        "Discord did not indicate success getting the user token.   %s", response.body()));
            }
        } catch (Exception e) {
            BotLogger.error("Error retrieving Discord user id from token", e);
        }

        throw new OAuth2AuthenticationException("Discord token provided, but did not auth");
    }
}
