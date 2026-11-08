package ti4.game.persistence;

import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
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

    private record StandbyCheck(
            @Nullable ManagedGamePreload.Snapshot preload,
            Set<String> stillCurrent,
            Set<String> savedSincePreload,
            int keptFromStandby,
            long preloadWaitMillis,
            long savedGameFilesMillis) {}

    private record BuildCounts(int fromPreload, int fromDatabase, int fromGameFiles, long millis) {}

    static void start() {
        long warmupStartedAt = System.currentTimeMillis();
        StandbyCheck standbyCheck;
        try {
            List<String> gameNames = GameLoadService.loadGameNames();
            standbyCheck = checkStandbyBuild(Set.copyOf(gameNames));
            GameManager.registerGameNames(gameNames);
        } catch (Exception e) {
            BotLogger.critical("Failed to warmup due to error while loading game names. Shutting down.", e);
            JdaService.shutdown();
            return;
        }

        JdaService.jda.getPresence().setPresence(OnlineStatus.ONLINE, Activity.customStatus("Ready to play"));

        ExecutorServiceManager.runAsync("GameManager warmup", () -> {
            try {
                buildManagedGames(warmupStartedAt, standbyCheck);
            } catch (Exception e) {
                BotLogger.critical("Failed during GameManager warmup. Shutting down.", e);
                JdaService.shutdown();
            }
        });
    }

    private static StandbyCheck checkStandbyBuild(Set<String> gameNames) {
        long waitStartedAt = System.currentTimeMillis();
        ManagedGamePreload.Snapshot preload = ManagedGamePreload.take();
        long preloadWaitMillis = System.currentTimeMillis() - waitStartedAt;
        Map<String, ManagedGame> standbyBuiltGames = ManagedGamePreload.closeStandbyBuild();

        long savedGameFilesStartedAt = System.currentTimeMillis();
        Set<String> savedSincePreload = preload == null ? null : gameFilesSavedSince(preload.startedAtMillis());
        long savedGameFilesMillis = System.currentTimeMillis() - savedGameFilesStartedAt;

        Set<String> stillCurrent = preload == null || savedSincePreload == null
                ? Set.of()
                : stillCurrent(preload, savedSincePreload, gameNames);
        int kept = discardOutdatedStandbyBuilds(standbyBuiltGames, stillCurrent);
        return new StandbyCheck(
                preload,
                stillCurrent,
                Objects.requireNonNullElse(savedSincePreload, Set.of()),
                kept,
                preloadWaitMillis,
                savedGameFilesMillis);
    }

    static Set<String> stillCurrent(
            ManagedGamePreload.Snapshot preload, Set<String> savedSincePreload, Set<String> gameNames) {
        return preload.verifiedNames().stream()
                .filter(gameNames::contains)
                .filter(name -> !savedSincePreload.contains(name))
                .collect(Collectors.toUnmodifiableSet());
    }

    static int discardOutdatedStandbyBuilds(Map<String, ManagedGame> standbyBuiltGames, Set<String> stillCurrent) {
        int kept = 0;
        for (Map.Entry<String, ManagedGame> standbyBuilt : standbyBuiltGames.entrySet()) {
            String gameName = standbyBuilt.getKey();
            if (stillCurrent.contains(gameName)) {
                kept++;
                continue;
            }
            GameManager.discardManagedGame(gameName, standbyBuilt.getValue());
        }
        return kept;
    }

    private static void buildManagedGames(long warmupStartedAt, StandbyCheck standbyCheck) {
        long changedRowsStartedAt = System.currentTimeMillis();
        Map<String, ManagedGameState> persistedGames = standbyCheck.preload() == null
                ? loadPersistedManagedGames()
                : withCurrentRows(standbyCheck.preload().rows(), standbyCheck.savedSincePreload());
        long changedRowsMillis = System.currentTimeMillis() - changedRowsStartedAt;
        BuildCounts built = buildMissing(standbyCheck.stillCurrent(), persistedGames);
        GameManager.finishWarmup();
        BotLogger.info(String.format(
                "UNBLOCKED %,d MANAGED GAMES %,d MS AFTER TAKING THE LEASE: %,d MS WAITING FOR THE STANDBY PRELOAD,"
                        + " %,d MS FINDING %,d GAME FILES SAVED SINCE THE PRELOAD, %,d MS READING CHANGED ROWS,"
                        + " %,d MS BUILDING. %,d BUILT ON THE STANDBY, %,d FROM THE STANDBY PRELOAD,"
                        + " %,d FROM THE DATABASE, %,d FROM GAME FILES",
                GameManager.getGameCount(),
                System.currentTimeMillis() - warmupStartedAt,
                standbyCheck.preloadWaitMillis(),
                standbyCheck.savedGameFilesMillis(),
                standbyCheck.savedSincePreload().size(),
                changedRowsMillis,
                built.millis(),
                standbyCheck.keptFromStandby(),
                built.fromPreload() - standbyCheck.keptFromStandby(),
                built.fromDatabase(),
                built.fromGameFiles()));
        if (JdaService.jda != null) {
            JdaService.updatePresence();
        }
    }

    private static BuildCounts buildMissing(
            Set<String> stillCurrent, @Nullable Map<String, ManagedGameState> persistedGames) {
        long startedAt = System.currentTimeMillis();
        Map<String, Exception> failedGames = new ConcurrentHashMap<>();
        AtomicInteger fromPreload = new AtomicInteger();
        AtomicInteger fromDatabase = new AtomicInteger();
        AtomicInteger fromGameFiles = new AtomicInteger();
        try (ExecutorService executorService =
                Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2)) {
            GameManager.getGameNames()
                    .forEach(name -> executorService.submit(() -> {
                        try {
                            if (!GameManager.isValid(name)) return;
                            if (stillCurrent.contains(name)) {
                                ManagedGameState preloadedGame = persistedGames.get(name);
                                GameManager.buildManagedGameIfAbsent(name, () -> new ManagedGame(preloadedGame));
                                fromPreload.incrementAndGet();
                                return;
                            }
                            boolean built = warmupManagedGame(name, persistedGames);
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
        if (changedNames.isEmpty() || DatabasePersistenceGate.isDisabled()) return preloadedRows;
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
