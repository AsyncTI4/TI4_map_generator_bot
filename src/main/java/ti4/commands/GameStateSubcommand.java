package ti4.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.jetbrains.annotations.NotNull;
import ti4.map.Game;
import ti4.map.Player;

public abstract class GameStateSubcommand extends Subcommand {

    private final CommandGameState commandGameState;
    private final boolean saveGame;
    private final boolean playerCommand;

    public GameStateSubcommand(
            @NotNull String name, @NotNull String description, boolean saveGame, boolean playerCommand) {
        super(name, description);
        this.saveGame = saveGame;
        this.playerCommand = playerCommand;
        commandGameState = new CommandGameState(saveGame, playerCommand);
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return super.accept(event) && CommandHelper.acceptIfValidGame(event, saveGame, playerCommand);
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

    @NotNull
    protected Game getGame() {
        return commandGameState.getGame();
    }

    @NotNull
    protected Player getPlayer() {
        return commandGameState.getPlayer();
    }
}
