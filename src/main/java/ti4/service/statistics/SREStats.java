package ti4.service.statistics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/*
 * SREStats: Micrometer-backed counters for bot requests and errors.
 *
 * What this file does:
 * - Exposes counters and helpers to increment and read total counts.
 *
 * How it does it:
 * - Lazily builds Micrometer Counters on a shared MeterRegistry (set via init()).
 * - If init() wasn't called yet, falls back to an in-memory SimpleMeterRegistry.
 */
@UtilityClass
public class SREStats {

    private static class RegistryHolder {
        private static volatile MeterRegistry registry;
    }

    private static final String REQUESTS_TOTAL_NAME = "ti4.bot.requests.total";
    private static final String ERRORS_TOTAL_NAME = "ti4.bot.errors.total";
    private static final String WEBSERVER_REQUEST_ERROR_TOTAL_NAME = "ti4.bot.webserver.http.errors.total";
    private static final String WEBSERVER_REQUESTS_TOTAL_NAME = "ti4.bot.webserver.http.requests.total";

    private static final List<Tag> BOT_TAGS = List.of(Tag.of("component", "bot"));
    private static final List<Tag> HTTP_TAGS = List.of(Tag.of("component", "http"));
    private static final List<Tag> REQUEST_TAGS = List.of(Tag.of("status", "request"));
    private static final List<Tag> ERROR_TAGS = List.of(Tag.of("status", "error"));

    private static volatile Counter REQUESTS_TOTAL;
    private static volatile Counter ERRORS_TOTAL;
    private static volatile Counter WEBSERVER_REQUEST_ERROR_TOTAL;
    private static volatile Counter WEBSERVER_REQUESTS_TOTAL;

    /**
     * Initialize the metrics registry used by this class. Call once during application startup.
     * If not called, a SimpleMeterRegistry is used as a fallback (metrics won't be exported).
     */
    public static void init(MeterRegistry meterRegistry) {
        RegistryHolder.registry = meterRegistry;
        REQUESTS_TOTAL = Counter.builder(REQUESTS_TOTAL_NAME)
                .tags(Stream.concat(BOT_TAGS.stream(), REQUEST_TAGS.stream()).toList())
                .description("Total incoming bot interaction requests")
                .register(meterRegistry);
        ERRORS_TOTAL = Counter.builder(ERRORS_TOTAL_NAME)
                .tags(Stream.concat(BOT_TAGS.stream(), ERROR_TAGS.stream()).toList())
                .description("Total error-severity events observed by the bot")
                .register(meterRegistry);
        WEBSERVER_REQUESTS_TOTAL = Counter.builder(WEBSERVER_REQUESTS_TOTAL_NAME)
                .tags(Stream.concat(HTTP_TAGS.stream(), REQUEST_TAGS.stream()).toList())
                .description("Total webserver HTTP requests handled/initiated by the bot's internal Spring server")
                .register(meterRegistry);
        WEBSERVER_REQUEST_ERROR_TOTAL = Counter.builder(WEBSERVER_REQUEST_ERROR_TOTAL_NAME)
                .tags(Stream.concat(HTTP_TAGS.stream(), ERROR_TAGS.stream()).toList())
                .description("Total webserver HTTP request errors")
                .register(meterRegistry);
    }

    private static MeterRegistry registry() {
        MeterRegistry reg = RegistryHolder.registry;
        if (reg != null) {
            return reg;
        }
        synchronized (SREStats.class) {
            if (RegistryHolder.registry == null) {
                RegistryHolder.registry = new SimpleMeterRegistry();
            }
            return RegistryHolder.registry;
        }
    }

    private static Counter requestsCounter() {
        Counter c = REQUESTS_TOTAL;
        if (c != null) {
            return c;
        }
        synchronized (SREStats.class) {
            if (REQUESTS_TOTAL == null) {
                REQUESTS_TOTAL = Counter.builder(REQUESTS_TOTAL_NAME)
                        .tags(Stream.concat(BOT_TAGS.stream(), REQUEST_TAGS.stream())
                                .toList())
                        .description("Total incoming bot interaction requests")
                        .register(registry());
            }
            return REQUESTS_TOTAL;
        }
    }

    private static Counter errorsCounter() {
        Counter c = ERRORS_TOTAL;
        if (c != null) {
            return c;
        }
        synchronized (SREStats.class) {
            if (ERRORS_TOTAL == null) {
                ERRORS_TOTAL = Counter.builder(ERRORS_TOTAL_NAME)
                        .tags(Stream.concat(BOT_TAGS.stream(), ERROR_TAGS.stream())
                                .toList())
                        .description("Total error-severity events observed by the bot")
                        .register(registry());
            }
            return ERRORS_TOTAL;
        }
    }

    private static Counter webserverRequestsErrorCounter() {
        Counter c = WEBSERVER_REQUEST_ERROR_TOTAL;
        if (c != null) {
            return c;
        }
        synchronized (SREStats.class) {
            if (WEBSERVER_REQUEST_ERROR_TOTAL == null) {
                WEBSERVER_REQUEST_ERROR_TOTAL = Counter.builder(WEBSERVER_REQUEST_ERROR_TOTAL_NAME)
                        .tags(Stream.concat(HTTP_TAGS.stream(), ERROR_TAGS.stream())
                                .toList())
                        .description("Total webserver HTTP request errors")
                        .register(registry());
            }
            return WEBSERVER_REQUEST_ERROR_TOTAL;
        }
    }

    private static Counter webserverRequestsTotalCounter() {
        Counter c = WEBSERVER_REQUESTS_TOTAL;
        if (c != null) {
            return c;
        }
        synchronized (SREStats.class) {
            if (WEBSERVER_REQUESTS_TOTAL == null) {
                WEBSERVER_REQUESTS_TOTAL = Counter.builder(WEBSERVER_REQUESTS_TOTAL_NAME)
                        .tags(Stream.concat(HTTP_TAGS.stream(), REQUEST_TAGS.stream())
                                .toList())
                        .description(
                                "Total webserver HTTP requests handled/initiated by the bot's internal Spring server")
                        .register(registry());
            }
            return WEBSERVER_REQUESTS_TOTAL;
        }
    }

    // Request counters
    public static void incrementRequestCount() {
        requestsCounter().increment();
    }

    public static long getRequestCount() {
        return (long) requestsCounter().count();
    }

    // Error counters
    public static void incrementErrorCount() {
        errorsCounter().increment();
    }

    public static long getErrorCount() {
        return (long) errorsCounter().count();
    }

    // Webserver HTTP request counters (totals)
    public static void incrementWebserverRequestCount() {
        webserverRequestsTotalCounter().increment();
    }

    public static long getWebserverRequestCount() {
        return (long) webserverRequestsTotalCounter().count();
    }

    // Webserver HTTP request counters (errors)
    public static void incrementWebserverRequestErrorCount() {
        webserverRequestsErrorCounter().increment();
    }

    public static long getWebserverRequestErrorCount() {
        return (long) webserverRequestsErrorCounter().count();
    }
}
