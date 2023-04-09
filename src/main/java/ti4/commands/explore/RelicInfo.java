package ti4.commands.explore;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class RelicInfo extends ExploreSubcommandData {
    public RelicInfo() {
		super(Constants.RELIC_INFO, "Send relic information to your Cards Info channel");
	}

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeMap, player, event, null);
        player = Helper.getPlayer(activeMap, player, event);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeMap, Helper.getPlayerRepresentation(event, player));
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeMap, getRelicInfoText(player));

    }

    private String getRelicInfoText(Player player) {
        List<String> playerRelics = player.getRelics();
        StringBuilder sb = new StringBuilder("__**Relic Info**__\n");
        if (playerRelics == null || playerRelics.isEmpty()) {
            sb.append("> No Relics");
            return sb.toString();
        }
        for (String relicID : playerRelics) {
            sb.append(Helper.getRelicRepresentation(relicID));
        }
        return sb.toString();
    }
}
