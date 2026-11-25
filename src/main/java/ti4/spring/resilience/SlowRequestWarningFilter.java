package ti4.spring.resilience;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ti4.logging.SlowApiCallWarning;

@Order(2)
@Component
public class SlowRequestWarningFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
            @NotNull FilterChain filterChain)
            throws ServletException, IOException {
        long startNanos = System.nanoTime();
        String outcome = "exception";
        try {
            filterChain.doFilter(request, response);
            outcome = "status " + response.getStatus();
        } finally {
            SlowApiCallWarning.warnIfSlowIncoming(
                    request.getMethod() + " " + request.getRequestURI(), outcome, startNanos);
        }
    }
}
