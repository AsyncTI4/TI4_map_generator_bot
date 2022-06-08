package ti4.commands.relic;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class PurgeRelic extends RelicSubcommandData {

	public PurgeRelic() {
		super(Constants.PURGE, "Purge a relic");
		addOptions(new OptionData(OptionType.STRING, Constants.RELIC_ID, "Relic ID sent between ()"));
	}

	@Override
	public void execute(SlashCommandInteractionEvent event) {
		Map activeMap = getActiveMap();
		Player activePlayer = activeMap.getPlayer(getUser().getId());
		String relicId = event.getOption(Constants.RELIC_ID).getAsString();
		if (activePlayer.getRelics().contains(relicId)) {
			activePlayer.removeRelic(relicId);
			String relicName = Mapper.getRelic(relicId).split(";")[0];
			MessageHelper.replyToMessage(event, "Purged relic: "+relicName);
		} else {
			MessageHelper.replyToMessage(event, "Invalid relic ID");
		}
	}
}
