package ti4.commands.explore;

import java.util.ArrayList;
import java.util.StringTokenizer;

import org.jetbrains.annotations.NotNull;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.cards.CardsSubcommandData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class ExpInfo extends ExploreSubcommandData {

	public ExpInfo() {
		super(Constants.EXP_INFO, "Display cards in exploration decks and discards.");
		addOptions(new OptionData(OptionType.STRING, Constants.EXP_TYPE, Constants.EXP_TYPE_DESCRIPTION));
	}

	@Override
	public void execute(SlashCommandInteractionEvent event) {
		Map activeMap = getActiveMap();
		ArrayList<String> types = new ArrayList<String>();
		OptionMapping reqType = event.getOption(Constants.EXP_TYPE);
		if (reqType != null) {
			types.add(reqType.getAsString());
		} else {
			types.add(Constants.CULTURAL);
			types.add(Constants.INDUSTRIAL);
			types.add(Constants.HAZARDOUS);
			types.add(Constants.FRONTIER);
		}
		for (String currentType : types) {
			StringBuilder info = new StringBuilder();
			ArrayList<String> deck = activeMap.getExploreDeck(currentType);
			ArrayList<String> discard = activeMap.getExploreDiscard(currentType);
			info.append("**").append(currentType.toUpperCase()).append(" EXPLORE DECK**\n").append(listNames(deck)).append("\n");
			info.append("**").append(currentType.toUpperCase()).append(" EXPLORE DISCARD**\n").append(listNames(discard)).append("\n");
			MessageHelper.replyToMessage(event, info.toString());
		}
	}
	
	private String listNames(ArrayList<String> deck) {
		StringBuilder sb = new StringBuilder();
		for (String cardID : deck) {
			StringTokenizer cardInfo = new StringTokenizer(Mapper.getExplore(cardID), ";");
			String name = cardInfo.nextToken();
			sb.append("(").append(cardID).append(") ").append(name).append("\n");
		}
		return sb.toString();
	}
	
}
