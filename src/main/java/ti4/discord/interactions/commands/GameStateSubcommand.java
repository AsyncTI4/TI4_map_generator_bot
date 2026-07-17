package ti4.discord.interactions.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.jetbrains.annotations.NotNull;
import ti4.game.Game;
import ti4.game.Player;

public abstract class GameStateSubcommand extends Subcommand implements GameStateContainer {

    private final CommandGameState commandGameState;

    protected GameStateSubcommand(
            @NotNull String name, @NotNull String description, boolean saveGame, boolean playerCommand) {
        super(name, description);
        commandGameState = new CommandGameState(saveGame, playerCommand);
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return super.accept(event)
                && CommandHelper.acceptIfValidGame(
                        event, commandGameState.saveGame(), commandGameState.playerCommand());
    }

    @Override
    public void preExecute(SlashCommandInteractionEvent event) {
        super.preExecute(event);
        commandGameState.preExecute(event);
    }

    @Override
    public void postExecute(SlashCommandInteractionEvent event) {
        super.postExecute(event);
        commandGameState.postExecute(event);
    }

    @Override
    public void onException(SlashCommandInteractionEvent event, Throwable throwable) {
        commandGameState.clear();
        super.onException(event, throwable);
    }

    @NotNull
    @Override
    public Game getGame() {
        return commandGameState.getGame();
    }

    @NotNull
    @Override
    public Player getPlayer() {
        return commandGameState.getPlayer();
    }

    @Override
    public boolean isSaveGame() {
        return commandGameState.saveGame();
    }
}
