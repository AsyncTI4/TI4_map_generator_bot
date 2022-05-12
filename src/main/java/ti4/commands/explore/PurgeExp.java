package ti4.commands.explore;

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

public class PurgeExp extends ExploreSubcommandData {

	public PurgeExp() {
		super(Constants.PURGE, "Remove an Exploration card from the game.");
		addOptions(new OptionData(OptionType.STRING, Constants.EXPLORE_CARD_ID, "Explore card ID sent between ()").setRequired(true));
	}

	@Override
	public void execute(SlashCommandInteractionEvent event) {
		Map activeMap = getActiveMap();
		String ids = event.getOption(Constants.EXPLORE_CARD_ID).getAsString().replaceAll(" ", "");
		StringTokenizer idTokenizer = new StringTokenizer(ids, ",");
		int count = idTokenizer.countTokens();
		for (int i = 0; i < count; i++) {
			StringBuilder sb = new StringBuilder();
			String id = idTokenizer.nextToken();
			String card = Mapper.getExplore(id);
			if(card != null) {
				activeMap.purgeExplore(id);
				sb.append("Exploration card purged: \n").append(displayExplore(id));
			} else {
				activeMap.purgeExplore(id);
				sb.append("Purged id without matching card: ").append(id);
			}
			MessageHelper.replyToMessage(event, sb.toString());
		}
	}
	
}
