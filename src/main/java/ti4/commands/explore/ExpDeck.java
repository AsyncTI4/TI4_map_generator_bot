package ti4.commands.explore;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;
import ti4.commands.cardspn.*;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.MapSaveLoadManager;
import ti4.message.MessageHelper;

public class ExpDeck extends ExploreSubcommandData {
	public ExpDeck() {
		super(Constants.EXP_DECK, "Draw from a specified Exploration Deck.");
		addOptions(new OptionData(OptionType.STRING, Constants.EXP_TYPE, Constants.EXP_TYPE_DESCRIPTION).setRequired(true));
	}
	
	@Override
	public String getActionID() {
		//TODO
		return "";
	}

	@Override
	public void execute(SlashCommandInteractionEvent event) {
		String userID = event.getUser().getId();
		Map activeMap = MapManager.getInstance().getUserActiveMap(userID);
		String cardID = activeMap.drawExplore(event.getOptions().get(0).getAsString().toLowerCase());
		
		StringBuilder sb = new StringBuilder();
		String cardInfo = Mapper.getExplore(cardID);
		sb.append("(").append(cardID).append(") ").append(cardInfo);
		MessageHelper.replyToMessage(event, sb.toString());
		MessageHelper.sendMessageToChannel(event.getChannel(), "Card has been discarded. Resolve effects and/or purge manually.");
	}
}
