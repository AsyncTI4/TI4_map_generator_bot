package ti4.commands.admin;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.GenerateMap;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.message.MessageHelper;

import java.io.File;

public class ReloadMap extends AdminSubcommandData {

    public ReloadMap() {
        super(Constants.RELOAD_GAME, "Reload game from save file");
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "GameName to reload").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping option = event.getOption(Constants.GAME_NAME);
        if (option != null) {
            String mapName = option.getAsString();
            if (!GameManager.getInstance().getGameNameToGame().containsKey(mapName)) {
                sendMessage("Game with such name does not exists, use /list_games");

                return;
            }
            Game activeGame = GameManager.getInstance().getGame(mapName);
            GameSaveLoadManager.reload(activeGame);
            activeGame = GameManager.getInstance().getGame(mapName);
            File file = GenerateMap.getInstance().saveImage(activeGame, event);
            MessageHelper.replyToMessage(event, file);

        } else {
            sendMessage("No Game specified.");
        }
    }
}
