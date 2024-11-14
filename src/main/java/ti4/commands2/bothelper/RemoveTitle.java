package ti4.commands2.bothelper;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.Subcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.message.MessageHelper;

class RemoveTitle extends Subcommand {

    public RemoveTitle() {
        super(Constants.REMOVE_TITLE, "Remove a title");
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "The game name where the title was given").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player @playername").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TITLE, "Title to Remove").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        Member user = guild.getMemberById(event.getOption("player").getAsString());

        OptionMapping option = event.getOption(Constants.GAME_NAME);
        String gameName = option.getAsString();

        Game game = GameManager.getGame(gameName);
        if (game == null) {
            MessageHelper.replyToMessage(event, "Game with such name does not exists, use /list_games");
            return;
        }

        String title = event.getOption("title").getAsString();
        String userID = user.getUser().getId();
        game.setStoredValue("TitlesFor" + userID, game.getStoredValue("TitlesFor" + userID).replace(title, ""));
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Removed the title " + title + " in game " + game.getName());
        GameSaveLoadManager.saveGame(game, event);
    }
}
