package ti4.spring.resilience;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ti4.executors.CircuitBreaker;
import ti4.spring.service.deploy.ActiveLeaseService;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
@Component
public class CircuitBreakerFilter extends OncePerRequestFilter {

    private static final String SERVICE_UNAVAILABLE_MESSAGE = "Service temporarily unavailable: ";
    private static final String READY_PATH = "/api/public/ready";
    private static final String PING_PATH = "/api/public/ping";
    private static final String DEPLOY_DRAIN_PATH = "/api/public/deploy/drain";

    @Override
    protected void doFilterInternal(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
            @NotNull FilterChain filterChain)
            throws ServletException, IOException {
        if (READY_PATH.equals(request.getRequestURI())
                || PING_PATH.equals(request.getRequestURI())
                || DEPLOY_DRAIN_PATH.equals(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }
        if (CircuitBreaker.isOpen()) {
            response.sendError(
                    HttpStatus.SERVICE_UNAVAILABLE.value(), SERVICE_UNAVAILABLE_MESSAGE + "circuit breaker is open");
            return;
        }
        if (!ActiveLeaseService.shouldCurrentProcessServeTraffic()) {
            response.sendError(
                    HttpStatus.SERVICE_UNAVAILABLE.value(), SERVICE_UNAVAILABLE_MESSAGE + "bot is not active");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
