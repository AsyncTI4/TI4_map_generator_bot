package ti4.commands.cards;

import org.jetbrains.annotations.NotNull;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class PurgeExp extends CardsSubcommandData {

	public PurgeExp() {
		super(Constants.PURGE_EXP, "Remove an Exploration card from the game.");
		addOptions(new OptionData(OptionType.STRING, Constants.EXPLORE_CARD_ID, "Explore card ID sent between ()").setRequired(true));
	}

	@Override
	public void execute(SlashCommandInteractionEvent event) {
		Map activeMap = getActiveMap();
		String id = event.getOption(Constants.EXPLORE_CARD_ID).getAsString();
		StringBuilder sb = new StringBuilder();
		String card = Mapper.getExplore(id);
		if(card != null) {
			activeMap.purgeExplore(id);
			sb.append("Exploration card purged: \n(").append(id).append(") ").append(card);
		} else {
			sb.append("No such Exploration Card ID found, please retry");
		}
		MessageHelper.replyToMessage(event, sb.toString());
	}
	
}
