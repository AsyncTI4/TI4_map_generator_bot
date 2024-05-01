package ti4.commands.franken;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class LeaderAdd extends LeaderAddRemove {
    public LeaderAdd() {
        super(Constants.LEADER_ADD, "Add a leader to your faction");
    }
    
    @Override
    public void doAction(Player player, List<String> leaderIDs) {
        addLeaders(getEvent(), player, leaderIDs);
    }

    public static void addLeaders(GenericInteractionCreateEvent event, Player player, List<String> leaderIDs) {
        StringBuilder sb = new StringBuilder(player.getRepresentation()).append(" added leaders:\n");
        for (String leaderID : leaderIDs ){
            sb.append(getAddLeaderText(player, leaderID));
            player.addLeader(leaderID);
        }
        MessageHelper.sendMessageToEventChannel(event, sb.toString());
    }

    public static String getAddLeaderText(Player player, String leaderID) {
        StringBuilder sb = new StringBuilder();
        if (player.hasLeader(leaderID)) {
            sb.append("> ").append(leaderID).append(" (player had this leader)");
        } else {
            Leader leader = new Leader(leaderID);
            sb.append("> ").append(Helper.getLeaderFullRepresentation(leader));
        }
        sb.append("\n");
        return sb.toString();
    }
}
