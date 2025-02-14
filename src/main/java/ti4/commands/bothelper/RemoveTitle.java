package ti4.commands.bothelper;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.manage.GameManager;
import ti4.message.MessageHelper;

class RemoveTitle extends Subcommand {

    public RemoveTitle() {
        super(Constants.REMOVE_TITLE, "Remove a title");
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "The game name where the title was given")
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player @playername").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TITLE, "Title to Remove").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String gameName = event.getOption(Constants.GAME_NAME).getAsString();
        if (!GameManager.isValid(gameName)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Game " + gameName + " not found");
            return;
        }
        Game game = GameManager.getManagedGame(gameName).getGame();
        String userId = event.getOption(Constants.PLAYER).getAsUser().getId();
        String title = event.getOption(Constants.TITLE).getAsString();
        game.setStoredValue(
                "TitlesFor" + userId, game.getStoredValue("TitlesFor" + userId).replace(title, ""));
        GameManager.save(game, "Title Removal");
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(), "Removed the title " + title + " in game " + game.getName());
    }
}
