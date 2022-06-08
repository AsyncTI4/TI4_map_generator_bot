package ti4.commands.relic;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class DrawRelic extends RelicSubcommandData {

	public DrawRelic() {
		super(Constants.DRAW, "Draw a relic");
	}

	@Override
	public void execute(SlashCommandInteractionEvent event) {
		Map activeMap = getActiveMap();
		Player activePlayer = activeMap.getPlayer(getUser().getId());
		String relicID = activeMap.drawRelic();
		activePlayer.addRelic(relicID);
		String[] relicData = Mapper.getRelic(relicID).split(";");
		String relicString = "("+relicID+") " + relicData[0] + " - " + relicData[1];
		MessageHelper.replyToMessage(event, relicString);
	}

}
