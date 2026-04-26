package ti4.game.persistence;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import ti4.discord.JdaService;
import ti4.executors.ExecutorServiceManager;
import ti4.game.Game;
import ti4.game.Player;
import ti4.logging.BotLogger;

@UtilityClass
public class GameManager {

    // TODO: We can evaluate dropping the managed objects entirely
    private static final Set<String> gameNames = ConcurrentHashMap.newKeySet();
    private static final ConcurrentMap<String, ManagedGame> gameNameToManagedGame = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, ManagedPlayer> userIdToManagedPlayer = new ConcurrentHashMap<>();

    private static final CountDownLatch GAME_NAMES_LOADED_LATCH = new CountDownLatch(1);
    private static final AtomicBoolean WARMUP_STARTED = new AtomicBoolean(false);
    private static final CountDownLatch WARMUP_FINISHED_LATCH = new CountDownLatch(1);
    private static final int WAIT_FOR_WARMUP_TIMEOUT_SECONDS = 30;

    public static void warmup() {
        if (!WARMUP_STARTED.compareAndSet(false, true)) {
            return;
        }

        try {
            gameNames.addAll(GameLoadService.loadGameNames());
            GAME_NAMES_LOADED_LATCH.countDown();
            BotLogger.info("LOADED " + gameNames.size() + " GAME NAMES");
        } catch (Exception e) {
            BotLogger.critical("Failed to warmup due to error while loading game names. Shutting down.", e);
            JdaService.shutdown();
        }

        JdaService.jda.getPresence().setPresence(OnlineStatus.ONLINE, Activity.customStatus("Ready to play"));

        ExecutorServiceManager.runAsync("GameManager warmup", () -> {
            try {
                BotLogger.info("STARTED BUILDING MANAGED GAMES");
                try (ExecutorService executorService =
                        Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2)) {
                    gameNames.forEach(name -> executorService.submit(() -> getManagedGame(name)));
                }
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
        handleMissingMatchingManagedGame(undo);
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
        waitFor(GAME_NAMES_LOADED_LATCH);
        Game undo = GameUndoService.undo(game, undoIndex);
        return handleUndo(undo);
    }

    @Nullable
    public static Game reload(String gameName) {
        waitFor(GAME_NAMES_LOADED_LATCH);
        Game game = GameLoadService.load(gameName);
        handleMissingMatchingManagedGame(game);
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

    @Nullable
    public static ManagedGame getManagedGame(String gameName) {
        if (!isValid(gameName)) return null;
        waitFor(GAME_NAMES_LOADED_LATCH);
        return gameNameToManagedGame.computeIfAbsent(gameName, k -> {
            Game game = GameLoadService.load(gameName);
            if (game == null) {
                BotLogger.error("Failed to load ManagedGame for " + gameName + ".");
                throw new IllegalStateException("Failed to load ManagedGame for " + gameName);
            }
            return new ManagedGame(game);
        });
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
        return userIdToManagedPlayer.compute(player.getUserID(), (id, existing) -> {
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
}
