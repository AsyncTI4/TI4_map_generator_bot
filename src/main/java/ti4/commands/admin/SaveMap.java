package ti4.commands.admin;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.message.MessageHelper;

public class SaveMap extends AdminSubcommandData {

    public SaveMap() {
        super(Constants.SAVE_GAME, "Save game");
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "GameName to reload").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {

        OptionMapping option = event.getOption(Constants.GAME_NAME);
        if (option != null) {
            String mapName = option.getAsString();
            if (!GameManager.getInstance().getGameNameToGame().containsKey(mapName)) {
                MessageHelper.sendMessageToEventChannel(event, "Game with such name does not exists, use /list_games");
                return;
            }
            Game game = GameManager.getInstance().getGame(mapName);
            GameSaveLoadManager.saveGame(game, event);
            MessageHelper.sendMessageToEventChannel(event, "Save map: " + game.getName());

        } else {
            MessageHelper.sendMessageToEventChannel(event, "No Game specified.");
        }
    }
}
