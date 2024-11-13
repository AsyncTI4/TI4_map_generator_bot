package ti4.commands2.admin;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.Subcommand;
import ti4.commands.uncategorized.ShowGame;
import ti4.helpers.Constants;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.message.MessageHelper;

class ReloadMap extends Subcommand {

    public ReloadMap() {
        super(Constants.RELOAD_GAME, "Reload game from save file");
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "GameName to reload").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String gameName = event.getOption(Constants.GAME_NAME).getAsString();
        if (!GameManager.isValidGame(gameName)) {
            MessageHelper.sendMessageToEventChannel(event, "Game with such name does not exists, use /list_games");
            return;
        }

        var game = GameSaveLoadManager.reload(gameName);
        ShowGame.simpleShowGame(game, event);
    }
}
