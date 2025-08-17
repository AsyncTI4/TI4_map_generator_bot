package ti4.spring.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ti4.AsyncTI4DiscordBot;
import ti4.executors.CircuitBreaker;
import ti4.spring.exception.ServiceUnavailableException;

@Order(1)
@RequiredArgsConstructor
@Component
public class CircuitBreakerFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
            @NotNull FilterChain filterChain)
            throws ServletException, IOException {
        if (CircuitBreaker.isOpen() || !AsyncTI4DiscordBot.isReadyToReceiveCommands())
            throw new ServiceUnavailableException();

        filterChain.doFilter(request, response);
    }
}
