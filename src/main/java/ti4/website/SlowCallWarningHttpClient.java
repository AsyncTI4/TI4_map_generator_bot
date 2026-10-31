package ti4.website;

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import lombok.RequiredArgsConstructor;
import ti4.logging.SlowApiCallWarning;

@RequiredArgsConstructor
class SlowCallWarningHttpClient extends HttpClient {

    private final HttpClient delegate;

    @Override
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
            throws IOException, InterruptedException {
        long startNanos = System.nanoTime();
        String outcome = "exception";
        try {
            HttpResponse<T> response = delegate.send(request, responseBodyHandler);
            outcome = "status " + response.statusCode();
            return response;
        } finally {
            SlowApiCallWarning.warnIfSlowOutgoing(describe(request), outcome, startNanos);
        }
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(
            HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
        long startNanos = System.nanoTime();
        return warnWhenComplete(request, startNanos, delegate.sendAsync(request, responseBodyHandler));
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(
            HttpRequest request,
            HttpResponse.BodyHandler<T> responseBodyHandler,
            HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
        long startNanos = System.nanoTime();
        return warnWhenComplete(
                request, startNanos, delegate.sendAsync(request, responseBodyHandler, pushPromiseHandler));
    }

    private static <T> CompletableFuture<HttpResponse<T>> warnWhenComplete(
            HttpRequest request, long startNanos, CompletableFuture<HttpResponse<T>> future) {
        return future.whenComplete((response, error) -> {
            String outcome = response == null ? "exception" : "status " + response.statusCode();
            SlowApiCallWarning.warnIfSlowOutgoing(describe(request), outcome, startNanos);
        });
    }

    static String describe(HttpRequest request) {
        URI uri = request.uri();
        return request.method() + " " + uri.getScheme() + "://" + uri.getHost() + uri.getPath();
    }

    @Override
    public Optional<CookieHandler> cookieHandler() {
        return delegate.cookieHandler();
    }

    @Override
    public Optional<Duration> connectTimeout() {
        return delegate.connectTimeout();
    }

    @Override
    public Redirect followRedirects() {
        return delegate.followRedirects();
    }

    @Override
    public Optional<ProxySelector> proxy() {
        return delegate.proxy();
    }

    @Override
    public SSLContext sslContext() {
        return delegate.sslContext();
    }

    @Override
    public SSLParameters sslParameters() {
        return delegate.sslParameters();
    }

    @Override
    public Optional<Authenticator> authenticator() {
        return delegate.authenticator();
    }

    @Override
    public Version version() {
        return delegate.version();
    }

    @Override
    public Optional<Executor> executor() {
        return delegate.executor();
    }

    @Override
    public WebSocket.Builder newWebSocketBuilder() {
        return delegate.newWebSocketBuilder();
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public boolean awaitTermination(Duration duration) throws InterruptedException {
        return delegate.awaitTermination(duration);
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public void shutdownNow() {
        delegate.shutdownNow();
    }

    @Override
    public void close() {
        delegate.close();
    }
}
