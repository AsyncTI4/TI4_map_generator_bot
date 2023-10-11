package ti4.commands.developer;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.jetbrains.annotations.NotNull;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.message.MessageHelper;

public abstract class DeveloperSubcommandData extends SubcommandData {

    private Game activeGame;
    private User user;

    public DeveloperSubcommandData(@NotNull String name, @NotNull String description) {
        super(name, description);
    }

    public Game getActiveGame() {
        return activeGame;
    }

    public User getUser() {
        return user;
    }

    abstract public void execute(SlashCommandInteractionEvent event);

    public void preExecute(SlashCommandInteractionEvent event) {
        user = event.getUser();
        activeGame = GameManager.getInstance().getUserActiveGame(user.getId());
    }
}
