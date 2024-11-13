package ti4.commands.game;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;
import ti4.message.MessageHelper;

abstract public class JoinLeave extends GameStateSubcommand {

    public JoinLeave(@NotNull String name, @NotNull String description) {
        super(name, description);
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "Game name").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        User user = event.getUser();
        action(game, user);

        // Helper.fixGameChannelPermissions(event.getGuild(), game);
        GameSaveLoadManager.saveGame(game, event);
        MessageHelper.replyToMessage(event, getResponseMessage(game, user));
    }

    abstract protected String getResponseMessage(Game game, User user);

    abstract protected void action(Game game, User user);
}
