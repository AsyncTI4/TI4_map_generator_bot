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

public class ShuffleExpBackIntoDeck extends ExploreSubcommandData {

	public ShuffleExpBackIntoDeck() {
		super(Constants.SHUFFLE_EXP_BACK_INTO_DECK, "Shuffle an Exploration card back into the deck, including purged cards");
		addOptions(new OptionData(OptionType.STRING, Constants.EXPLORE_CARD_ID, "Explore card ID sent between ()").setRequired(true));
	}

	@Override
	public void execute(SlashCommandInteractionEvent event) {
		Map activeMap = getActiveMap();
		String id = event.getOption(Constants.EXPLORE_CARD_ID).getAsString();
		StringBuilder sb = new StringBuilder();
		String card = Mapper.getExplore(id);
		if(card != null) {
			activeMap.addExplore(id);
			sb.append("Card shuffled into exploration deck: \n(").append(id).append(") ").append(card);
		} else {
			sb.append("No such Exploration Card ID found, please retry");
		}
		MessageHelper.replyToMessage(event, sb.toString());
	}

}
