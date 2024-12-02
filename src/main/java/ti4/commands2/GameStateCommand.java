package ti4.commands2;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.jetbrains.annotations.NotNull;
import ti4.map.Game;
import ti4.map.Player;

public abstract class GameStateCommand implements ParentCommand {

    private final CommandGameState commandGameState;

    public GameStateCommand(boolean saveGame, boolean isPlayerCommand) {
        commandGameState = new CommandGameState(saveGame, isPlayerCommand);
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return ParentCommand.super.accept(event) && CommandHelper.acceptIfPlayerInGameAndGameChannel(event);
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

    @NotNull
    protected Game getGame() {
        return commandGameState.getGame();
    }

    @NotNull
    protected Player getPlayer() {
        return commandGameState.getPlayer();
    }
}
