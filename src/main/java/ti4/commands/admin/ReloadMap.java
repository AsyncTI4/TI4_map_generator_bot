package ti4.commands.admin;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.uncategorized.ShowGame;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.message.MessageHelper;

public class ReloadMap extends AdminSubcommandData {

    public ReloadMap() {
        super(Constants.RELOAD_GAME, "Reload game from save file");
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "GameName to reload").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping option = event.getOption(Constants.GAME_NAME);
        if (option != null) {
            String mapName = option.getAsString();
            if (!GameManager.isValidGame(mapName)) {
                MessageHelper.sendMessageToEventChannel(event, "Game with such name does not exists, use /list_games");

                return;
            }
            Game game = GameManager.getGame(mapName);
            GameSaveLoadManager.reload(game);
            game = GameManager.getGame(mapName);
            ShowGame.simpleShowGame(game, event);

        } else {
            MessageHelper.sendMessageToEventChannel(event, "No Game specified.");
        }
    }
}
