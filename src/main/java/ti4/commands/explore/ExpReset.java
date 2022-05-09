package ti4.commands.explore;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Map;

public class ExpReset extends ExploreSubcommandData {

	public ExpReset() {
		super(Constants.EXP_RESET, "Reset the exploration decks, emptying discards and adding all cards to their respective decks.");
		addOptions(new OptionData(OptionType.STRING, Constants.CONFIRM, "Type YES").setRequired(true));
	}

	@Override
	public void execute(SlashCommandInteractionEvent event) {
		if (event.getOption(Constants.CONFIRM).getAsString() == "YES") {
			Map activeMap = getActiveMap();
			activeMap.resetExplore();
		}
	}

}
