package ti4.commands.admin;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.MapSaveLoadManager;
import ti4.message.MessageHelper;

public class SaveMap extends AdminSubcommandData {

    public SaveMap() {
        super(Constants.SAVE_GAME, "Save game");
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "GameName to reload").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {

        OptionMapping option = event.getOption(Constants.GAME_NAME);
        if (option != null) {
            String mapName = option.getAsString();
            if (!MapManager.getInstance().getMapList().containsKey(mapName)) {
                MessageHelper.replyToMessage(event, "Game with such name does not exists, use /list_games");
                return;
            }
            Map map = MapManager.getInstance().getMap(mapName);
            MapSaveLoadManager.saveMap(map);
            MessageHelper.replyToMessage(event, "Save map: " + map.getName());

        } else {
            MessageHelper.replyToMessage(event, "No Game specified.");
        }
    }
}
