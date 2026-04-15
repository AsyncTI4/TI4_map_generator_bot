package ti4.spring.resilience;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import ti4.logging.BotLogger;
import ti4.rollbar.RollbarManager;
import ti4.service.statistics.SREStats;
import ti4.spring.security.UserNotInGameForbiddenException;

@Order(3)
@Component
public class ErrorLoggingFilter extends OncePerRequestFilter {

    private static final int START_OF_HTTP_ERROR_RANGE = 400;
    private static final int START_OF_HTTP_SERVER_ERROR_RANGE = 500;
    private static final String HTTP_ERROR_THREAD_NAME = "http-errors";

    @Override
    protected void doFilterInternal(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
            @NotNull FilterChain filterChain)
            throws ServletException, IOException {
        var cachingResponse = new ContentCachingResponseWrapper(response);
        RollbarManager.clear();
        RollbarManager.put("interaction_type", "http_request");
        RollbarManager.put("http_method", request.getMethod());
        RollbarManager.put("request_uri", request.getRequestURI());
        SREStats.incrementWebserverRequestCount();
        SREStats.incrementRequestCount();

        try {
            try {
                filterChain.doFilter(request, cachingResponse);
            } catch (Exception e) {
                if (!isAuthenticationNoise(e)) {
                    String exceptionMessage = e.getMessage() != null ? e.getMessage() : "(no message)";
                    String exceptionSummary = e.getClass().getName() + ": " + exceptionMessage;
                    BotLogger.errorToThread(
                            "Exception during request to " + request.getRequestURI() + ": " + exceptionSummary,
                            HTTP_ERROR_THREAD_NAME);
                    SREStats.incrementWebserverRequestErrorCount();
                }
                throw e;
            }

            if (shouldReportResponseStatus(cachingResponse.getStatus())) {
                String body =
                        new String(cachingResponse.getContentAsByteArray(), cachingResponse.getCharacterEncoding());
                String error = String.format(
                        "Request to %s returned status %s with body: %s",
                        request.getRequestURI(), cachingResponse.getStatus(), body);
                BotLogger.errorToThread(error, HTTP_ERROR_THREAD_NAME);
                SREStats.incrementWebserverRequestErrorCount();
                if (cachingResponse.getStatus() >= START_OF_HTTP_SERVER_ERROR_RANGE) {
                    RollbarManager.report(com.rollbar.api.payload.data.Level.ERROR, null, error, null);
                }
            }

            cachingResponse.copyBodyToResponse();
        } finally {
            RollbarManager.clear();
        }
    }

    private static boolean shouldReportResponseStatus(int statusCode) {
        return statusCode >= START_OF_HTTP_SERVER_ERROR_RANGE
                || (statusCode >= START_OF_HTTP_ERROR_RANGE
                        && statusCode != HttpStatus.UNAUTHORIZED.value()
                        && statusCode != HttpStatus.FORBIDDEN.value());
    }

    private static boolean isAuthenticationNoise(Exception exception) {
        return exception instanceof OAuth2AuthenticationException
                || exception instanceof AccessDeniedException
                || exception instanceof UserNotInGameForbiddenException;
    }
}
