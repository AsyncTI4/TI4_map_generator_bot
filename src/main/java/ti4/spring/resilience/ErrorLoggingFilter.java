package ti4.spring.resilience;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import ti4.logging.BotLogger;
import ti4.logging.RollbarManager;
import ti4.service.statistics.SREStats;
import ti4.spring.security.UserNotInGameForbiddenException;

@Order(3)
@Component
public class ErrorLoggingFilter extends OncePerRequestFilter {

    private static final int START_OF_HTTP_ERROR_RANGE = 400;
    private static final int START_OF_HTTP_SERVER_ERROR_RANGE = 500;
    private static final String HTTP_ERROR_THREAD_NAME = "http-errors";
    private static final String READY_PATH = "/api/public/ready";
    private static final List<IgnoredHttpErrorRule> IGNORED_HTTP_ERROR_RULES =
            List.of(new IgnoredHttpErrorRule(READY_PATH, HttpStatus.SERVICE_UNAVAILABLE.value()));

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

            String requestUri = request.getRequestURI();
            int status = cachingResponse.getStatus();
            if (shouldReportResponseStatus(requestUri, status)) {
                String body =
                        new String(cachingResponse.getContentAsByteArray(), cachingResponse.getCharacterEncoding());
                if (!shouldIgnoreReportedStatus(requestUri, status, body)) {
                    String error =
                            String.format("Request to %s returned status %s with body: %s", requestUri, status, body);
                    BotLogger.errorToThread(error, HTTP_ERROR_THREAD_NAME);
                    SREStats.incrementWebserverRequestErrorCount();
                    if (status >= START_OF_HTTP_SERVER_ERROR_RANGE) {
                        RollbarManager.report(com.rollbar.api.payload.data.Level.ERROR, null, error, null);
                    }
                }
            }

            cachingResponse.copyBodyToResponse();
        } finally {
            RollbarManager.clear();
        }
    }

    private static boolean shouldReportResponseStatus(String requestUri, int statusCode) {
        if (READY_PATH.equals(requestUri) && statusCode == HttpStatus.SERVICE_UNAVAILABLE.value()) {
            return true;
        }
        return statusCode >= START_OF_HTTP_SERVER_ERROR_RANGE
                || (statusCode >= START_OF_HTTP_ERROR_RANGE
                        && statusCode != HttpStatus.UNAUTHORIZED.value()
                        && statusCode != HttpStatus.FORBIDDEN.value());
    }

    static boolean shouldIgnoreReportedStatus(String requestUri, int statusCode, String body) {
        return IGNORED_HTTP_ERROR_RULES.stream().anyMatch(rule -> rule.matches(requestUri, statusCode, body));
    }

    private static boolean isAuthenticationNoise(Exception exception) {
        return exception instanceof OAuth2AuthenticationException
                || exception instanceof AccessDeniedException
                || exception instanceof UserNotInGameForbiddenException;
    }

    private record IgnoredHttpErrorRule(String requestPath, int statusCode) {

        private boolean matches(String requestUri, int responseStatusCode, String body) {
            return requestPath.equals(requestUri) && statusCode == responseStatusCode;
        }
    }
}
