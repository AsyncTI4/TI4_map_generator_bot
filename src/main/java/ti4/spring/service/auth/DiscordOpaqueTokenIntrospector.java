package ti4.spring.service.auth;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
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

    @Override
    public OAuth2AuthenticatedPrincipal introspect(String token) {
        if (token == null) throw new OAuth2AuthenticationException("Invalid Discord token");

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ME_ENDPOINT))
                .header("Authorization", token)
                .build();

            HttpResponse<String> response = EgressClientManager.getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode node = EgressClientManager.getObjectMapper().readTree(response.body());
                return new DefaultOAuth2AuthenticatedPrincipal(
                    node.get("id").asText(),
                    Map.of(
                        "id", node.get("id").asText(),
                        "username", node.get("username").asText()),
                    Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")));
            }
        } catch (Exception e) {
            BotLogger.error("Error retrieving Discord user id from token", e);
        }

        throw new OAuth2AuthenticationException("Invalid Discord token");
    }
}