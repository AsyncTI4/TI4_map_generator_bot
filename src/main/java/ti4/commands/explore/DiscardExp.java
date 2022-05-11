package ti4.commands.explore;

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

public class DiscardExp extends ExploreSubcommandData {

	public DiscardExp() {
		super(Constants.DISCARD_EXP, "Discard an Exploration Card from the deck.");
		addOptions(new OptionData(OptionType.STRING, Constants.EXPLORE_CARD_ID, "Explore card ID sent between ()").setRequired(true));
	}

	@Override
	public void execute(SlashCommandInteractionEvent event) {
		Map activeMap = getActiveMap();
		String id = event.getOption(Constants.EXPLORE_CARD_ID).getAsString();
		StringBuilder sb = new StringBuilder();
		String card = Mapper.getExplore(id);
		if(card != null) {
			activeMap.discardExplore(id);
			sb.append("Card discarded: \n(").append(id).append(") ").append(card);
		} else {
			sb.append("No such Exploration Card ID found, please retry");
		}
		MessageHelper.replyToMessage(event, sb.toString());
	}
	
}
