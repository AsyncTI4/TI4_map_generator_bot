package ti4.game.persistence;

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
import ti4.game.Player;
import ti4.helpers.StringHelper;
import ti4.logging.BotLogger;
import ti4.service.fow.LoreService;
import ti4.service.persistence.GameDatabaseSyncPipeline;
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
                Map<String, GameLoadResult.Corrupt> corruptGames = new ConcurrentHashMap<>();
                try (ExecutorService executorService =
                        Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2)) {
                    gameNames.forEach(name -> executorService.submit(() -> buildManagedGame(name, corruptGames)));
                }
                reportCorruptGamesFoundAtWarmup(corruptGames);
                warmupFinishedLatch.countDown();
                BotLogger.info("FINISHED BUILDING MANAGED GAMES");
                if (JdaService.jda != null) {
                    JdaService.updatePresence();
                }
            } catch (Exception e) {
                BotLogger.critical("Failed during GameManager warmup. Shutting down.", e);
                JdaService.shutdown();
            }
        });
    }

    private static void buildManagedGame(String gameName, Map<String, GameLoadResult.Corrupt> corruptGames) {
        switch (GameLoadService.tryLoad(gameName)) {
            case GameLoadResult.Loaded(Game game) ->
                gameNameToManagedGame.computeIfAbsent(gameName, _ -> new ManagedGame(game));
            case GameLoadResult.Corrupt corrupt -> corruptGames.put(gameName, corrupt);
            case GameLoadResult.Missing() -> {}
        }
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

    @Nullable
    static Game get(String gameName) {
        return switch (GameLoadService.tryLoad(gameName)) {
            case GameLoadResult.Loaded(Game game) -> game;
            case GameLoadResult.Corrupt corrupt -> {
                reportCorruptGame(gameName, corrupt);
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
        GameDatabaseSyncPipeline.queueSync(game);

        gameNames.add(game.getName());
        gameNameToManagedGame.put(game.getName(), new ManagedGame(game));

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
        handleManagedGameRemoval(gameName);
        return true;
    }

    @Nullable
    public static Game undo(Game game) {
        waitFor(gameNamesLoadedLatch);
        Game undo = GameUndoService.undo(game);
        return handleUndo(undo);
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
        Game undo = GameUndoService.undo(game, undoIndex);
        return handleUndo(undo);
    }

    @Nullable
    public static Game reload(String gameName) {
        waitFor(gameNamesLoadedLatch);
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
        waitFor(gameNamesLoadedLatch);
        return gameNameToManagedGame.computeIfAbsent(gameName, _ -> new ManagedGame(loadForManagedGame(gameName)));
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

    static ManagedPlayer addOrMergePlayer(ManagedGame game, Player player) {
        return userIdToManagedPlayer.compute(player.getUserID(), (_, existing) -> {
            if (existing == null) {
                return new ManagedPlayer(game, player);
            }
            existing.addOrReplaceGame(game, player);
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
