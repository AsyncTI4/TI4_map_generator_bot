package ti4.commands2;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.jetbrains.annotations.NotNull;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.map.Player;

class CommandGameStateHelper {

    private final boolean saveGame;
    private final boolean isPlayerCommand;
    private final ThreadLocal<Game> game = new ThreadLocal<>();
    private final ThreadLocal<Player> player = new ThreadLocal<>();

    public CommandGameStateHelper(boolean saveGame, boolean isPlayerCommand) {
        this.saveGame = saveGame;
        this.isPlayerCommand = isPlayerCommand;
    }

    public void preExecute(SlashCommandInteractionEvent event) {
        String gameName = CommandHelper.getGameName(event);
        if (!GameManager.isValidGame(gameName)) {
            throw new IllegalArgumentException("Invalid game name: " + gameName + " while attempting to run event " + event.getName() +
                    " in channel " + event.getChannel().getName());
        }
        game.set(GameManager.getGame(gameName));

        if (!isPlayerCommand) {
            return;
        }
        var gamePlayer = CommandHelper.getPlayerFromEvent(getGame(), event);
        if (gamePlayer == null) {
            throw new IllegalArgumentException("Unable to determine player while attempting to run event " + event.getName() +
                    " in channel " + event.getChannel().getName() + " for game " + gameName);
        }
        player.set(gamePlayer);
    }

    public void postExecute(SlashCommandInteractionEvent event) {
        if (saveGame) {
            GameSaveLoadManager.saveGame(game.get(), event);
        }
        game.remove();
        player.remove();
    }

    @NotNull
    public Game getGame() {
        return game.get();
    }

    @NotNull
    public Player getPlayer() {
        if (!isPlayerCommand) {
            throw new IllegalStateException("CommandGameStateHelper cannot get player state because command was not set to be a player command.");
        }
        return player.get();
    }
}
