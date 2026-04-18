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
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.discord.JdaService;
import ti4.executors.ExecutorServiceManager;
import ti4.game.Game;
import ti4.game.Player;
import ti4.logging.BotLogger;

@UtilityClass
public class GameManager {

    private static final Set<String> validGameNames = ConcurrentHashMap.newKeySet();
    // TODO: We can evaluate dropping the managed objects entirely
    private static final ConcurrentMap<String, ManagedGame> gameNameToManagedGame = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, ManagedPlayer> userIdToManagedPlayer = new ConcurrentHashMap<>();
    private static final CountDownLatch WARMUP_LATCH = new CountDownLatch(1);
    private static final long WAIT_FOR_WARMUP_TIMEOUT_SECONDS = 30;

    public static void initialize() {
        validGameNames.addAll(GameLoadService.loadManagedGameNames());
        ExecutorServiceManager.runAsync("GameManager managed game warmup", () -> {
            try {
                validGameNames.forEach(GameManager::getManagedGame);
                WARMUP_LATCH.countDown();
            } catch (Exception e) {
                BotLogger.critical("Failed during managed game warmup.", e);
            } finally {
                if (JdaService.jda != null) {
                    JdaService.updatePresence();
                }
            }
        });
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
        validGameNames.remove(gameName);
        var managedGame = gameNameToManagedGame.remove(gameName);
        if (managedGame != null) {
            managedGame.getPlayers().forEach(player -> player.getGames().remove(managedGame));
        }
    }

    public static boolean isValid(String gameName) {
        return gameName != null && validGameNames.contains(gameName);
    }

    public static boolean save(Game game, String reason) {
        boolean wasActive = Optional.ofNullable(gameNameToManagedGame.get(game.getName()))
                .map(ManagedGame::isActive)
                .orElse(false);
        if (!GameSaveService.save(game, reason)) {
            return false;
        }
        validGameNames.add(game.getName());
        gameNameToManagedGame.put(game.getName(), new ManagedGame(game));

        boolean isActive = Optional.ofNullable(gameNameToManagedGame.get(game.getName()))
                .map(ManagedGame::isActive)
                .orElse(false);
        if (wasActive != isActive) {
            JdaService.updatePresence();
        }
        return true;
    }

    public static boolean delete(String gameName) {
        if (!GameSaveService.delete(gameName)) {
            return false;
        }
        handleManagedGameRemoval(gameName);
        return true;
    }

    @Nullable
    public static Game undo(Game game) {
        Game undo = GameUndoService.undo(game);
        return handleUndo(undo);
    }

    private static Game handleUndo(Game undo) {
        if (undo == null) {
            return null;
        }
        if (doesNotHaveMatchingManagedGame(undo)) {
            gameNameToManagedGame.put(undo.getName(), new ManagedGame(undo));
        }
        return undo;
    }

    @Nullable
    public static Game undo(Game game, int undoIndex) {
        Game undo = GameUndoService.undo(game, undoIndex);
        return handleUndo(undo);
    }

    @Nullable
    public static Game reload(String gameName) {
        Game game = load(gameName);
        if (game == null) {
            game = GameUndoService.loadUndoForMissingGame(gameName);
            handleUndo(game);
        }
        return game;
    }

    public static List<String> getGameNames() {
        return new ArrayList<>(validGameNames);
    }

    public static int getGameCount() {
        return validGameNames.size();
    }

    public static long getActiveGameCount() {
        return gameNameToManagedGame.values().stream()
                .filter(ManagedGame::isActive)
                .count();
    }

    public static ManagedGame getManagedGame(String gameName) {
        if (!isValid(gameName)) {
            return null;
        }
        return gameNameToManagedGame.computeIfAbsent(gameName, GameLoadService::loadManagedGame);
    }

    public static List<ManagedGame> getManagedGames() {
        ensureManagedGamesWarmupComplete();
        return new ArrayList<>(gameNameToManagedGame.values());
    }

    public static ManagedPlayer getManagedPlayer(String playerId) {
        ensureManagedGamesWarmupComplete();
        return userIdToManagedPlayer.get(playerId);
    }

    public static Set<ManagedPlayer> getManagedPlayers() {
        ensureManagedGamesWarmupComplete();
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

    public static boolean isWarmupComplete() {
        return WARMUP_LATCH.getCount() == 0;
    }

    private static void ensureManagedGamesWarmupComplete() {
        try {
            boolean success = WARMUP_LATCH.await(WAIT_FOR_WARMUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!success) {
                throw new IllegalStateException("GameManager is still warming up!");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
