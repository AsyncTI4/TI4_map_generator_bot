package ti4.game.persistence;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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
    private static final ConcurrentMap<String, ManagedGame> gameNameToManagedGame =
            new ConcurrentHashMap<>(); // TODO: We can evaluate dropping the managed objects entirely
    private static final ConcurrentMap<String, ManagedPlayer> userIdToManagedPlayer = new ConcurrentHashMap<>();
    private static final AtomicBoolean gameNamesIndexed = new AtomicBoolean(false);
    private static final AtomicBoolean managedGamesWarmupStarted = new AtomicBoolean(false);
    private static final AtomicBoolean managedGamesWarmupComplete = new AtomicBoolean(false);

    /**
     * Loads the authoritative game-name index synchronously, then warms managed-game metadata in the background.
     */
    public static void initialize() {
        validGameNames.clear();
        gameNameToManagedGame.clear();
        userIdToManagedPlayer.clear();
        gameNamesIndexed.set(false);
        managedGamesWarmupStarted.set(false);
        managedGamesWarmupComplete.set(false);

        validGameNames.addAll(GameLoadService.loadManagedGameNames());
        gameNamesIndexed.set(true);
    }

    /**
     * Starts the background managed-game warmup once game names are indexed and only if it has not already begun.
     */
    public static boolean startManagedGamesWarmupIfNeeded() {
        if (JdaService.testingMode || !gameNamesIndexed.get() || managedGamesWarmupComplete.get()) {
            return false;
        }
        if (!managedGamesWarmupStarted.compareAndSet(false, true)) {
            return false;
        }
        ExecutorServiceManager.runAsync("GameManager managed game warmup", GameManager::warmupManagedGames);
        return true;
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
        ensureManagedGameLoaded(gameName);
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
        if (!mayMutate(game, reason)) {
            return false;
        }
        validGameNames.add(game.getName());
        boolean wasActive = Optional.ofNullable(gameNameToManagedGame.get(game.getName()))
                .map(ManagedGame::isActive)
                .orElse(false);
        if (!GameSaveService.save(game, reason)) {
            return false;
        }
        gameNameToManagedGame.put(game.getName(), new ManagedGame(game));

        boolean isActive = Optional.ofNullable(gameNameToManagedGame.get(game.getName()))
                .map(ManagedGame::isActive)
                .orElse(false);
        if (wasActive != isActive) {
            JdaService.updatePresence();
        }
        return true;
    }

    private static boolean mayMutate(Game game, String reason) {
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
        return ensureManagedGameLoaded(gameName);
    }

    public static List<ManagedGame> getManagedGames() {
        ensureAllManagedGamesLoaded();
        return new ArrayList<>(gameNameToManagedGame.values());
    }

    public static ManagedPlayer getManagedPlayer(String playerId) {
        ensureAllManagedGamesLoaded();
        return userIdToManagedPlayer.get(playerId);
    }

    public static Set<ManagedPlayer> getManagedPlayers() {
        ensureAllManagedGamesLoaded();
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

    /**
     * Returns whether the background pass that materializes all managed games has finished.
     */
    public static boolean isManagedGamesWarmupComplete() {
        return managedGamesWarmupComplete.get();
    }

    /**
     * Ensures a single valid game has a ManagedGame entry, loading it lazily on first access.
     */
    private static ManagedGame ensureManagedGameLoaded(String gameName) {
        return ensureManagedGameLoaded(gameName, ManagedGameLoadMode.LAZY_REQUEST);
    }

    /**
     * Materializes managed metadata for one game, choosing whether the parsed Game is retained for immediate reuse.
     */
    private static ManagedGame ensureManagedGameLoaded(String gameName, ManagedGameLoadMode loadMode) {
        if (!isValid(gameName)) {
            return null;
        }
        return gameNameToManagedGame.computeIfAbsent(gameName, key -> loadManagedGame(key, loadMode));
    }

    @Nullable
    private static ManagedGame loadManagedGame(String gameName, ManagedGameLoadMode loadMode) {
        ManagedGame managedGame = GameLoadService.loadManagedGame(gameName, loadMode);
        if (managedGame == null) {
            handleManagedGameRemoval(gameName);
        }
        return managedGame;
    }

    /**
     * Blocks until every indexed game has managed metadata, for callers that require a complete global view.
     */
    private static void ensureAllManagedGamesLoaded() {
        if (managedGamesWarmupComplete.get()) {
            return;
        }
        validGameNames.forEach(gameName -> ensureManagedGameLoaded(gameName, ManagedGameLoadMode.WARMUP));
        managedGamesWarmupComplete.compareAndSet(false, true);
    }

    /**
     * Background task that hydrates ManagedGame entries and player indexes for every known game.
     */
    private static void warmupManagedGames() {
        try {
            validGameNames.forEach(gameName -> ensureManagedGameLoaded(gameName, ManagedGameLoadMode.WARMUP));
        } catch (Exception e) {
            BotLogger.critical("Failed during managed game warmup.", e);
        } finally {
            managedGamesWarmupComplete.set(true);
            if (JdaService.jda != null) {
                JdaService.updatePresence();
            }
        }
    }
}
