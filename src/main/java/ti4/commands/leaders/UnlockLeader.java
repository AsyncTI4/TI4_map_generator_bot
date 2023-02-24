package ti4.commands.leaders;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Leader;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class UnlockLeader extends LeaderAction {
    public UnlockLeader() {
        super(Constants.UNLOCK_LEADER, "Unlock leader");
    }

    @Override
    void action(SlashCommandInteractionEvent event, String leader, Map activeMap, Player player) {
        Leader playerLeader = player.getLeader(leader);
        if (playerLeader != null){
            playerLeader.setLocked(false);
            editReplyMessage(event, Helper.getFactionLeaderEmoji(player, playerLeader));
            StringBuilder message = new StringBuilder(Helper.getPlayerRepresentation(event, player))
                    .append(" unlocked ")
                    .append(Helper.getLeaderFullRepresentation(player, playerLeader));
            MessageHelper.sendMessageToChannel(event.getChannel(), message.toString());
            if (playerLeader.isExhausted()){
                MessageHelper.sendMessageToChannel(event.getChannel(), "Leader is also exhausted");
            }
        } else {
            editReplyMessage(event, "Leader not found");
        }
    }
}
