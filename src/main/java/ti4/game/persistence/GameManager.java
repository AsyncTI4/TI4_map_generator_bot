package ti4.game.persistence;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
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
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.helpers.StringHelper;
import ti4.logging.BotLogger;
import ti4.service.fow.LoreService;
import ti4.spring.service.persistence.PersistedManagedGameService;
import ti4.spring.websocket.GameWebStatePipeline;

@UtilityClass
public class GameManager {

    // TODO: We can evaluate dropping the managed objects entirely
    private static final Set<String> gameNames = ConcurrentHashMap.newKeySet();
    private static final ConcurrentMap<String, ManagedGame> gameNameToManagedGame = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, ManagedPlayer> userIdToManagedPlayer = new ConcurrentHashMap<>();
    private static final AtomicInteger latestPbdNumber = new AtomicInteger();

    private static final CountDownLatch gameNamesLoadedLatch = new CountDownLatch(1);
    private static final AtomicBoolean warmupStarted = new AtomicBoolean(false);
    private static final CountDownLatch warmupFinishedLatch = new CountDownLatch(1);
    private static final int WAIT_FOR_WARMUP_TIMEOUT_SECONDS = 40;

    public static void preloadManagedGames() {
        ManagedGamePreload.start();
    }

    public static void warmup() {
        if (!warmupStarted.compareAndSet(false, true)) {
            return;
        }
        long warmupStartedAt = System.currentTimeMillis();

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
                WarmupSources sources = warmupSources();
                Map<String, Exception> failedGames = new ConcurrentHashMap<>();
                AtomicInteger builtFromPreload = new AtomicInteger();
                AtomicInteger builtFromDatabase = new AtomicInteger();
                AtomicInteger builtFromGameFiles = new AtomicInteger();
                try (ExecutorService executorService =
                        Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2)) {
                    gameNames.forEach(name -> executorService.submit(() -> {
                        try {
                            if (sources.preloadedNames().contains(name)) {
                                buildManagedGameFromPreload(
                                        sources.persistedGames().get(name));
                                builtFromPreload.incrementAndGet();
                                return;
                            }
                            boolean fromDatabase = warmupManagedGame(name, sources.persistedGames());
                            (fromDatabase ? builtFromDatabase : builtFromGameFiles).incrementAndGet();
                        } catch (Exception e) {
                            failedGames.put(name, e);
                        }
                    }));
                }
                reportGamesThatFailedWarmup(failedGames);
                warmupFinishedLatch.countDown();
                BotLogger.info(String.format(
                        "MANAGED GAMES UNBLOCKED %,d MS AFTER WARMUP STARTED (%,d MS WAITING FOR THE STANDBY PRELOAD): %,d FROM THE STANDBY PRELOAD, %,d FROM THE DATABASE, %,d FROM GAME FILES",
                        System.currentTimeMillis() - warmupStartedAt,
                        sources.preloadWaitMillis(),
                        builtFromPreload.get(),
                        builtFromDatabase.get(),
                        builtFromGameFiles.get()));
                if (JdaService.jda != null) {
                    JdaService.updatePresence();
                }
            } catch (Exception e) {
                BotLogger.critical("Failed during GameManager warmup. Shutting down.", e);
                JdaService.shutdown();
            }
        });
    }

    private record WarmupSources(
            @Nullable Map<String, ManagedGameState> persistedGames,
            Set<String> preloadedNames,
            long preloadWaitMillis) {}

    private static WarmupSources warmupSources() {
        long waitStartedAt = System.currentTimeMillis();
        ManagedGamePreload.Snapshot preload = ManagedGamePreload.take();
        long preloadWaitMillis = System.currentTimeMillis() - waitStartedAt;
        if (preload == null) {
            return new WarmupSources(loadPersistedManagedGames(), Set.of(), preloadWaitMillis);
        }
        return new WarmupSources(preload.rows(), preload.stillCurrent(loadCurrentRevisionKeys()), preloadWaitMillis);
    }

    private static Map<String, String> loadCurrentRevisionKeys() {
        try {
            return PersistedManagedGameService.getBean().loadRevisionKeys();
        } catch (Exception e) {
            BotLogger.error("Could not read current revision keys, so the standby preload will be rechecked.", e);
            return Map.of();
        }
    }

    private static void buildManagedGameFromPreload(ManagedGameState preloadedGame) {
        gameNameToManagedGame.computeIfAbsent(preloadedGame.name(), _ -> new ManagedGame(preloadedGame));
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
        gameNameToManagedGame.computeIfAbsent(gameName, _ -> new ManagedGame(persistedGame));
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
            gameNameToManagedGame.computeIfAbsent(gameName, _ -> new ManagedGame(game));
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

    @Nullable
    static Game get(String gameName) {
        ManagedGame cachedManagedGame = gameNameToManagedGame.get(gameName);
        Game game = GameLoadService.load(gameName);
        if (game != null) return game;
        if (gameFileExists(gameName)) {
            handleCorruptGame(gameName, cachedManagedGame);
        } else {
            handleMissingGame(gameName);
        }
        return null;
    }

    public static boolean isCorrupt(String gameName) {
        return GameLoadService.isKnownUnreadable(gameName);
    }

    private static boolean gameFileExists(String gameName) {
        return Storage.getGameFile(gameName + Constants.TXT).exists();
    }

    private static void handleCorruptGame(String gameName, ManagedGame managedGame) {
        if (managedGame == null) return;
        gameNameToManagedGame.computeIfPresent(gameName, (_, current) -> {
            if (current != managedGame) return current;
            managedGame.getPlayers().forEach(player -> player.removeGame(gameName));
            return null;
        });
    }

    private static void handleMissingGame(String gameName) {
        unregisterGameAndQueueDatabaseDelete(gameName);
    }

    private static void unregisterGameAndQueueDatabaseDelete(String gameName) {
        gameNames.remove(gameName);
        var managedGame = gameNameToManagedGame.remove(gameName);
        if (managedGame != null) {
            managedGame.getPlayers().forEach(player -> player.removeGame(gameName));
        }
        LoreService.evictGameLore(gameName);
        GameDatabaseSyncPipeline.queueDelete(gameName);
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
        if (!GameSaveService.save(game, reason)) {
            throw new RuntimeException("Failed to save game " + game.getName() + ".");
        }
        GameWebStatePipeline.queue(game);
        ManagedGameState managedGameState = ManagedGameState.of(game);
        GameDatabaseSyncPipeline.queueSync(game, managedGameState);

        gameNames.add(game.getName());
        replaceManagedGame(managedGameState);

        boolean isActive = Optional.ofNullable(gameNameToManagedGame.get(game.getName()))
                .map(ManagedGame::isActive)
                .orElse(false);
        if (wasActive != isActive) {
            JdaService.updatePresence();
        }
    }

    public static boolean delete(String gameName) {
        waitFor(warmupFinishedLatch);
        if (!GameSaveService.delete(gameName)) {
            return false;
        }
        unregisterGameAndQueueDatabaseDelete(gameName);
        return true;
    }

    @Nullable
    public static Game undo(Game game) {
        waitFor(gameNamesLoadedLatch);
        Game undo = GameUndoService.undo(game);
        return handleUndo(undo);
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

    static void replaceManagedGame(ManagedGameState managedGameState) {
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
        Game undo = GameUndoService.undo(game, undoIndex);
        return handleUndo(undo);
    }

    @Nullable
    public static Game reload(String gameName) {
        waitFor(gameNamesLoadedLatch);
        ManagedGame cachedManagedGame = gameNameToManagedGame.get(gameName);
        Game game = GameLoadService.load(gameName);
        if (game == null) {
            game = GameUndoService.loadUndoForMissingGame(gameName);
        }
        if (game == null) {
            if (gameFileExists(gameName)) {
                handleCorruptGame(gameName, cachedManagedGame);
            } else {
                handleMissingGame(gameName);
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
    public static ManagedGame getManagedGame(String gameName) {
        if (!isValid(gameName)) return null;
        waitFor(gameNamesLoadedLatch);
        return gameNameToManagedGame.computeIfAbsent(gameName, _ -> {
            Game game = GameLoadService.load(gameName);
            if (game == null) {
                BotLogger.error("Failed to load ManagedGame for " + gameName + ".");
                throw new IllegalStateException("Failed to load ManagedGame for " + gameName);
            }
            return new ManagedGame(game);
        });
    }

    @Nullable
    static ManagedGame peekManagedGame(String gameName) {
        return gameNameToManagedGame.get(gameName);
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
