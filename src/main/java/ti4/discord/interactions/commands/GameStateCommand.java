package ti4.discord.interactions.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.jetbrains.annotations.NotNull;
import ti4.game.Game;
import ti4.game.Player;

public abstract class GameStateCommand implements ParentCommand, GameStateContainer {

    private final String name;
    private final String description;
    private final CommandGameState commandGameState;

    protected GameStateCommand(boolean saveGame, boolean playerCommand) {
        this(null, null, saveGame, playerCommand);
    }

    protected GameStateCommand(String name, String description, boolean saveGame, boolean playerCommand) {
        this.name = name;
        this.description = description;
        commandGameState = new CommandGameState(saveGame, playerCommand);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
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
        CommandGameState.clear();
        ParentCommand.super.onException(event, throwable);
    }

    @NotNull
    @Override
    public Game getGame() {
        return CommandGameState.getGame();
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
