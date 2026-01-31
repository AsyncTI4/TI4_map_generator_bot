package ti4.spring.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter that attempts to authenticate requests on public endpoints without requiring authentication.
 * This allows endpoints like /api/public/** to optionally use authentication when provided,
 * enabling FoW games to return player-specific maps while non-FoW games remain fully public.
 *
 * <p>If a Bearer token is present, this filter attempts to validate it and set the security context.
 * If validation fails, the request continues without authentication (the controller handles authorization).
 */
@Component
@RequiredArgsConstructor
public class OptionalAuthFilter extends OncePerRequestFilter {

    private static final String PUBLIC_API_PATH = "/api/public/";
    private static final String BEARER_PREFIX = "Bearer ";

    private final DiscordOpaqueTokenIntrospector introspector;

    @Override
    protected boolean shouldNotFilter(@NotNull HttpServletRequest request) {
        String path = request.getRequestURI();
        // Only apply to public API paths
        return path == null || !path.startsWith(PUBLIC_API_PATH);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull FilterChain chain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            try {
                String token = authHeader.substring(BEARER_PREFIX.length());
                OAuth2AuthenticatedPrincipal principal = introspector.introspect(token);

                // Create an OAuth2AccessToken for BearerTokenAuthentication
                OAuth2AccessToken accessToken =
                        new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, token, Instant.now(), null);

                // Create and set the authentication
                BearerTokenAuthentication authentication =
                        new BearerTokenAuthentication(principal, accessToken, principal.getAuthorities());

                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception e) {
                // Token invalid or expired - continue without auth.
                // The controller will handle 401 for FoW games if auth is required.
            }
        }

        chain.doFilter(request, response);
    }
}
