package ti4.game.persistence;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.discord.JdaService;
import ti4.executors.ExecutorServiceManager;
import ti4.game.Game;
import ti4.game.Player;
import ti4.logging.BotLogger;
import ti4.spring.context.SpringContext;
import ti4.spring.service.deploy.ActiveLeaseService;

@UtilityClass
public class GameManager {

    // TODO: We can evaluate dropping the managed objects entirely
    private static final Set<String> gameNames = ConcurrentHashMap.newKeySet();
    private static final ConcurrentMap<String, ManagedGame> gameNameToManagedGame = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, ManagedPlayer> userIdToManagedPlayer = new ConcurrentHashMap<>();

    private static final CountDownLatch GAME_NAMES_LOADED_LATCH = new CountDownLatch(1);
    private static final AtomicBoolean WARMUP_STARTED = new AtomicBoolean(false);
    private static final CountDownLatch WARMUP_FINISHED_LATCH = new CountDownLatch(1);
    private static final int WAIT_FOR_WARMUP_TIMEOUT_SECONDS = 10;

    public static void warmup() {
        if (!currentInstanceOwnsLeaseForWarmup() || !WARMUP_STARTED.compareAndSet(false, true)) {
            return;
        }

        BotLogger.info("LOADING GAME NAMES");
        gameNames.addAll(GameLoadService.loadGameNames());
        GAME_NAMES_LOADED_LATCH.countDown();

        ExecutorServiceManager.runAsync("GameManager warmup", () -> {
            try {
                BotLogger.info("STARTED BUILDING MANAGED GAMES");
                gameNames.forEach(GameManager::getManagedGame);
                WARMUP_FINISHED_LATCH.countDown();
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

    private static boolean currentInstanceOwnsLeaseForWarmup() {
        try {
            return SpringContext.getBean(ActiveLeaseService.class).mayMutate();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private static Game load(String gameName) {
        Game game = GameLoadService.load(gameName);
        if (game == null) {
            return null;
        }

        if (doesNotHaveMatchingManagedGame(game)) {
            gameNameToManagedGame.put(game.getName(), new ManagedGame(game));
        }
        return game;
    }

    private static boolean doesNotHaveMatchingManagedGame(Game game) {
        var managedGame = gameNameToManagedGame.get(game.getName());
        return managedGame == null || !managedGame.matches(game);
    }

    @Nullable
    static Game get(String gameName) {
        if (!isValid(gameName)) {
            return null;
        }
        Game game = load(gameName);
        if (game == null) {
            handleManagedGameRemoval(gameName);
        }
        return game;
    }

    private static void handleManagedGameRemoval(String gameName) {
        var managedGame = gameNameToManagedGame.remove(gameName);
        if (managedGame != null) {
            managedGame.getPlayers().forEach(player -> player.getGames().remove(managedGame));
        }
    }

    public static boolean isValid(String gameName) {
        waitFor(GAME_NAMES_LOADED_LATCH);
        return gameName != null && gameNames.contains(gameName);
    }

    public static void save(Game game, String reason) {
        waitFor(GAME_NAMES_LOADED_LATCH);
        boolean wasActive = Optional.ofNullable(gameNameToManagedGame.get(game.getName()))
                .map(ManagedGame::isActive)
                .orElse(false);
        if (!GameSaveService.save(game, reason)) {
            throw new RuntimeException("Failed to save game " + game.getName() + ".");
        }

        gameNameToManagedGame.put(game.getName(), new ManagedGame(game));

        boolean isActive = Optional.ofNullable(gameNameToManagedGame.get(game.getName()))
                .map(ManagedGame::isActive)
                .orElse(false);
        if (wasActive != isActive) {
            JdaService.updatePresence();
        }
    }

    public static boolean delete(String gameName) {
        waitFor(WARMUP_FINISHED_LATCH);
        if (!GameSaveService.delete(gameName)) {
            return false;
        }
        handleManagedGameRemoval(gameName);
        return true;
    }

    @Nullable
    public static Game undo(Game game) {
        waitFor(GAME_NAMES_LOADED_LATCH);
        Game undo = GameUndoService.undo(game);
        return handleUndo(undo);
    }

    private static Game handleUndo(Game undo) {
        if (undo == null) {
            return null;
        }
        if (doesNotHaveMatchingManagedGame(undo)) {
            gameNames.add(undo.getName());
            gameNameToManagedGame.put(undo.getName(), new ManagedGame(undo));
        }
        return undo;
    }

    @Nullable
    public static Game undo(Game game, int undoIndex) {
        waitFor(GAME_NAMES_LOADED_LATCH);
        Game undo = GameUndoService.undo(game, undoIndex);
        return handleUndo(undo);
    }

    @Nullable
    public static Game reload(String gameName) {
        waitFor(GAME_NAMES_LOADED_LATCH);
        Game game = load(gameName);
        if (game == null) {
            game = GameUndoService.loadUndoForMissingGame(gameName);
            handleUndo(game);
        }
        return game;
    }

    public static List<String> getGameNames() {
        waitFor(GAME_NAMES_LOADED_LATCH);
        return new ArrayList<>(gameNames);
    }

    public static int getGameCount() {
        waitFor(GAME_NAMES_LOADED_LATCH);
        return gameNames.size();
    }

    public static long getActiveGameCount() {
        waitFor(GAME_NAMES_LOADED_LATCH);
        return gameNameToManagedGame.values().stream()
                .filter(ManagedGame::isActive)
                .count();
    }

    public static ManagedGame getManagedGame(String gameName) {
        waitFor(GAME_NAMES_LOADED_LATCH);
        if (!isValid(gameName)) {
            return null;
        }
        return gameNameToManagedGame.computeIfAbsent(gameName, GameLoadService::loadManagedGame);
    }

    public static List<ManagedGame> getManagedGames() {
        waitFor(WARMUP_FINISHED_LATCH);
        return new ArrayList<>(gameNameToManagedGame.values());
    }

    public static ManagedPlayer getManagedPlayer(String playerId) {
        waitFor(WARMUP_FINISHED_LATCH);
        return userIdToManagedPlayer.get(playerId);
    }

    public static Set<ManagedPlayer> getManagedPlayers() {
        waitFor(WARMUP_FINISHED_LATCH);
        return new HashSet<>(userIdToManagedPlayer.values());
    }

    static ManagedPlayer addOrMergePlayer(ManagedGame game, Player player) {
        var managedPlayer = userIdToManagedPlayer.get(player.getUserID());
        if (managedPlayer == null) {
            managedPlayer = new ManagedPlayer(game, player);
            userIdToManagedPlayer.put(player.getUserID(), managedPlayer);
            return managedPlayer;
        }
        managedPlayer.addOrReplaceGame(game, player);
        return managedPlayer;
    }

    private static void waitFor(CountDownLatch latch) {
        try {
            boolean success = latch.await(WAIT_FOR_WARMUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!success) {
                throw new IllegalStateException("Failed to wait for warmup.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
