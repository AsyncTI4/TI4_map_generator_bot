package ti4.commands.leaders;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.map.Leader;
import ti4.map.Map;
import ti4.map.Player;

public class LockLeader extends LeaderAction {
    public LockLeader() {
        super(Constants.LOCK_LEADER, "Lock leader");
    }

    @Override
    void action(SlashCommandInteractionEvent event, String leaderID, Map activeMap, Player player) {
        Leader playerLeader = player.getLeader(leaderID);
        if (playerLeader == null) {
            sendMessage("Leader not found");
            return;
        }
        playerLeader.setLocked(true);
        sendMessage("Leader '" + playerLeader.getId() + "'' locked");
    }
}
