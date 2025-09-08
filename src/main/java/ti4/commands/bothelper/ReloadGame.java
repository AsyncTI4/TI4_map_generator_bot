package ti4.commands.bothelper;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.persistence.GameManager;
import ti4.message.MessageHelper;

public class ReloadGame extends Subcommand {

    public ReloadGame() {
        super(Constants.RELOAD_GAME, "Reload game from disk");
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "Game name to load to").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String gameName = event.getOption(Constants.GAME_NAME).getAsString();
        Game game = GameManager.reload(gameName);
        if (game == null) {
            MessageHelper.sendMessageToEventChannel(event, "Failed to reload game.");
            return;
        }

        MessageHelper.sendMessageToEventChannel(event, game.getName() + " reloaded.");
        MessageHelper.sendMessageToChannel(game.getActionsChannel(), game.getName() + " was reloaded.");
    }
}
