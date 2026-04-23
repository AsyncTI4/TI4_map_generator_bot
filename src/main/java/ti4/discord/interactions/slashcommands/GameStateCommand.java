package ti4.discord.interactions.slashcommands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.jetbrains.annotations.NotNull;
import ti4.game.Game;
import ti4.game.Player;

public abstract class GameStateCommand implements ParentCommand, GameStateContainer {

    private final CommandGameState commandGameState;

    protected GameStateCommand(boolean saveGame, boolean playerCommand) {
        commandGameState = new CommandGameState(saveGame, playerCommand);
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return ParentCommand.super.accept(event)
                && CommandHelper.acceptIfValidGame(
                        event, commandGameState.saveGame(), commandGameState.playerCommand());
    }

    @Override
    public void preExecute(SlashCommandInteractionEvent event) {
        ParentCommand.super.preExecute(event);
        commandGameState.preExecute(event);
    }

    @Override
    public void postExecute(SlashCommandInteractionEvent event) {
        ParentCommand.super.postExecute(event);
        commandGameState.postExecute(event);
    }

    @Override
    public void onException(SlashCommandInteractionEvent event, Throwable throwable) {
        commandGameState.clear();
        ParentCommand.super.onException(event, throwable);
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
