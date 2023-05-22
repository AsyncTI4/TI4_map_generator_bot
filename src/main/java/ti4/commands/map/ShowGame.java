package ti4.commands.map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.commands.Command;
import ti4.generator.GenerateMap;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.message.MessageHelper;

import java.io.File;

public class ShowGame implements Command {

    @Override
    public String getActionID() {
        return Constants.SHOW_GAME;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        if (!event.getName().equals(getActionID())) {
            return false;
        }
        OptionMapping option = event.getOption(Constants.GAME_NAME);
        if (option != null) {
            String mapName = option.getAsString();
            if (!MapManager.getInstance().getMapList().containsKey(mapName)) {
                MessageHelper.replyToMessage(event, "Game with such name does not exists, use /list_games");
                return false;
            }
        } else {
            Map userActiveMap = MapManager.getInstance().getUserActiveMap(event.getUser().getId());
            if (userActiveMap == null){
                MessageHelper.replyToMessage(event, "No active game set, need to specify what map to show");
                return false;
            }
        }
        return true;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {

        Map activeMap;
        OptionMapping option = event.getOption(Constants.GAME_NAME);
        MapManager mapManager = MapManager.getInstance();
        if (option != null) {
            String mapName = option.getAsString().toLowerCase();
            activeMap = mapManager.getMap(mapName);
        } else {
            activeMap = mapManager.getUserActiveMap(event.getUser().getId());
        }
        DisplayType displayType = null;
        OptionMapping statsOption = event.getOption(Constants.DISPLAY_TYPE);
        if (statsOption != null) {
            String temp = statsOption.getAsString();
            if (temp.equals(DisplayType.all.getValue())) {
                displayType = DisplayType.all;
            } else if (temp.equals(DisplayType.map.getValue())) {
                displayType = DisplayType.map;
            } else if (temp.equals(DisplayType.stats.getValue())) {
                displayType = DisplayType.stats;
            } else if (temp.equals(DisplayType.split.getValue())) {
                displayType = DisplayType.map;
                File stats_file = GenerateMap.getInstance().saveImage(activeMap, displayType, event);
                MessageHelper.sendFileToChannel(event.getChannel(), stats_file);

                displayType = DisplayType.stats;
            }
        }
        File file = GenerateMap.getInstance().saveImage(activeMap, displayType, event);
        MessageHelper.sendFileToChannel(event.getChannel(), file);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
                Commands.slash(getActionID(), "Shows selected map")
                        .addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "Map name to be shown"))
                        .addOptions(new OptionData(OptionType.STRING, Constants.DISPLAY_TYPE, "Show map in specific format. all, map, stats").setAutoComplete(true)));
    }
}
