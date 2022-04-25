package ti4.commands.game;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.map.Map;

public class Setup extends GameSubcommandData {
    public Setup() {
        super(Constants.SETUP, "Game Setup");
        addOptions(new OptionData(OptionType.STRING, Constants.DISPLAY_TYPE, "Show map in specific format. all, map, stats").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();

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
            } else if (temp.equals("none")){
                activeMap.setDisplayTypeForced(null);
                return;
            }
        }
        if (displayType != null) {
            activeMap.setDisplayTypeForced(displayType);
        }
    }
}
