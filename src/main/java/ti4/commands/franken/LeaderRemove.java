package ti4.commands.franken;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class LeaderRemove extends LeaderAddRemove {

    public LeaderRemove() {
        super(Constants.LEADER_REMOVE, "Remove a leader from your faction");
    }

    @Override
    public void doAction(Player player, List<String> leaderIDs, SlashCommandInteractionEvent event) {
        removeLeaders(event, player, leaderIDs);
    }

    public static void removeLeaders(GenericInteractionCreateEvent event, Player player, List<String> leaderIDs) {
        StringBuilder sb = new StringBuilder(player.getRepresentation()).append(" removed leaders:\n");
        for (String leaderID : leaderIDs) {
            if (!player.hasLeader(leaderID)) {
                sb.append("> ").append(leaderID).append(" (player did not have this leader)");
            } else {
                Leader leader = new Leader(leaderID);
                sb.append("> ").append(Helper.getLeaderFullRepresentation(leader));
            }
            sb.append("\n");
            player.removeLeader(leaderID);
        }
        MessageHelper.sendMessageToEventChannel(event, sb.toString());
    }
}
