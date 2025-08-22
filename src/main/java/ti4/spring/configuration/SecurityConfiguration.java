package ti4.spring.configuration;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import ti4.spring.auth.DiscordOpaqueTokenIntrospector;

@RequiredArgsConstructor
@EnableMethodSecurity
@Configuration
public class SecurityConfiguration {

    private final DiscordOpaqueTokenIntrospector discordOpaqueTokenIntrospector;

    @Value("${management.endpoints.actuator.api.key:${MANAGEMENT_ENDPOINTS_ACTUATOR_API_KEY:}}")
    private String actuatorApiKey;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/**")
                        .hasRole("ACTUATOR")
                        // Public API paths
                        .requestMatchers("/api/public/**")
                        .permitAll()
                        // Everything else requires auth
                        .anyRequest()
                        .authenticated())
                // Add API key filter for /actuator/** when key is configured.
                .addFilterBefore(new ActuatorApiKeyFilter(actuatorApiKey), AuthorizationFilter.class)
                .oauth2ResourceServer(
                        oauth2 -> oauth2.opaqueToken(token -> token.introspector(discordOpaqueTokenIntrospector)));

        return http.build();
    }
}
