package ti4.commands2;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.jetbrains.annotations.NotNull;
import ti4.map.Game;
import ti4.map.Player;

public abstract class GameStateSubcommand extends Subcommand {

    private final CommandGameStateHelper commandGameStateHelper;

    public GameStateSubcommand(@NotNull String name, @NotNull String description, boolean saveGame, boolean isPlayerCommand) {
        super(name, description);
        commandGameStateHelper = new CommandGameStateHelper(saveGame, isPlayerCommand);
    }

    @Override
    public void preExecute(SlashCommandInteractionEvent event) {
        super.preExecute(event);
        commandGameStateHelper.preExecute(event);
    }

    @Override
    public void postExecute(SlashCommandInteractionEvent event) {
        super.postExecute(event);
        commandGameStateHelper.postExecute(event);
    }

    @NotNull
    protected Game getGame() {
        return commandGameStateHelper.getGame();
    }

    @NotNull
    protected Player getPlayer() {
        return commandGameStateHelper.getPlayer();
    }
}
