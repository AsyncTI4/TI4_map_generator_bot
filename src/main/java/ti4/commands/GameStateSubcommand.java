package ti4.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.jetbrains.annotations.NotNull;
import ti4.AsyncTI4DiscordBot;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;

public abstract class GameStateSubcommand extends Subcommand {

    protected final boolean loadGame;
    protected final boolean saveGame;
    private final ThreadLocal<Game> game = new ThreadLocal<>();
    private final ThreadLocal<Long> gameLastModifiedDate = new ThreadLocal<>();

    public GameStateSubcommand(@NotNull String name, @NotNull String description, boolean loadGame, boolean saveGame) {
        super(name, description);
        this.loadGame = loadGame;
        this.saveGame = saveGame;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return name.equals(event.getInteraction().getSubcommandName()) &&
                CommandHelper.acceptIfHasRoles(event, AsyncTI4DiscordBot.adminRoles) || CommandHelper.acceptIfPlayerInGame(event);
    }

    public void preExecute(SlashCommandInteractionEvent event) {
        super.preExecute(event);
        if (loadGame) {
            String gameName = CommandHelper.getGameName(event);
            if (!GameManager.isValidGame(gameName)) {
                throw new IllegalArgumentException("Invalid game name: " + gameName + " while attempting to run event " + event.getName() +
                        " in channel " + event.getChannel().getName());
            }
            game.set(GameManager.getGame(gameName));
            gameLastModifiedDate.set(game.get().getLastModifiedDate());
        }
    }

    public void postExecute(SlashCommandInteractionEvent event) {
        super.postExecute(event);
        if (loadGame && saveGame && gameLastModifiedDate.get() != game.get().getLastModifiedDate()) {
            GameSaveLoadManager.saveGame(game.get(), event);
        }
        game.remove();
        gameLastModifiedDate.remove();
    }

    @NotNull
    protected Game getGame() {
        return game.get();
    }
}
