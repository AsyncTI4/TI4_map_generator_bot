package ti4.commands.explore;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
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
		addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setAutoComplete(true));
	}

	@Override
	public void execute(SlashCommandInteractionEvent event) {
		Map activeMap = getActiveMap();
		Player activePlayer = activeMap.getPlayer(getUser().getId());
		activePlayer = Helper.getPlayer(activeMap, activePlayer, event);
		if (activePlayer == null){
			MessageHelper.replyToMessage(event, "Player not found in game.");
			return;
		}
		String color = event.getOption(Constants.TRAIT).getAsString();
		OptionMapping countOption = event.getOption(Constants.COUNT);
		int count = 3;
		if (countOption != null) {
			count = countOption.getAsInt();
		}
		List<String> fragmentsToPurge = new ArrayList<String>();
		List<String> unknowns = new ArrayList<String>();
		ArrayList<String> playerFragments = activePlayer.getFragments();
		for (String id : playerFragments) {
			String[] cardInfo = Mapper.getExplore(id).split(";");
			if (cardInfo[1].equalsIgnoreCase(color)) {
				fragmentsToPurge.add(id);
			} else if (cardInfo[1].equalsIgnoreCase(Constants.FRONTIER)) {
				unknowns.add(id);
			}
		}
		
		while (fragmentsToPurge.size() > count) {
			fragmentsToPurge.remove(0);
		}
		
		while (fragmentsToPurge.size() < count) {
			if (unknowns.size() == 0) {
				MessageHelper.replyToMessage(event, "Not enough fragments");
				return;
			}
			fragmentsToPurge.add(unknowns.remove(0));
		}
		
		for (String id : fragmentsToPurge) {
			activePlayer.removeFragment(id);
		}
		
		MessageHelper.replyToMessage(event, "Fragments purged: "+fragmentsToPurge.toString());
	}
	
}
