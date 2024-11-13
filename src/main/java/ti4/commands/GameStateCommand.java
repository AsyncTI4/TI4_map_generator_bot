package ti4.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.map.Game;
import ti4.map.Player;

public abstract class GameStateCommand implements Command {

    private final CommandGameStateHelper commandGameStateHelper;

    public GameStateCommand(boolean loadGame, boolean saveGame, boolean isPlayerCommand) {
        commandGameStateHelper = new CommandGameStateHelper(loadGame, saveGame, isPlayerCommand);
    }

    @Override
    public void preExecute(SlashCommandInteractionEvent event) {
        Command.super.preExecute(event);
        commandGameStateHelper.preExecute(event);
    }

    @Override
    public void postExecute(SlashCommandInteractionEvent event) {
        Command.super.postExecute(event);
        commandGameStateHelper.postExecute(event);
    }

    protected Game getGame() {
        return commandGameStateHelper.getGame();
    }

    protected Player getPlayer() {
        return commandGameStateHelper.getPlayer();
    }
}
