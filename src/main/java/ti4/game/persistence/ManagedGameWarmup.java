package ti4.game.persistence;

import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import ti4.discord.JdaService;
import ti4.executors.ExecutorServiceManager;
import ti4.game.Game;
import ti4.helpers.StringHelper;
import ti4.logging.BotLogger;
import ti4.spring.service.persistence.PersistedManagedGameService;

@UtilityClass
class ManagedGameWarmup {

    private static final int MAX_CHANGED_ROWS_TO_READ_BY_NAME = 100;
    private static final long SAVED_SINCE_PRELOAD_MARGIN_MILLIS = 1000;

    private record Sources(
            @Nullable Map<String, ManagedGameState> persistedGames,
            Set<String> preloadedNames,
            Map<String, ManagedGame> standbyBuiltGames,
            long preloadWaitMillis,
            long revisionKeysMillis,
            long savedGameFilesMillis,
            int savedGameFileCount,
            long changedRowsMillis,
            int changedRowCount) {}

    private record BuildCounts(int fromPreload, int fromDatabase, int fromGameFiles, long millis) {}

    static void start() {
        long warmupStartedAt = System.currentTimeMillis();
        CompletableFuture<Map<String, String>> currentRevisionKeys = startReadingCurrentRevisionKeys();

        try {
            GameManager.registerGameNames(GameLoadService.loadGameNames());
            BotLogger.info("LOADED " + GameManager.getGameCount() + " GAME NAMES");
        } catch (Exception e) {
            BotLogger.critical("Failed to warmup due to error while loading game names. Shutting down.", e);
            JdaService.shutdown();
        }

        JdaService.jda.getPresence().setPresence(OnlineStatus.ONLINE, Activity.customStatus("Ready to play"));

        ExecutorServiceManager.runAsync("GameManager warmup", () -> {
            try {
                buildManagedGames(warmupStartedAt, currentRevisionKeys);
            } catch (Exception e) {
                BotLogger.critical("Failed during GameManager warmup. Shutting down.", e);
                JdaService.shutdown();
            }
        });
    }

    private static void buildManagedGames(
            long warmupStartedAt, CompletableFuture<Map<String, String>> currentRevisionKeys) {
        BotLogger.info("STARTED BUILDING MANAGED GAMES");
        Sources sources = sources(currentRevisionKeys);
        Set<String> gameNames = Set.copyOf(GameManager.getGameNames());
        int keptFromStandby =
                discardOutdatedStandbyBuilds(sources.standbyBuiltGames(), sources.preloadedNames(), gameNames);
        BuildCounts built = buildMissing(gameNames, sources);
        GameManager.finishWarmup();
        BotLogger.info(String.format(
                "MANAGED GAMES UNBLOCKED %,d MS AFTER WARMUP STARTED (%,d MS WAITING FOR THE STANDBY PRELOAD,"
                        + " %,d MS CHECKING REVISION KEYS, %,d MS FINDING %,d GAME FILES SAVED SINCE THE PRELOAD,"
                        + " %,d MS READING %,d CHANGED ROWS, %,d MS BUILDING):"
                        + " %,d BUILT ON THE STANDBY, %,d FROM THE STANDBY PRELOAD, %,d FROM THE DATABASE,"
                        + " %,d FROM GAME FILES",
                System.currentTimeMillis() - warmupStartedAt,
                sources.preloadWaitMillis(),
                sources.revisionKeysMillis(),
                sources.savedGameFilesMillis(),
                sources.savedGameFileCount(),
                sources.changedRowsMillis(),
                sources.changedRowCount(),
                built.millis(),
                keptFromStandby,
                built.fromPreload() - keptFromStandby,
                built.fromDatabase(),
                built.fromGameFiles()));
        if (JdaService.jda != null) {
            JdaService.updatePresence();
        }
    }

    private static BuildCounts buildMissing(Set<String> gameNames, Sources sources) {
        long startedAt = System.currentTimeMillis();
        Map<String, Exception> failedGames = new ConcurrentHashMap<>();
        AtomicInteger fromPreload = new AtomicInteger();
        AtomicInteger fromDatabase = new AtomicInteger();
        AtomicInteger fromGameFiles = new AtomicInteger();
        try (ExecutorService executorService =
                Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2)) {
            gameNames.forEach(name -> executorService.submit(() -> {
                try {
                    if (sources.preloadedNames().contains(name)) {
                        ManagedGameState preloadedGame =
                                sources.persistedGames().get(name);
                        GameManager.buildManagedGameIfAbsent(name, () -> new ManagedGame(preloadedGame));
                        fromPreload.incrementAndGet();
                        return;
                    }
                    boolean built = warmupManagedGame(name, sources.persistedGames());
                    (built ? fromDatabase : fromGameFiles).incrementAndGet();
                } catch (Exception e) {
                    failedGames.put(name, e);
                }
            }));
        }
        reportGamesThatFailedWarmup(failedGames);
        return new BuildCounts(
                fromPreload.get(), fromDatabase.get(), fromGameFiles.get(), System.currentTimeMillis() - startedAt);
    }

    private static CompletableFuture<Map<String, String>> startReadingCurrentRevisionKeys() {
        if (DatabasePersistenceGate.isDisabled()) return CompletableFuture.completedFuture(Map.of());
        return CompletableFuture.supplyAsync(
                ManagedGameWarmup::loadCurrentRevisionKeys,
                task -> Thread.ofPlatform().name("revision-key-read").daemon().start(task));
    }

    private static Sources sources(CompletableFuture<Map<String, String>> currentRevisionKeysRead) {
        long waitStartedAt = System.currentTimeMillis();
        ManagedGamePreload.Snapshot preload = ManagedGamePreload.take();
        long preloadWaitMillis = System.currentTimeMillis() - waitStartedAt;
        Map<String, ManagedGame> standbyBuiltGames = ManagedGamePreload.closeStandbyBuild();
        if (preload == null) {
            return new Sources(
                    loadPersistedManagedGames(), Set.of(), standbyBuiltGames, preloadWaitMillis, 0, 0, 0, 0, 0);
        }

        long revisionKeysStartedAt = System.currentTimeMillis();
        Map<String, String> currentRevisionKeys = currentRevisionKeysRead.join();
        long revisionKeysMillis = System.currentTimeMillis() - revisionKeysStartedAt;

        long savedGameFilesStartedAt = System.currentTimeMillis();
        Set<String> savedSincePreload = gameFilesSavedSince(preload.startedAtMillis());
        long savedGameFilesMillis = System.currentTimeMillis() - savedGameFilesStartedAt;
        if (savedSincePreload == null) {
            return new Sources(
                    preload.rows(),
                    Set.of(),
                    standbyBuiltGames,
                    preloadWaitMillis,
                    revisionKeysMillis,
                    savedGameFilesMillis,
                    0,
                    0,
                    0);
        }
        Set<String> stillCurrent = stillCurrent(preload, currentRevisionKeys, savedSincePreload);

        long changedRowsStartedAt = System.currentTimeMillis();
        Set<String> changedNames = Stream.concat(currentRevisionKeys.keySet().stream(), savedSincePreload.stream())
                .filter(name -> !stillCurrent.contains(name))
                .collect(Collectors.toUnmodifiableSet());
        Map<String, ManagedGameState> persistedGames = withCurrentRows(preload.rows(), changedNames);
        long changedRowsMillis = System.currentTimeMillis() - changedRowsStartedAt;

        return new Sources(
                persistedGames,
                stillCurrent,
                standbyBuiltGames,
                preloadWaitMillis,
                revisionKeysMillis,
                savedGameFilesMillis,
                savedSincePreload.size(),
                changedRowsMillis,
                changedNames.size());
    }

    static Set<String> stillCurrent(
            ManagedGamePreload.Snapshot preload,
            Map<String, String> currentRevisionKeys,
            Set<String> savedSincePreload) {
        return preload.stillCurrent(currentRevisionKeys).stream()
                .filter(name -> !savedSincePreload.contains(name))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Nullable
    private static Set<String> gameFilesSavedSince(long preloadStartedAtMillis) {
        try {
            return GameLoadService.gameNamesSavedSince(preloadStartedAtMillis - SAVED_SINCE_PRELOAD_MARGIN_MILLIS);
        } catch (Exception e) {
            BotLogger.error(
                    "Could not list the game files saved since the standby preload, so every game will be checked against its file.",
                    e);
            return null;
        }
    }

    private static Map<String, ManagedGameState> withCurrentRows(
            Map<String, ManagedGameState> preloadedRows, Set<String> changedNames) {
        if (changedNames.isEmpty()) return preloadedRows;
        if (changedNames.size() > MAX_CHANGED_ROWS_TO_READ_BY_NAME) {
            return Objects.requireNonNullElse(loadPersistedManagedGames(), preloadedRows);
        }
        try {
            Map<String, ManagedGameState> rows = new HashMap<>(preloadedRows);
            rows.putAll(PersistedManagedGameService.getBean().loadAll(changedNames));
            return rows;
        } catch (Exception e) {
            BotLogger.error("Could not read the changed managed game rows, so they will be built from game files.", e);
            return preloadedRows;
        }
    }

    static int discardOutdatedStandbyBuilds(
            Map<String, ManagedGame> standbyBuiltGames, Set<String> stillCurrent, Set<String> gameNames) {
        int kept = 0;
        for (Map.Entry<String, ManagedGame> standbyBuilt : standbyBuiltGames.entrySet()) {
            String gameName = standbyBuilt.getKey();
            if (stillCurrent.contains(gameName) && gameNames.contains(gameName)) {
                kept++;
                continue;
            }
            GameManager.discardManagedGame(gameName, standbyBuilt.getValue());
        }
        return kept;
    }

    private static Map<String, String> loadCurrentRevisionKeys() {
        try {
            return PersistedManagedGameService.getBean().loadRevisionKeys();
        } catch (Exception e) {
            BotLogger.error("Could not read current revision keys, so the standby preload will be rechecked.", e);
            return Map.of();
        }
    }

    @Nullable
    private static Map<String, ManagedGameState> loadPersistedManagedGames() {
        if (DatabasePersistenceGate.isDisabled()) {
            BotLogger.info("Database maintenance mode is on, so every managed game will be built from its game file.");
            return null;
        }
        try {
            return PersistedManagedGameService.getBean().loadAll();
        } catch (Exception e) {
            BotLogger.error(
                    "Failed to load managed games from the database, so they will be built from game files.", e);
            return null;
        }
    }

    static boolean warmupManagedGame(String gameName, @Nullable Map<String, ManagedGameState> persistedGames) {
        ManagedGameState persistedGame = persistedGames == null ? null : persistedGames.get(gameName);
        if (persistedGame != null && buildManagedGameFromCurrentRow(gameName, persistedGame)) {
            return true;
        }
        Game game = buildManagedGameFromGameFile(gameName);
        if (game != null && persistedGames != null) {
            GameDatabaseSyncPipeline.queueSyncFromGameFile(gameName);
        }
        return false;
    }

    private static boolean buildManagedGameFromCurrentRow(String gameName, ManagedGameState persistedGame) {
        if (!persistedGame.matchesRevision(readRevisionKeyOrNull(gameName))) return false;
        GameManager.buildManagedGameIfAbsent(gameName, () -> new ManagedGame(persistedGame));
        return true;
    }

    @Nullable
    private static String readRevisionKeyOrNull(String gameName) {
        try {
            return GameRevisionKeyFile.read(gameName);
        } catch (UncheckedIOException e) {
            return null;
        }
    }

    @Nullable
    private static Game buildManagedGameFromGameFile(String gameName) {
        Game game = GameLoadService.load(gameName);
        if (game != null) {
            GameManager.buildManagedGameIfAbsent(gameName, () -> new ManagedGame(game));
        }
        return game;
    }

    private static void reportGamesThatFailedWarmup(Map<String, Exception> failedGames) {
        if (failedGames.isEmpty()) return;
        List<String> failedGameNames = failedGames.keySet().stream().sorted().toList();
        String firstFailedGameName = failedGameNames.getFirst();
        BotLogger.error(
                "Could not build " + StringHelper.pluralize(failedGameNames.size(), "managed game")
                        + " at startup: " + String.join(", ", failedGameNames)
                        + ". They stay unloaded until they are next accessed. The stack trace is from "
                        + firstFailedGameName + ".",
                failedGames.get(firstFailedGameName));
    }
}
