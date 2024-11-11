package ti4.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.jetbrains.annotations.NotNull;
import ti4.helpers.Helper;
import ti4.map.Player;

public abstract class PlayerGameStateSubcommand extends GameStateSubcommand {

    private final ThreadLocal<Player> player = new ThreadLocal<>();

    public PlayerGameStateSubcommand(@NotNull String name, @NotNull String description, boolean loadGame, boolean saveGame) {
        super(name, description, loadGame, saveGame);
    }

    public void preExecute(SlashCommandInteractionEvent event) {
        super.preExecute(event);
        if (loadGame) {
            setPlayer(event);
        }
    }

    private void setPlayer(SlashCommandInteractionEvent event) {
        var game = getGame();
        var gamePlayer = Helper.getPlayerFromEvent(getGame(), event);
        if (gamePlayer == null) {
            throw new IllegalArgumentException("Unable to determine player while attempting to run event " + event.getName() +
                    " in channel " + event.getChannel().getName() + " for game " + game.getName());
        }
        player.set(gamePlayer);
    }

    public void postExecute(SlashCommandInteractionEvent event) {
        super.postExecute(event);
        player.remove();
    }

    @NotNull
    protected Player getPlayer() {
        return player.get();
    }
}
