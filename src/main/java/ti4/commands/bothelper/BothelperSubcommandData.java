package ti4.commands.bothelper;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.jetbrains.annotations.NotNull;
import ti4.map.Game;
import ti4.map.GameManager;

public abstract class BothelperSubcommandData extends SubcommandData {

    private Game game;
    private User user;

    public BothelperSubcommandData(@NotNull String name, @NotNull String description) {
        super(name, description);
    }

    public User getUser() {
        return user;
    }

    public Game getActiveGame() {
        return game;
    }

    abstract public void execute(SlashCommandInteractionEvent event);

    public void preExecute(SlashCommandInteractionEvent event) {
        user = event.getUser();
        game = GameManager.getInstance().getUserActiveGame(user.getId());
    }
}
