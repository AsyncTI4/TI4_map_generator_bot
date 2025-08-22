package ti4.spring.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import ti4.message.logging.BotLogger;

@Order(3)
@Component
public class ErrorLoggingFilter extends OncePerRequestFilter {

    private static final int START_OF_HTTP_ERROR_RANGE = 400;

    @Override
    protected void doFilterInternal(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
            @NotNull FilterChain filterChain)
            throws ServletException, IOException {
        var cachingResponse = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(request, cachingResponse);
        } catch (Exception e) {
            BotLogger.error("Exception during request to " + request.getRequestURI(), e);
            throw e;
        }

        if (cachingResponse.getStatus() >= START_OF_HTTP_ERROR_RANGE) {
            String body = new String(cachingResponse.getContentAsByteArray(), cachingResponse.getCharacterEncoding());
            String error = String.format(
                    "Request to %s returned status %s with body: %s",
                    request.getRequestURI(), cachingResponse.getStatus(), body);
            BotLogger.error(error);
        }

        cachingResponse.copyBodyToResponse();
    }
}
