package ti4.commands.game;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;
import ti4.message.MessageHelper;

abstract public class JoinLeave extends GameSubcommandData {

    public JoinLeave(@NotNull String name, @NotNull String description) {
        super(name, description);
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "Game name"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        User user = event.getUser();
        action(activeGame, user);

       // Helper.fixGameChannelPermissions(event.getGuild(), activeGame);
        GameSaveLoadManager.saveMap(activeGame, event);
        MessageHelper.replyToMessage(event, getResponseMessage(activeGame, user));
    }
    abstract protected String getResponseMessage(Game activeGame, User user);

    abstract protected void action(Game activeGame, User user);
}