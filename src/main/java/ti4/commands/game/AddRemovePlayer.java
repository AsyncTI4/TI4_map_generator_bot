package ti4.commands.game;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import ti4.commands.CommandHelper;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.message.MessageHelper;

abstract public class AddRemovePlayer extends GameStateSubcommand {

    public AddRemovePlayer(@NotNull String name, @NotNull String description) {
        super(name, description, true, false);
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER1, "Player @playerName").setRequired(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER2, "Player @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER3, "Player @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER4, "Player @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER5, "Player @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER6, "Player @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER7, "Player @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER8, "Player @playerName"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping gameOption = event.getOption(Constants.GAME_NAME);
        String gameName;
        if (gameOption != null) {
            gameName = event.getOptions().getFirst().getAsString();
            if (!GameManager.isValidGame(gameName)) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Game with such name does not exist, use `/help list_games`");
                return;
            }
        } else {
            gameName = CommandHelper.getGameName(event);
        }

        Game game = GameManager.getGame(gameName);
        User user = event.getUser();
        action(event, game, user);
        Helper.fixGameChannelPermissions(event.getGuild(), GameManager.getGame(gameName));
        GameSaveLoadManager.saveGame(game, event);
        MessageHelper.replyToMessage(event, getResponseMessage(game, user));
    }

    abstract protected String getResponseMessage(Game game, User user);

    abstract protected void action(SlashCommandInteractionEvent event, Game game, User user);
}
