package ti4.game.persistence;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.logging.BotLogger;
import ti4.spring.service.persistence.PersistedManagedGameService;

@UtilityClass
class ManagedGamePreload {

    private static final int TAKE_TIMEOUT_SECONDS = 120;
    private static final AtomicReference<CompletableFuture<Snapshot>> preload = new AtomicReference<>();

    record Snapshot(Map<String, ManagedGameState> rows, Set<String> verifiedNames) {

        Set<String> stillCurrent(Map<String, String> currentRevisionKeys) {
            return verifiedNames.stream()
                    .filter(name -> Objects.equals(
                            currentRevisionKeys.get(name), rows.get(name).revisionKey()))
                    .collect(Collectors.toUnmodifiableSet());
        }
    }

    static void start() {
        CompletableFuture<Snapshot> future = new CompletableFuture<>();
        if (!preload.compareAndSet(null, future)) return;
        Thread.ofPlatform().name("managed-game-preload").daemon().start(() -> complete(future));
    }

    private static void complete(CompletableFuture<Snapshot> future) {
        try {
            future.complete(build());
        } catch (Exception e) {
            BotLogger.error("Could not preload managed games, so takeover will build them from scratch.", e);
            future.complete(null);
        }
    }

    @Nullable
    private static Snapshot build() {
        if (DatabasePersistenceGate.isDisabled()) return null;
        Map<String, ManagedGameState> rows =
                PersistedManagedGameService.getBean().loadAll();
        Set<String> verifiedNames = ConcurrentHashMap.newKeySet();
        try (ExecutorService executorService =
                Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2)) {
            rows.forEach((name, row) -> executorService.submit(() -> {
                if (row.matchesRevision(readRevisionKeyOrNull(name))) verifiedNames.add(name);
            }));
        }
        return new Snapshot(rows, Set.copyOf(verifiedNames));
    }

    @Nullable
    private static String readRevisionKeyOrNull(String gameName) {
        try {
            return GameRevisionKeyFile.read(gameName);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Nullable
    static Snapshot take() {
        CompletableFuture<Snapshot> future = preload.getAndSet(null);
        if (future == null) return null;
        try {
            return future.get(TAKE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            BotLogger.error("Gave up waiting for the managed game preload, so takeover will build from scratch.", e);
            return null;
        }
    }
}
