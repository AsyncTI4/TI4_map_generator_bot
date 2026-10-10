package ti4.game.persistence;

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
                AtomicInteger builtFromDatabase = new AtomicInteger();
                try (ExecutorService executorService =
                        Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2)) {
                    gameNames.forEach(name -> executorService.submit(() -> {
                        if (warmupManagedGame(name, persistedGames)) {
                            builtFromDatabase.incrementAndGet();
                        }
                    }));
                }
                warmupFinishedLatch.countDown();
                BotLogger.info(String.format(
                        "FINISHED BUILDING MANAGED GAMES: %,d FROM THE DATABASE, %,d FROM GAME FILES",
                        builtFromDatabase.get(), gameNames.size() - builtFromDatabase.get()));
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

    static boolean warmupManagedGame(String gameName, @Nullable Map<String, PersistedManagedGame> persistedGames) {
        if (persistedGames == null) {
            buildManagedGameFromGameFile(gameName, loadGameOrThrow(gameName));
            return false;
        }
        PersistedManagedGame persistedGame = persistedGames.get(gameName);
        if (persistedGame != null && persistedGame.matchesGameFile(GameLoadService.getGameFileLastModified(gameName))) {
            gameNameToManagedGame.computeIfAbsent(gameName, _ -> new ManagedGame(persistedGame.state()));
            return true;
        }
        buildManagedGameFromGameFile(gameName, loadGameAndQueueDatabaseSync(gameName));
        return false;
    }

    private static Game loadGameAndQueueDatabaseSync(String gameName) {
        return GameFileLockManager.wrapWithReadLock(gameName, () -> {
            Game game = loadGameOrThrow(gameName);
            GameDatabaseSyncPipeline.queueSync(game);
            return game;
        });
    }

    private static ManagedGame buildManagedGameFromGameFile(String gameName, Game game) {
        return gameNameToManagedGame.computeIfAbsent(gameName, _ -> new ManagedGame(game));
    }

    private static Game loadGameOrThrow(String gameName) {
        Game game = GameLoadService.load(gameName);
        if (game == null) {
            BotLogger.error("Failed to load ManagedGame for " + gameName + ".");
            throw new IllegalStateException("Failed to load ManagedGame for " + gameName);
        }
        return game;
    }

    public static long getGameFileLastModified(String gameName) {
        return GameLoadService.getGameFileLastModified(gameName);
    }

    @Nullable
    static Game get(String gameName) {
        Game game = GameLoadService.load(gameName);
        if (game == null) {
            handleManagedGameRemoval(gameName);
        }
        return game;
    }

    private static void handleManagedGameRemoval(String gameName) {
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
        if (!saveAndQueueDatabaseSync(game, reason)) {
            throw new RuntimeException("Failed to save game " + game.getName() + ".");
        }
        GameWebStatePipeline.queue(game);

        gameNames.add(game.getName());
        gameNameToManagedGame.put(game.getName(), new ManagedGame(game));

        boolean isActive = Optional.ofNullable(gameNameToManagedGame.get(game.getName()))
                .map(ManagedGame::isActive)
                .orElse(false);
        if (wasActive != isActive) {
            JdaService.updatePresence();
        }
    }

    private static boolean saveAndQueueDatabaseSync(Game game, String reason) {
        return GameFileLockManager.wrapWithWriteLock(game.getName(), () -> {
            if (!GameSaveService.save(game, reason)) {
                return false;
            }
            GameDatabaseSyncPipeline.queueSync(game);
            return true;
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
        handleMissingMatchingManagedGame(undo);
        GameDatabaseSyncPipeline.queueSync(undo);
        return undo;
    }

    private static void handleMissingMatchingManagedGame(Game game) {
        if (game == null) return;
        var managedGame = gameNameToManagedGame.get(game.getName());
        if (managedGame == null || !managedGame.matches(game)) {
            gameNames.add(game.getName());
            gameNameToManagedGame.put(game.getName(), new ManagedGame(game));
        }
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
        Game game = GameLoadService.load(gameName);
        if (game == null) {
            game = GameUndoService.loadUndoForMissingGame(gameName);
        }
        if (game == null) {
            handleManagedGameRemoval(gameName);
            return null;
        }
        handleMissingMatchingManagedGame(game);
        GameDatabaseSyncPipeline.queueSync(game);
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
        ManagedGame managedGame = gameNameToManagedGame.get(gameName);
        if (managedGame != null) return managedGame;
        return buildManagedGameFromGameFile(gameName, loadGameOrThrow(gameName));
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
