package ti4.commands.special;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.jetbrains.annotations.NotNull;
import ti4.generator.MapRenderPipeline;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;
import ti4.map.UserGameContextManager;

public abstract class SpecialSubcommandData extends SubcommandData {

    private Game game;
    private User user;

    public String getActionID() {
        return getName();
    }

    public SpecialSubcommandData(@NotNull String name, @NotNull String description) {
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
        GameSaveLoadManager.saveGame(game, event);
        MapRenderPipeline.renderToWebsiteOnly(game, event);
    }
}
