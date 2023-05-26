package ti4.commands.leaders;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Leader;
import ti4.map.Map;
import ti4.map.Player;

public class PurgeLeader extends LeaderAction {
    public PurgeLeader() {
        super(Constants.PURGE_LEADER, "Purge leader");
    }

    @Override
    void action(SlashCommandInteractionEvent event, String leader, Map activeMap, Player player) {
        Leader playerLeader = player.getLeader(leader);
        boolean purged = player.removeLeader(playerLeader);
        if (purged) {
            sendMessage(Helper.getFactionLeaderEmoji(playerLeader));
            StringBuilder message = new StringBuilder(Helper.getPlayerRepresentation(player, activeMap))
                    .append(" purged ")
                    .append(Helper.getLeaderFullRepresentation(playerLeader));
            sendMessage(message.toString());
        } else {
            sendMessage("Leader not found");
        }
    }
}
