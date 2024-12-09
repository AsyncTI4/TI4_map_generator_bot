package ti4.commands2.admin;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.Subcommand;
import ti4.helpers.Constants;
import ti4.map.manage.GameManager;
import ti4.message.MessageHelper;
import ti4.service.ShowGameService;

class ReloadGame extends Subcommand {

    public ReloadGame() {
        super(Constants.RELOAD_GAME, "Reload game from save file");
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "GameName to reload").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String gameName = event.getOption(Constants.GAME_NAME).getAsString();
        if (!GameManager.isValid(gameName)) {
            MessageHelper.sendMessageToEventChannel(event, "Game with such name does not exist. Use an autocompleted entry.");
            return;
        }

        var game = GameManager.reload(gameName);
        ShowGameService.simpleShowGame(game, event);
    }
}
