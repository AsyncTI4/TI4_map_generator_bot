package ti4.service.statistics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
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

    private static final List<Tag> COMMON_TAGS = List.of(Tag.of("component", "bot"));

    private static volatile Counter REQUESTS_TOTAL;
    private static volatile Counter ERRORS_TOTAL;

    /**
     * Initialize the metrics registry used by this class. Call once during application startup.
     * If not called, a SimpleMeterRegistry is used as a fallback (metrics won't be exported).
     */
    public static void init(MeterRegistry meterRegistry) {
        RegistryHolder.registry = meterRegistry;
        REQUESTS_TOTAL = Counter.builder(REQUESTS_TOTAL_NAME)
                .tags(COMMON_TAGS)
                .description("Total incoming bot interaction requests")
                .register(meterRegistry);
        ERRORS_TOTAL = Counter.builder(ERRORS_TOTAL_NAME)
                .tags(COMMON_TAGS)
                .description("Total error-severity events observed by the bot")
                .register(meterRegistry);
    }

    private static MeterRegistry registry() {
        MeterRegistry reg = RegistryHolder.registry;
        if (reg == null) {
            synchronized (SREStats.class) {
                if (RegistryHolder.registry == null) {
                    RegistryHolder.registry = new SimpleMeterRegistry();
                }
                reg = RegistryHolder.registry;
            }
        }
        return reg;
    }

   private static Counter requestsCounter() {
       Counter c = REQUESTS_TOTAL;
       if (c == null) {
           synchronized (SREStats.class) {
               if (REQUESTS_TOTAL == null) {
                   REQUESTS_TOTAL = Counter.builder(REQUESTS_TOTAL_NAME)
                           .tags(COMMON_TAGS)
                           .description("Total incoming bot interaction requests")
                           .register(registry());
               }
               c = REQUESTS_TOTAL;
           }
       }
       return c;
   }

   private static Counter errorsCounter() {
       Counter c = ERRORS_TOTAL;
       if (c == null) {
           synchronized (SREStats.class) {
               if (ERRORS_TOTAL == null) {
                   ERRORS_TOTAL = Counter.builder(ERRORS_TOTAL_NAME)
                           .tags(COMMON_TAGS)
                           .description("Total error-severity events observed by the bot")
                           .register(registry());
               }
               c = ERRORS_TOTAL;
           }
       }
       return c;
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
}
