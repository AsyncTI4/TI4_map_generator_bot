package ti4.commands.franken;

import java.util.List;

import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class LeaderAdd extends LeaderAddRemove {
    public LeaderAdd() {
        super(Constants.LEADER_ADD, "Add a leader to your faction");
    }
    
    @Override
    public void doAction(Player player, List<String> leaderIDs) {
        StringBuilder sb = new StringBuilder(player.getRepresentation()).append(" added leaders:\n");
        for (String leaderID : leaderIDs ){
            if (player.hasLeader(leaderID)) {
                sb.append("> ").append(leaderID).append(" (player had this leader)");
            } else {
                Leader leader = new Leader(leaderID);
                sb.append("> ").append(Helper.getLeaderFullRepresentation(leader));
            }
            sb.append("\n");
            player.addLeader(leaderID);
        }
        MessageHelper.sendMessageToEventChannel(getEvent(), sb.toString());
    }

    public void addLeader(Player player, String leaderID, Game activeGame) {
        StringBuilder sb = new StringBuilder(player.getRepresentation()).append(" added leaders:\n");

        if (player.hasLeader(leaderID)) {
            sb.append("> ").append(leaderID).append(" (player had this leader)");
        } else {
            Leader leader = new Leader(leaderID);
            sb.append("> ").append(Helper.getLeaderFullRepresentation(leader));
        }
        sb.append("\n");
        player.addLeader(leaderID);
        
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), sb.toString());
    }
}
