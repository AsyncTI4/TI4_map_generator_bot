package ti4.commands.game;

import java.util.ArrayList;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.map.Map;
import ti4.message.MessageHelper;

public class Setup extends GameSubcommandData {
    public Setup() {
        super(Constants.SETUP, "Game Setup");
        addOptions(new OptionData(OptionType.INTEGER, Constants.PLAYER_COUNT_FOR_MAP, "Specify player map size between 2 or 30. Default 6").setRequired(false));
        addOptions(new OptionData(OptionType.INTEGER, Constants.RING_COUNT_FOR_MAP, "Specify ring count for map: Default between: 3 use standard map, 8 - for max map size. Default 4").setRequired(false));
        addOptions(new OptionData(OptionType.INTEGER, Constants.VP_COUNT, "Specify game VP count").setRequired(false));
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_CUSTOM_NAME, "Add Custom description to game").setRequired(false));
        addOptions(new OptionData(OptionType.STRING, Constants.COMMUNITY_MODE, "Set to YES if want to allow Community Mode for map, FALSE to disable it").setRequired(false));
        addOptions(new OptionData(OptionType.STRING, Constants.ALLIANCE_MODE, "Set to YES if want to allow Alliance Mode for map, FALSE to disable it").setRequired(false));
        addOptions(new OptionData(OptionType.STRING, Constants.FOW_MODE, "Set to YES if want to allow FoW Mode for map, FALSE to disable it").setRequired(false));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.ABSOL_MODE, "True to switch out the PoK Agendas & Relics for Absol's - do NOT change this mid-game"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.DISCORDANT_STARS_MODE, "True to add the Discordant Stars factions to the pool."));
        addOptions(new OptionData(OptionType.STRING, Constants.LARGE_TEXT, "Small/medium/large, default small").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();

        OptionMapping playerCount = event.getOption(Constants.PLAYER_COUNT_FOR_MAP);
        if (playerCount != null) {
            int count = playerCount.getAsInt();
            if (count < 2 || count > 30) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Must specify between 2 or 30 players.");
            } else {
                activeMap.setPlayerCountForMap(count);
            }
        }

        OptionMapping ringCount = event.getOption(Constants.RING_COUNT_FOR_MAP);
        if (ringCount != null) {
            int count = ringCount.getAsInt();
            if (count < 3 || count > 8) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Must specify between 3 and 8 rings.");
            } else {
                activeMap.setRingCount(count);
            }
        }

        OptionMapping vpOption = event.getOption(Constants.VP_COUNT);
        if (vpOption != null) {
            int count = vpOption.getAsInt();
            if (count < 1){
                count = 1;
            } else if (count > 20){
                count = 20;
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

        OptionMapping allianceOption = event.getOption(Constants.ALLIANCE_MODE);
        if (allianceOption != null){
            String alliaceMode = allianceOption.getAsString();
            if ("YES".equals(alliaceMode)){
                activeMap.setAllianceMode(true);
            } else if ("FALSE".equals(alliaceMode)){
                activeMap.setAllianceMode(false);
            }
        }

        OptionMapping fowOption = event.getOption(Constants.FOW_MODE);
        if (fowOption != null){
            String fowMode = fowOption.getAsString();
            if ("YES".equals(fowMode)){
                activeMap.setFoWMode(true);
            } else if ("FALSE".equals(fowMode)){
                activeMap.setFoWMode(false);
            }
        }

        OptionMapping largeText = event.getOption(Constants.LARGE_TEXT);
        if (largeText != null) {
            String large = largeText.getAsString();
            getActiveMap().setLargeText(large);
        }

        OptionMapping customOption = event.getOption(Constants.GAME_CUSTOM_NAME);
        if (customOption != null){
            String customName = customOption.getAsString();
            activeMap.setCustomName(customName);
        }

        OptionMapping absolModeOption = event.getOption(Constants.ABSOL_MODE);
        if (absolModeOption != null) {
            getActiveMap().setAbsolMode(absolModeOption.getAsBoolean());
            getActiveMap().resetAgendas();
            getActiveMap().resetRelics();
        }

        OptionMapping discordantStarsOption = event.getOption(Constants.DISCORDANT_STARS_MODE);
        if (discordantStarsOption != null) {
            activeMap.setDiscordantStarsMode(discordantStarsOption.getAsBoolean());
        }

        if (displayType != null) {
            activeMap.setDisplayTypeForced(displayType);
        }
    }
}
