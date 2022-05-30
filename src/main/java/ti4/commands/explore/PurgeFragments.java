package ti4.commands.explore;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.MapSaveLoadManager;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.generator.Mapper;

public class PurgeFragments extends ExploreSubcommandData {

	public PurgeFragments() {
		super(Constants.PURGE_FRAGMENTS, "Purge a number of relic fragments to gain a relic (can use unknown fragments)");
		addOptions(typeOption.setRequired(true), 
				new OptionData(OptionType.INTEGER, Constants.COUNT, "Number of fragments to purge (default 3, use this for NRA or black market forgery)"));
	}

	@Override
	public void execute(SlashCommandInteractionEvent event) {
		Map activeMap = getActiveMap();
		Player activePlayer = activeMap.getPlayer(getUser().getId());
		String color = event.getOption(Constants.TRAIT).getAsString();
		OptionMapping countOption = event.getOption(Constants.COUNT);
		int count = 3;
		if (countOption != null) {
			count = countOption.getAsInt();
		}
		List<String> fragments = new ArrayList<String>();
		List<String> unknowns = new ArrayList<String>();
		List<String> relics = activePlayer.getFragments();
		for (String id : relics) {
			String[] cardInfo = Mapper.getExplore(id).split(";");
			if (cardInfo[1].equalsIgnoreCase(color)) {
				fragments.add(id);
			} else if (cardInfo[1].equalsIgnoreCase(Constants.FRONTIER)) {
				unknowns.add(id);
			}
		}
		
		while (fragments.size() > count) {
			fragments.remove(0);
		}
		
		while (fragments.size() < count) {
			if (unknowns.size() == 0) {
				MessageHelper.replyToMessage(event, "Not enough fragments");
				return;
			}
			fragments.add(unknowns.remove(0));
		}
		
		for (String id : fragments) {
			activePlayer.removeFragment(id);
		}
		
		MessageHelper.replyToMessage(event, "Fragments purged: "+fragments.toString());
		MapSaveLoadManager.saveMap(activeMap);
	}
	
}
