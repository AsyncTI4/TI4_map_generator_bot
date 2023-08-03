package ti4.commands.custom;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.MapSaveLoadManager;
import ti4.message.MessageHelper;

public class SetUpPeakableObjectives extends CustomSubcommandData {
    public SetUpPeakableObjectives() {
        super(Constants.SETUP_PEAKABLE_OBJECTIVES, "Set up a set of peakable POs");
        addOptions(new OptionData(OptionType.INTEGER, Constants.NUMBER_OF_OBJECTIVES, "How many objectives you want in stage 1s/stage 2s(4 or 5)").setRequired(true));
        
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        OptionMapping loc1 = event.getOption(Constants.NUMBER_OF_OBJECTIVES);
        
        activeMap.setUpPeakableObjectives(loc1.getAsInt());
        MessageHelper.sendMessageToChannel(event.getChannel(), "Set up peakable objective decks with "+ loc1.getAsInt() + " objectives in each.");
        MapSaveLoadManager.saveMap(activeMap, event);
    }
}
