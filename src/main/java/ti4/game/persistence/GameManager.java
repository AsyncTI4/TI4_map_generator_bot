package ti4.game.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.JdaService;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.logging.BotLogger;
import ti4.service.fow.LoreService;
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

    public static void buildPreloadedManagedGames() {
        ManagedGamePreload.buildManagedGames();
    }

    public static void warmup() {
        if (!warmupStarted.compareAndSet(false, true)) return;
        ManagedGameWarmup.start();
    }

    static void registerGameNames(Collection<String> loadedGameNames) {
        gameNames.addAll(loadedGameNames);
        resetLatestPbdNumberFrom(0);
        gameNamesLoadedLatch.countDown();
    }

    static void finishWarmup() {
        warmupFinishedLatch.countDown();
    }

    static ManagedGame buildManagedGameIfAbsent(String gameName, Supplier<ManagedGame> newManagedGame) {
        return gameNameToManagedGame.computeIfAbsent(gameName, _ -> newManagedGame.get());
    }

    static void discardManagedGame(String gameName, ManagedGame managedGame) {
        ManagedGame remaining = gameNameToManagedGame.computeIfPresent(
                gameName, (_, current) -> current == managedGame ? null : current);
        if (remaining != null) return;
        managedGame.getPlayers().forEach(player -> player.removeGame(gameName, managedGame));
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
        // TODO: Queued after the file write lock is released, so two concurrent saves of one game can reach the
        // database out of order and leave the older state until the next save or the nightly reconciliation.
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
