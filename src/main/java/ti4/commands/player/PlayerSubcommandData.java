package ti4.commands.player;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.jetbrains.annotations.NotNull;
import ti4.map.Game;
import ti4.map.GameManager;

public abstract class PlayerSubcommandData extends SubcommandData {

    private Game game;
    private User user;

    public String getActionID() {
        return getName();
    }

    public PlayerSubcommandData(@NotNull String name, @NotNull String description) {
        super(name, description);
    }

    public Game getActiveGame() {
        return game;
    }

    public User getUser() {
        return user;
    }

    abstract public void execute(SlashCommandInteractionEvent event);

    public void preExecute(SlashCommandInteractionEvent event) {
        user = event.getUser();
        game = UserGameContextManager.getContextGame(user.getId());
    }

    public void reply(SlashCommandInteractionEvent event) {
        PlayerCommand.reply(event);
    }
}
