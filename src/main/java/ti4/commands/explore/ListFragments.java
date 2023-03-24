package ti4.commands.explore;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class ListFragments extends ExploreSubcommandData {

	public ListFragments() {
		super(Constants.LIST_FRAGMENTS, "List the IDs of relic fragments in your play area");
	}

	@Override
	public void execute(SlashCommandInteractionEvent event) {
		Map activeMap = getActiveMap();
		Player player = activeMap.getPlayer(getUser().getId());
		player = Helper.getGamePlayer(activeMap, player, event, null);

		sendMessage(player.getFragments().toString());
	}
	
}
