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

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.stream.Collectors;

public class Explore implements Command {

	@Override
	public String getActionID() {
		return Constants.EXPLORE;
	}
	
	public String getActionDescription() {
		return "Draw an explore card from the specified deck.";
	}

	@Override
	public boolean accept(SlashCommandInteractionEvent event) {
		return event.getName().equals(getActionID());
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

	@Override
	public void registerCommands(CommandListUpdateAction commands) {
		OptionData type = new OptionData(OptionType.STRING, "type", "Cultural, Industrial, Hazardous, or Frontier.").setRequired(true);
		commands.addCommands(Commands.slash(getActionID(),getActionDescription()).addOptions(type));
	}
	
	
}
