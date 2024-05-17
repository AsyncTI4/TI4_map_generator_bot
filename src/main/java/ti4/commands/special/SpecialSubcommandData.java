package ti4.commands.special;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.jetbrains.annotations.NotNull;
import ti4.generator.MapGenerator;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;

public abstract class SpecialSubcommandData extends SubcommandData {

    private Game activeGame;
    private User user;

    public String getActionID() {
        return getName();
    }

    public SpecialSubcommandData(@NotNull String name, @NotNull String description) {
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

    public void reply(SlashCommandInteractionEvent event) {
        GameSaveLoadManager.saveMap(activeGame, event);
        MapGenerator.saveImageToWebsiteOnly(activeGame, event);
    }
}
