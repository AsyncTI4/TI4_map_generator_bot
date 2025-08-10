package ti4.spring.service.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ti4.message.BotLogger;

@Order(3)
@Component
public class ErrorLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
            @NotNull FilterChain filterChain)
            throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            BotLogger.error("Exception during request to " + request.getRequestURI(), e);
            throw e;
        }

        if (response.getStatus() >= 400) {
            String error =
                    String.format("Request to %s returned status %s", request.getRequestURI(), response.getStatus());
            BotLogger.error(error);
        }
    }
}
