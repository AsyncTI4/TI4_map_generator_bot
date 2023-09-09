package ti4.commands.installation;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.io.File;

import org.jetbrains.annotations.NotNull;

import ti4.generator.GenerateMap;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.message.MessageHelper;

public abstract class InstallationSubcommandData extends SubcommandData {

    private Game activeGame;
    private User user;

    public String getActionID() {
        return getName();
    }

    public InstallationSubcommandData(@NotNull String name, @NotNull String description) {
        super(name, description);
    }

    public Game getActiveMap() {
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
        String userID = event.getUser().getId();
        Game activeGame = GameManager.getInstance().getUserActiveGame(userID);
        GameSaveLoadManager.saveMap(activeGame, event);

        File file = GenerateMap.getInstance().saveImage(activeGame, event);
        MessageHelper.replyToMessage(event, file);
    }
}
