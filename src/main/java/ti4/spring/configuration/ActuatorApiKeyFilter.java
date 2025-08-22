package ti4.spring.configuration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * What: A servlet filter that, for /actuator/**, grants ROLE_ACTUATOR when X-API-KEY matches the configured key.
 * How:
 *  - Activates only for request paths that start with /actuator/.
 *  - If the configured key is blank/null, the filter is effectively disabled (no auth is set).
 *  - If the request is already authenticated (by any other mechanism), do nothing (do not re-check X-API-KEY).
 *  - Otherwise, if header "X-API-KEY" matches the configured key, sets an authenticated principal with ROLE_ACTUATOR.
 *  - If missing or mismatched, does not short-circuit; downstream authorization will return 401/403 as appropriate.
 */
public class ActuatorApiKeyFilter extends OncePerRequestFilter {

    private static final String ACTUATOR_PATH = "/actuator/";
    private static final String API_KEY_HEADER = "X-API-KEY";

    private final String expectedKey;

    public ActuatorApiKeyFilter(String expectedKey) {
        this.expectedKey = expectedKey;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only run for /actuator/**, and only if a key is configured (non-blank)
        String path = request.getRequestURI();
        boolean isActuator = path != null && (path.equals("/actuator") || path.startsWith(ACTUATOR_PATH));
        boolean keyConfigured = StringUtils.hasText(expectedKey);
        return !(isActuator && keyConfigured);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey == null) {
            filterChain.doFilter(request, response);
            return;
        }
        boolean apiKeyMatches = apiKey.equals(expectedKey);
        if (apiKeyMatches) {
            var current = SecurityContextHolder.getContext().getAuthentication();
            Object principal = current != null ? current.getPrincipal() : "actuator";
            Object credentials = current != null ? current.getCredentials() : "N/A";
            List<GrantedAuthority> merged = new ArrayList<>();
            if (current != null && current.getAuthorities() != null) {
                merged.addAll(current.getAuthorities());
            }
            merged.add(new SimpleGrantedAuthority("ROLE_ACTUATOR"));

            UsernamePasswordAuthenticationToken elevated =
                    new UsernamePasswordAuthenticationToken(principal, credentials, merged);
            if (current != null) {
                elevated.setDetails(current.getDetails());
            }
            SecurityContextHolder.getContext().setAuthentication(elevated);
        }

        filterChain.doFilter(request, response);
    }
}
