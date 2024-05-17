package ti4.commands.leaders;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class LockLeader extends LeaderAction {
    public LockLeader() {
        super(Constants.LOCK_LEADER, "Lock leader");
    }

    @Override
    void action(SlashCommandInteractionEvent event, String leaderID, Game activeGame, Player player) {
        Leader playerLeader = player.unsafeGetLeader(leaderID);
        if (playerLeader == null) {
            MessageHelper.sendMessageToEventChannel(event, "Leader not found");
            return;
        }
        playerLeader.setLocked(true);
        MessageHelper.sendMessageToEventChannel(event, "Leader '" + playerLeader.getId() + "'' locked");
    }
}
