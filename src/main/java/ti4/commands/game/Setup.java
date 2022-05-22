package ti4.commands.game;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.map.Map;
import ti4.message.MessageHelper;

public class Setup extends GameSubcommandData {
    public Setup() {
        super(Constants.SETUP, "Game Setup");
        addOptions(new OptionData(OptionType.INTEGER, Constants.PLAYER_COUNT_FOR_MAP, "Specify player map size: 6 or 8. Default 6").setRequired(false));
        addOptions(new OptionData(OptionType.INTEGER, Constants.VP_COUNT, "Specify game VP count").setRequired(false));
        addOptions(new OptionData(OptionType.STRING, Constants.DISPLAY_TYPE, "Show map in specific format. all, map, stats").setRequired(false).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_CUSTOM_NAME, "Add Custom description to game").setRequired(false));
        addOptions(new OptionData(OptionType.STRING, Constants.COMMUNITY_MODE, "Set to YES if want to allow Community Mode for map, FALSE to disable it").setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();

        OptionMapping playerCount = event.getOption(Constants.PLAYER_COUNT_FOR_MAP);
        if (playerCount != null) {
            int count = playerCount.getAsInt();
            if (count != 6 && count != 8) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Must specify 6 or 8 player.");
            } else {
                activeMap.setPlayerCountForMap(count);
            }
        }

        OptionMapping vpOption = event.getOption(Constants.VP_COUNT);
        if (vpOption != null) {
            int count = vpOption.getAsInt();
            if (count > 14){
                count = 14;
            } else if (count < 1){
                count = 1;
            }
            activeMap.setVp(count);
        }

        DisplayType displayType = null;
        OptionMapping statsOption = event.getOption(Constants.DISPLAY_TYPE);
        if (statsOption != null) {
            String temp = statsOption.getAsString();
            if (temp.equals(DisplayType.all.getValue())) {
                displayType = DisplayType.all;
            }
            if (temp.equals(DisplayType.map.getValue())) {
                displayType = DisplayType.map;
            } else if (temp.equals(DisplayType.stats.getValue())) {
                displayType = DisplayType.stats;
            } else if (temp.equals("none")) {
                activeMap.setDisplayTypeForced(null);
                return;
            }
        }
        OptionMapping communityOption = event.getOption(Constants.COMMUNITY_MODE);
        if (communityOption != null){
            String communityMode = communityOption.getAsString();
            if ("YES".equals(communityMode)){
                activeMap.setCommunityMode(true);
            } else if ("FALSE".equals(communityMode)){
                activeMap.setCommunityMode(false);
            }
        }

        OptionMapping customOption = event.getOption(Constants.GAME_CUSTOM_NAME);
        if (customOption != null){
            String customName = customOption.getAsString();
            activeMap.setCustomName(customName);
        }


        if (displayType != null) {
            activeMap.setDisplayTypeForced(displayType);
        }
    }
}
