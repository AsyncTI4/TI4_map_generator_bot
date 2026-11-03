package ti4.game.persistence;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.JdaService;
import ti4.executors.ExecutorServiceManager;
import ti4.game.Game;
import ti4.helpers.StringHelper;
import ti4.logging.BotLogger;
import ti4.service.fow.LoreService;
import ti4.service.persistence.DatabasePersistenceGate;
import ti4.service.persistence.GameDatabaseSyncPipeline;
import ti4.spring.service.persistence.PersistedManagedGame;
import ti4.spring.service.persistence.PersistedManagedGameService;
import ti4.spring.websocket.GameWebStatePipeline;

@UtilityClass
public class GameManager {

    // TODO: We can evaluate dropping the managed objects entirely
    private static final Set<String> gameNames = ConcurrentHashMap.newKeySet();
    private static final ConcurrentMap<String, ManagedGame> gameNameToManagedGame = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, ManagedPlayer> userIdToManagedPlayer = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Long> reportedCorruptFileVersions = new ConcurrentHashMap<>();
    private static final AtomicInteger latestPbdNumber = new AtomicInteger();

    private static final CountDownLatch gameNamesLoadedLatch = new CountDownLatch(1);
    private static final AtomicBoolean warmupStarted = new AtomicBoolean(false);
    private static final CountDownLatch warmupFinishedLatch = new CountDownLatch(1);
    private static final int WAIT_FOR_WARMUP_TIMEOUT_SECONDS = 40;

    public static void warmup() {
        if (!warmupStarted.compareAndSet(false, true)) {
            return;
        }

        try {
            gameNames.addAll(GameLoadService.loadGameNames());
            resetLatestPbdNumberFrom(0);
            gameNamesLoadedLatch.countDown();
            BotLogger.info("LOADED " + gameNames.size() + " GAME NAMES");
        } catch (Exception e) {
            BotLogger.critical("Failed to warmup due to error while loading game names. Shutting down.", e);
            JdaService.shutdown();
        }

        JdaService.jda.getPresence().setPresence(OnlineStatus.ONLINE, Activity.customStatus("Ready to play"));

        ExecutorServiceManager.runAsync("GameManager warmup", () -> {
            try {
                BotLogger.info("STARTED BUILDING MANAGED GAMES");
                Map<String, PersistedManagedGame> persistedGames = loadPersistedManagedGames();
                DatabaseWarmupCheck databaseCheck = DatabaseWarmupCheck.fromSettings();
                Map<String, GameLoadResult.Corrupt> corruptGames = new ConcurrentHashMap<>();
                Map<String, Exception> failedGames = new ConcurrentHashMap<>();
                AtomicInteger builtFromDatabase = new AtomicInteger();
                AtomicInteger builtFromGameFiles = new AtomicInteger();
                try (ExecutorService executorService =
                        Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2)) {
                    gameNames.forEach(name -> executorService.submit(() -> {
                        try {
                            boolean fromDatabase = warmupManagedGame(name, persistedGames, corruptGames, databaseCheck);
                            (fromDatabase ? builtFromDatabase : builtFromGameFiles).incrementAndGet();
                        } catch (Exception e) {
                            failedGames.put(name, e);
                        }
                    }));
                }
                reportCorruptGamesFoundAtWarmup(corruptGames);
                reportGamesThatFailedWarmup(failedGames);
                if (persistedGames != null) {
                    databaseCheck.report();
                }
                warmupFinishedLatch.countDown();
                BotLogger.info(String.format(
                        "FINISHED BUILDING MANAGED GAMES: %,d FROM THE DATABASE, %,d FROM GAME FILES",
                        builtFromDatabase.get(), builtFromGameFiles.get()));
                if (JdaService.jda != null) {
                    JdaService.updatePresence();
                }
            } catch (Exception e) {
                BotLogger.critical("Failed during GameManager warmup. Shutting down.", e);
                JdaService.shutdown();
            }
        });
    }

    @Nullable
    private static Map<String, PersistedManagedGame> loadPersistedManagedGames() {
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

    static boolean warmupManagedGame(
            String gameName,
            @Nullable Map<String, PersistedManagedGame> persistedGames,
            Map<String, GameLoadResult.Corrupt> corruptGames,
            DatabaseWarmupCheck databaseCheck) {
        PersistedManagedGame persistedGame = persistedGames == null ? null : persistedGames.get(gameName);
        if (persistedGame != null
                && databaseCheck.readsEnabled()
                && buildManagedGameFromCurrentRow(gameName, persistedGame)) {
            return true;
        }
        switch (buildManagedGameFromGameFile(gameName)) {
            case GameLoadResult.Loaded(Game game) -> {
                if (persistedGames != null
                        && !databaseCheck.rowMatchesFile(gameName, persistedGame, ManagedGameState.of(game))) {
                    GameDatabaseSyncPipeline.queueSyncFromGameFile(gameName);
                }
            }
            case GameLoadResult.Corrupt corrupt -> corruptGames.put(gameName, corrupt);
            case GameLoadResult.Missing() -> {}
        }
        return false;
    }

    private static boolean buildManagedGameFromCurrentRow(String gameName, PersistedManagedGame persistedGame) {
        return GameFileLockManager.wrapWithReadLock(gameName, () -> {
            if (!persistedGame.matchesRevision(readRevisionKeyOrNull(gameName))) return false;
            gameNameToManagedGame.computeIfAbsent(gameName, _ -> new ManagedGame(persistedGame.state()));
            return true;
        });
    }

    @Nullable
    private static String readRevisionKeyOrNull(String gameName) {
        try {
            return GameRevisionKeyFile.read(gameName);
        } catch (UncheckedIOException e) {
            return null;
        }
    }

    private static GameLoadResult buildManagedGameFromGameFile(String gameName) {
        return GameFileLockManager.wrapWithReadLock(gameName, () -> {
            GameLoadResult loadResult = GameLoadService.tryLoad(gameName);
            if (loadResult instanceof GameLoadResult.Loaded(Game game)) {
                gameNameToManagedGame.computeIfAbsent(gameName, _ -> new ManagedGame(game));
            }
            return loadResult;
        });
    }

    private static void reportCorruptGamesFoundAtWarmup(Map<String, GameLoadResult.Corrupt> corruptGames) {
        if (corruptGames.isEmpty()) return;
        corruptGames.forEach(GameManager::markCorruptVersionReported);
        List<String> corruptGameNames = corruptGames.keySet().stream().sorted().toList();
        String firstCorruptGameName = corruptGameNames.getFirst();
        BotLogger.error(
                "Could not load " + StringHelper.pluralize(corruptGameNames.size(), "corrupt game file")
                        + " at startup: " + String.join(", ", corruptGameNames)
                        + ". A bothelper can run `/bothelper reload_game` on each to restore it from its latest undo."
                        + " The stack trace is from " + firstCorruptGameName + ".",
                corruptGames.get(firstCorruptGameName).cause());
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

    @Nullable
    public static Game loadGameFile(String gameName) {
        return GameFileLockManager.wrapWithReadLock(gameName, () -> switch (GameLoadService.tryLoad(gameName)) {
            case GameLoadResult.Loaded(Game game) -> game;
            case GameLoadResult.Corrupt corrupt -> {
                reportCorruptGame(gameName, corrupt);
                evictManagedGame(gameName);
                yield null;
            }
            case GameLoadResult.Missing() -> null;
        });
    }

    @Nullable
    static Game get(String gameName) {
        ManagedGame examinedManagedGame = gameNameToManagedGame.get(gameName);
        return switch (GameLoadService.tryLoad(gameName)) {
            case GameLoadResult.Loaded(Game game) -> game;
            case GameLoadResult.Corrupt corrupt -> {
                reportCorruptGame(gameName, corrupt);
                evictManagedGameIfUnchanged(gameName, examinedManagedGame);
                yield null;
            }
            case GameLoadResult.Missing() -> {
                handleManagedGameRemoval(gameName);
                yield null;
            }
        };
    }

    private static void reportCorruptGame(String gameName, GameLoadResult.Corrupt corrupt) {
        if (markCorruptVersionReported(gameName, corrupt)) {
            BotLogger.error(corruptGameMessage(gameName), corrupt.cause());
        }
    }

    private static boolean markCorruptVersionReported(String gameName, GameLoadResult.Corrupt corrupt) {
        Long previouslyReportedVersion = reportedCorruptFileVersions.put(gameName, corrupt.fileLastModified());
        return !Objects.equals(previouslyReportedVersion, corrupt.fileLastModified());
    }

    private static String corruptGameMessage(String gameName) {
        return "The game file for " + gameName + " is corrupt and could not be loaded."
                + " A bothelper can run `/bothelper reload_game` to restore it from its latest undo.";
    }

    private static void handleManagedGameRemoval(String gameName) {
        gameNames.remove(gameName);
        evictManagedGame(gameName);
        LoreService.evictGameLore(gameName);
        GameDatabaseSyncPipeline.queueDelete(gameName);
    }

    private static void evictManagedGameIfUnchanged(String gameName, ManagedGame examinedManagedGame) {
        if (examinedManagedGame == null) return;
        gameNameToManagedGame.computeIfPresent(gameName, (_, managedGame) -> {
            if (managedGame != examinedManagedGame) return managedGame;
            managedGame.getPlayers().forEach(player -> player.removeGame(gameName));
            return null;
        });
    }

    private static void evictManagedGame(String gameName) {
        gameNameToManagedGame.computeIfPresent(gameName, (_, managedGame) -> {
            managedGame.getPlayers().forEach(player -> player.removeGame(gameName));
            return null;
        });
    }

    public static boolean isValid(String gameName) {
        waitFor(gameNamesLoadedLatch);
        return gameName != null && gameNames.contains(gameName);
    }

    public static void save(Game game, String reason) {
        waitFor(gameNamesLoadedLatch);
        boolean wasActive = Optional.ofNullable(gameNameToManagedGame.get(game.getName()))
                .map(ManagedGame::isActive)
                .orElse(false);
        ManagedGameState managedGameState = saveAndQueueDatabaseSync(game, reason);
        if (managedGameState == null) {
            throw new RuntimeException("Failed to save game " + game.getName() + ".");
        }
        GameWebStatePipeline.queue(game);

        boolean isActive = Optional.ofNullable(gameNameToManagedGame.get(game.getName()))
                .map(ManagedGame::isActive)
                .orElse(false);
        if (wasActive != isActive) {
            JdaService.updatePresence();
        }
    }

    @Nullable
    private static ManagedGameState saveAndQueueDatabaseSync(Game game, String reason) {
        return GameFileLockManager.wrapWithWriteLock(game.getName(), () -> {
            if (!GameSaveService.save(game, reason)) {
                return null;
            }
            ManagedGameState managedGameState = ManagedGameState.of(game);
            GameDatabaseSyncPipeline.queueSync(game, managedGameState);
            gameNames.add(game.getName());
            replaceManagedGame(managedGameState);
            return managedGameState;
        });
    }

    public static boolean delete(String gameName) {
        waitFor(warmupFinishedLatch);
        return GameFileLockManager.wrapWithWriteLock(gameName, () -> {
            if (!GameSaveService.delete(gameName)) {
                return false;
            }
            handleManagedGameRemoval(gameName);
            return true;
        });
    }

    @Nullable
    public static Game undo(Game game) {
        waitFor(gameNamesLoadedLatch);
        return GameFileLockManager.wrapWithWriteLock(game.getName(), () -> handleUndo(GameUndoService.undo(game)));
    }

    private static Game handleUndo(Game undo) {
        if (undo == null) return null;
        ManagedGameState managedGameState = ManagedGameState.of(undo);
        handleMissingMatchingManagedGame(undo, managedGameState);
        GameDatabaseSyncPipeline.queueSync(undo, managedGameState);
        return undo;
    }

    private static void handleMissingMatchingManagedGame(Game game, ManagedGameState managedGameState) {
        var managedGame = gameNameToManagedGame.get(game.getName());
        if (managedGame == null || !managedGame.matches(game)) {
            gameNames.add(game.getName());
            replaceManagedGame(managedGameState);
        }
    }

    private static void replaceManagedGame(ManagedGameState managedGameState) {
        gameNameToManagedGame.compute(managedGameState.name(), (_, previousManagedGame) -> {
            ManagedGame managedGame = new ManagedGame(managedGameState);
            if (previousManagedGame != null) {
                removeGameFromDepartedPlayers(previousManagedGame, managedGame);
            }
            return managedGame;
        });
    }

    private static void removeGameFromDepartedPlayers(ManagedGame previousManagedGame, ManagedGame managedGame) {
        previousManagedGame.getPlayers().stream()
                .filter(player -> !managedGame.hasPlayer(player.getId()))
                .forEach(player -> player.removeGame(managedGame.getName()));
    }

    @Nullable
    public static Game undo(Game game, int undoIndex) {
        waitFor(gameNamesLoadedLatch);
        return GameFileLockManager.wrapWithWriteLock(
                game.getName(), () -> handleUndo(GameUndoService.undo(game, undoIndex)));
    }

    @Nullable
    public static Game reload(String gameName) {
        waitFor(gameNamesLoadedLatch);
        return GameFileLockManager.wrapWithWriteLock(gameName, () -> reloadGameFile(gameName));
    }

    @Nullable
    private static Game reloadGameFile(String gameName) {
        GameLoadResult loadResult = GameLoadService.tryLoad(gameName);
        Game game = loadResult instanceof GameLoadResult.Loaded(Game loadedGame)
                ? loadedGame
                : GameUndoService.loadUndoForMissingGame(gameName);
        if (game == null) {
            if (loadResult instanceof GameLoadResult.Missing) {
                handleManagedGameRemoval(gameName);
            }
            return null;
        }
        ManagedGameState managedGameState = ManagedGameState.of(game);
        handleMissingMatchingManagedGame(game, managedGameState);
        GameDatabaseSyncPipeline.queueSync(game, managedGameState);
        return game;
    }

    public static List<String> getGameNames() {
        waitFor(gameNamesLoadedLatch);
        return List.copyOf(gameNames);
    }

    public static int getGameCount() {
        waitFor(gameNamesLoadedLatch);
        return gameNames.size();
    }

    public static long getActiveGameCount() {
        waitFor(gameNamesLoadedLatch);
        return gameNameToManagedGame.values().stream()
                .filter(ManagedGame::isActive)
                .count();
    }

    @Nullable
    public static String readRevisionKey(String gameName) {
        return GameRevisionKeyFile.read(gameName);
    }

    @Nullable
    public static String backfillRevisionKey(String gameName) {
        return GameRevisionKeyFile.backfill(gameName);
    }

    @Nullable
    public static ManagedGame getManagedGame(String gameName) {
        if (!isValid(gameName)) return null;
        ManagedGame managedGame = gameNameToManagedGame.get(gameName);
        if (managedGame != null) return managedGame;
        return GameFileLockManager.wrapWithReadLock(gameName, () -> {
            Game game = loadForManagedGame(gameName);
            return gameNameToManagedGame.computeIfAbsent(gameName, _ -> new ManagedGame(game));
        });
    }

    @Nullable
    public static ManagedGame getLoadableManagedGame(String gameName) {
        if (!isValid(gameName)) return null;
        waitFor(gameNamesLoadedLatch);
        return gameNameToManagedGame.computeIfAbsent(gameName, _ -> loadManagedGameIfReadable(gameName));
    }

    @Nullable
    private static ManagedGame loadManagedGameIfReadable(String gameName) {
        return switch (GameLoadService.tryLoad(gameName)) {
            case GameLoadResult.Loaded(Game game) -> new ManagedGame(game);
            case GameLoadResult.Corrupt corrupt -> {
                reportCorruptGame(gameName, corrupt);
                yield null;
            }
            case GameLoadResult.Missing() -> null;
        };
    }

    private static Game loadForManagedGame(String gameName) {
        return switch (GameLoadService.tryLoad(gameName)) {
            case GameLoadResult.Loaded(Game game) -> game;
            case GameLoadResult.Corrupt corrupt -> {
                reportCorruptGame(gameName, corrupt);
                throw new IllegalStateException(corruptGameMessage(gameName));
            }
            case GameLoadResult.Missing() -> {
                BotLogger.error("Failed to load ManagedGame for " + gameName + ".");
                throw new IllegalStateException("Failed to load ManagedGame for " + gameName);
            }
        };
    }

    public static boolean isCorrupt(String gameName) {
        return GameLoadService.tryLoad(gameName) instanceof GameLoadResult.Corrupt;
    }

    public static boolean isWarmupFinished() {
        return warmupFinishedLatch.getCount() == 0;
    }

    public static List<ManagedGame> getManagedGames() {
        waitFor(warmupFinishedLatch);
        return List.copyOf(gameNameToManagedGame.values());
    }

    public static ManagedPlayer getManagedPlayer(String playerId) {
        waitFor(warmupFinishedLatch);
        return userIdToManagedPlayer.get(playerId);
    }

    public static Set<ManagedPlayer> getManagedPlayers() {
        waitFor(warmupFinishedLatch);
        return Set.copyOf(userIdToManagedPlayer.values());
    }

    static ManagedPlayer addOrMergePlayer(ManagedGame game, ManagedGameState.Participant participant) {
        return userIdToManagedPlayer.compute(participant.userId(), (_, existing) -> {
            if (existing == null) {
                return new ManagedPlayer(game, participant.userId(), participant.userName());
            }
            existing.addOrReplaceGame(game, participant.userId());
            return existing;
        });
    }

    private static void waitFor(CountDownLatch latch) {
        if (latch.getCount() == 0) return;

        try {
            boolean success = latch.await(WAIT_FOR_WARMUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!success) {
                throw new IllegalStateException("Failed to wait for warmup.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to wait for warmup.", e);
        }
    }

    public static int getLatestPbdNumber() {
        waitFor(gameNamesLoadedLatch);
        return latestPbdNumber.get();
    }

    public static int getAndIncrementLatestPbdNumber() {
        waitFor(gameNamesLoadedLatch);
        return latestPbdNumber.incrementAndGet();
    }

    public static void resetLatestPbdNumberFrom(int toResetFrom) {
        int maxNumber = gameNames.stream()
                .filter(gameName -> gameName.startsWith("pbd"))
                .map(gameName -> gameName.replace("pbd", ""))
                .filter(StringUtils::isNumeric)
                .map(Integer::parseInt)
                .max(Integer::compare)
                .orElse(0);
        latestPbdNumber.compareAndSet(toResetFrom, maxNumber);
    }
}
