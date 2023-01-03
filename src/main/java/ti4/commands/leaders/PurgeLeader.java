package ti4.commands.leaders;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Leader;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class PurgeLeader extends LeaderAction {
    public PurgeLeader() {
        super(Constants.PURGE_LEADER, "Purge leader");
    }

    @Override
    void action(SlashCommandInteractionEvent event, String leader, Map activeMap, Player player) {
        boolean purged = player.removeLeader(leader);
        if (purged) {
            Leader playerLeader = player.getLeader(leader);
            StringBuilder message = new StringBuilder(Helper.getPlayerRepresentation(event, player)).append(" purged ").append(Helper.getPlayerFactionLeaderEmoji(player, leader)).append(playerLeader.getName());
            MessageHelper.sendMessageToChannel(event.getChannel(), message.toString());
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Leader not found");
        }
    }
}
