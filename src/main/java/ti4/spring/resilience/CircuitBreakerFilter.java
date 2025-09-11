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
import ti4.AsyncTI4DiscordBot;
import ti4.executors.CircuitBreaker;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
@Component
public class CircuitBreakerFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
            @NotNull FilterChain filterChain)
            throws ServletException, IOException {
        if (CircuitBreaker.isOpen()) {
            response.sendError(
                    HttpStatus.SERVICE_UNAVAILABLE.value(), "Service temporarily unavailable: circuit breaker is open");
            return;
        }
        if (!AsyncTI4DiscordBot.isReadyToReceiveCommands()) {
            response.sendError(
                    HttpStatus.SERVICE_UNAVAILABLE.value(), "Service temporarily unavailable: bot is not ready");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
