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

    private static final Set<String> validGameNames = ConcurrentHashMap.newKeySet();
    // TODO: We can evaluate dropping the managed objects entirely
    private static final ConcurrentMap<String, ManagedGame> gameNameToManagedGame = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, ManagedPlayer> userIdToManagedPlayer = new ConcurrentHashMap<>();
    private static final AtomicBoolean GAME_NAMES_INDEXED = new AtomicBoolean(false);
    private static final AtomicBoolean WARMUP_STARTED = new AtomicBoolean(false);
    private static final CountDownLatch WARMUP_LATCH = new CountDownLatch(1);
    private static final long WAIT_FOR_WARMUP_TIMEOUT_SECONDS = 30;

    public static synchronized void initialize() {
        indexManagedGameNamesIfNeeded();
        if (JdaService.testingMode) {
            WARMUP_LATCH.countDown();
            return;
        }

        startManagedGamesWarmupIfNeeded();
    }

    public static synchronized void startManagedGamesWarmupIfNeeded() {
        indexManagedGameNamesIfNeeded();
        if (!currentInstanceOwnsLeaseForWarmup()
                || WARMUP_LATCH.getCount() == 0
                || !WARMUP_STARTED.compareAndSet(false, true)) {
            return;
        }

        ExecutorServiceManager.runAsync("GameManager managed game warmup", () -> {
            try {
                validGameNames.forEach(GameManager::getManagedGame);
                WARMUP_LATCH.countDown();
            } catch (Exception e) {
                WARMUP_STARTED.compareAndSet(true, false);
                BotLogger.critical("Failed during managed game warmup.", e);
            } finally {
                if (JdaService.jda != null) {
                    JdaService.updatePresence();
                }
            }
        });
    }

    private static void indexManagedGameNamesIfNeeded() {
        if (GAME_NAMES_INDEXED.compareAndSet(false, true)) {
            validGameNames.addAll(GameLoadService.loadManagedGameNames());
        }
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
            validGameNames.add(game.getName());
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
        if (!hasLeaseToMakeChanges(game, reason)) {
            return false;
        }

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

    private static boolean hasLeaseToMakeChanges(Game game, String reason) {
        try {
            ActiveLeaseService activeLeaseService = SpringContext.getBean(ActiveLeaseService.class);
            if (activeLeaseService.mayMutate()) {
                return true;
            }
            String gameName = game == null ? "unknown" : game.getName();
            BotLogger.warning("Rejected game save because this instance does not own the active lease. Game: `"
                    + gameName + "` Reason: " + reason);
            return false;
        } catch (IllegalStateException e) {
            // Spring may not be initialized in some startup or test paths; preserve legacy behavior there.
            return true;
        }
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
            validGameNames.add(undo.getName());
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
        waitForAllManagedGamesToLoad();
        return new ArrayList<>(gameNameToManagedGame.values());
    }

    public static ManagedPlayer getManagedPlayer(String playerId) {
        waitForAllManagedGamesToLoad();
        return userIdToManagedPlayer.get(playerId);
    }

    public static Set<ManagedPlayer> getManagedPlayers() {
        waitForAllManagedGamesToLoad();
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

    public static boolean areAllManagedGamesLoaded() {
        return WARMUP_LATCH.getCount() == 0;
    }

    private static void waitForAllManagedGamesToLoad() {
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
