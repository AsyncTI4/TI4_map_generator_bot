package ti4.commands2.bothelper;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

class RemoveTitle extends GameStateSubcommand {

    public RemoveTitle() {
        super(Constants.REMOVE_TITLE, "Remove a title", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "The game name where the title was given").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player @playername").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TITLE, "Title to Remove").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String title = event.getOption(Constants.TITLE).getAsString();
        String userId = event.getOption(Constants.PLAYER).getAsUser().getId();
        game.setStoredValue("TitlesFor" + userId, game.getStoredValue("TitlesFor" + userId).replace(title, ""));
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Removed the title " + title + " in game " + game.getName());
    }
}
