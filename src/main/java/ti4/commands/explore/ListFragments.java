package ti4.commands.explore;

import org.jetbrains.annotations.NotNull;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
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
		MessageHelper.replyToMessage(event, player.getFragments().toString());
	}
	
}
