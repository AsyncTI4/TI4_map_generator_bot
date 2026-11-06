package ti4.model.metadata;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.logging.BotLogger;
import ti4.spring.service.autoping.AutoPingService;

@UtilityClass
public class AutoPingMetadataManager {

    private static final Duration MINIMUM_TIME_BETWEEN_PERSISTED_DELAYS = Duration.ofMinutes(1);
    private static final Cache<String, Boolean> recentlyDelayedGameNames = Caffeine.newBuilder()
            .expireAfterWrite(MINIMUM_TIME_BETWEEN_PERSISTED_DELAYS)
            .build();

    public static void setupAutoPing(String gameName) {
        run(service -> service.compute(gameName, _ -> new AutoPing(System.currentTimeMillis(), 0, false)));
    }

    public static void addPing(String gameName) {
        run(service -> service.compute(
                gameName,
                autoPing -> new AutoPing(
                        System.currentTimeMillis(), autoPing == null ? 1 : autoPing.pingCount() + 1, false)));
    }

    public static void delayPing(String gameName) {
        if (recentlyDelayedGameNames.asMap().putIfAbsent(gameName, Boolean.TRUE) != null) {
            return;
        }
        run(service -> service.updateIfPresent(
                gameName,
                autoPing -> new AutoPing(System.currentTimeMillis(), autoPing.pingCount(), autoPing.quickPing())));
    }

    public static void setupQuickPing(String gameName) {
        run(service -> service.compute(
                gameName,
                autoPing ->
                        new AutoPing(System.currentTimeMillis(), autoPing == null ? 0 : autoPing.pingCount(), true)));
    }

    public static void remove(List<String> gameNames) {
        run(service -> service.delete(gameNames));
    }

    @Nullable
    public static AutoPing getLatestAutoPing(String gameName) {
        return call(service -> service.find(gameName).orElse(null), null);
    }

    public static Set<String> getGameNamesWithAutoPing() {
        return call(AutoPingService::gameNames, Set.of());
    }

    private static void run(Consumer<AutoPingService> operation) {
        call(
                service -> {
                    operation.accept(service);
                    return null;
                },
                null);
    }

    private static synchronized <T> T call(Function<AutoPingService, T> operation, T fallback) {
        try {
            return operation.apply(AutoPingService.getBean());
        } catch (Exception e) {
            BotLogger.error("Failed to read or write auto pings.", e);
            return fallback;
        }
    }

    public record AutoPing(long lastPingTimeEpochMilliseconds, int pingCount, boolean quickPing) {}
}
