package ti4.map.persistence;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import lombok.experimental.UtilityClass;
import ti4.AsyncTI4DiscordBot;
import ti4.map.Game;
import ti4.map.Player;

@UtilityClass
public class GameManager {

    private static final ConcurrentMap<String, ManagedGame> gameNameToManagedGame = new ConcurrentHashMap<>(); // TODO: We can evaluate dropping the managed objects entirely
    private static final ConcurrentMap<String, ManagedPlayer> playerNameToManagedPlayer = new ConcurrentHashMap<>();

    public static void initialize() {
        GameLoadService.loadManagedGames().forEach(managedGame -> gameNameToManagedGame.put(managedGame.getName(), managedGame));
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
        return gameName != null && gameNameToManagedGame.containsKey(gameName);
    }

    public static boolean save(Game game, String reason) {
        boolean wasActive = Optional.ofNullable(gameNameToManagedGame.get(game.getName())).map(ManagedGame::isActive).orElse(false);
        if (!GameSaveService.save(game, reason)) {
            return false;
        }
        gameNameToManagedGame.put(game.getName(), new ManagedGame(game));

        boolean isActive = Optional.ofNullable(gameNameToManagedGame.get(game.getName())).map(ManagedGame::isActive).orElse(false);
        if (wasActive != isActive) {
            AsyncTI4DiscordBot.updatePresence();
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
        return new ArrayList<>(gameNameToManagedGame.keySet());
    }

    public static int getGameCount() {
        return gameNameToManagedGame.size();
    }

    public static long getActiveGameCount() {
        return getManagedGames().stream().filter(ManagedGame::isActive).count();
    }

    public static ManagedGame getManagedGame(String gameName) {
        return gameNameToManagedGame.get(gameName);
    }

    public static List<ManagedGame> getManagedGames() {
        return new ArrayList<>(gameNameToManagedGame.values());
    }

    public static ManagedPlayer getManagedPlayer(String playerId) {
        return playerNameToManagedPlayer.get(playerId);
    }

    static ManagedPlayer addOrMergePlayer(ManagedGame game, Player player) {
        var managedPlayer = playerNameToManagedPlayer.get(player.getUserID());
        if (managedPlayer == null) {
            managedPlayer = new ManagedPlayer(game, player);
            playerNameToManagedPlayer.put(player.getUserID(), managedPlayer);
            return managedPlayer;
        }
        managedPlayer.addOrReplaceGame(game, player);
        return managedPlayer;
    }
}
