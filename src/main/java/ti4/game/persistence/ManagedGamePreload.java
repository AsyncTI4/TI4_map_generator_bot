package ti4.game.persistence;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.logging.BotLogger;
import ti4.spring.service.persistence.PersistedManagedGameService;

@UtilityClass
class ManagedGamePreload {

    private static final int TAKE_TIMEOUT_SECONDS = 120;
    private static final AtomicReference<CompletableFuture<Snapshot>> preload = new AtomicReference<>();
    private static final ReadWriteLock standbyBuildLock = new ReentrantReadWriteLock();
    private static final Map<String, ManagedGame> standbyBuiltGames = new ConcurrentHashMap<>();
    private static boolean standbyBuildClosed;
    private static final Executor MANAGED_GAME_BUILDER =
            task -> Thread.ofPlatform().name("managed-game-prebuild").daemon().start(task);

    record Snapshot(Map<String, ManagedGameState> rows, Set<String> verifiedNames, long startedAtMillis) {

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

    static void buildManagedGames() {
        preload.updateAndGet(future -> future == null
                ? null
                : future.thenApplyAsync(ManagedGamePreload::withManagedGames, MANAGED_GAME_BUILDER));
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
        long startedAtMillis = System.currentTimeMillis();
        Map<String, ManagedGameState> rows =
                PersistedManagedGameService.getBean().loadAll();
        Set<String> verifiedNames = ConcurrentHashMap.newKeySet();
        try (ExecutorService executorService = newBuildPool()) {
            rows.forEach((name, row) -> executorService.submit(() -> {
                if (row.matchesRevision(readRevisionKeyOrNull(name))) verifiedNames.add(name);
            }));
        }
        return new Snapshot(rows, Set.copyOf(verifiedNames), startedAtMillis);
    }

    @Nullable
    private static Snapshot withManagedGames(@Nullable Snapshot snapshot) {
        if (snapshot == null) return null;
        Set<String> failedNames = ConcurrentHashMap.newKeySet();
        try (ExecutorService executorService = newBuildPool()) {
            snapshot.verifiedNames()
                    .forEach(name -> executorService.submit(() -> {
                        try {
                            buildOnStandby(snapshot.rows().get(name));
                        } catch (Exception e) {
                            failedNames.add(name);
                        }
                    }));
        }
        if (!failedNames.isEmpty()) {
            BotLogger.warning("Could not build " + failedNames.size()
                    + " managed games on the standby, so takeover will build them: "
                    + String.join(", ", failedNames.stream().sorted().limit(20).toList()));
        }
        return snapshot;
    }

    private static void buildOnStandby(ManagedGameState preloadedGame) {
        standbyBuildLock.readLock().lock();
        try {
            if (standbyBuildClosed) return;
            ManagedGame managedGame =
                    GameManager.buildManagedGameIfAbsent(preloadedGame.name(), () -> new ManagedGame(preloadedGame));
            standbyBuiltGames.put(preloadedGame.name(), managedGame);
        } finally {
            standbyBuildLock.readLock().unlock();
        }
    }

    static Map<String, ManagedGame> closeStandbyBuild() {
        standbyBuildLock.writeLock().lock();
        try {
            standbyBuildClosed = true;
            Map<String, ManagedGame> built = Map.copyOf(standbyBuiltGames);
            standbyBuiltGames.clear();
            return built;
        } finally {
            standbyBuildLock.writeLock().unlock();
        }
    }

    private static ExecutorService newBuildPool() {
        return Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);
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
