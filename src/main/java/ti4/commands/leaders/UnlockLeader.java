package ti4.commands.leaders;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
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
        unlockLeader(event, leader, activeMap, player);
    }

    public void unlockLeader(SlashCommandInteractionEvent event, String leader, Map activeMap, Player player) {
        Leader playerLeader = player.getLeader(leader);
        MessageChannel channel = activeMap.getMainGameChannel();
        if (channel == null || activeMap.isFoWMode()) channel = event.getChannel();
        if (playerLeader != null){
            playerLeader.setLocked(false);
            MessageHelper.sendMessageToChannel(channel, Helper.getFactionLeaderEmoji(player, playerLeader));
            StringBuilder message = new StringBuilder(Helper.getPlayerRepresentation(event, player))
                    .append(" unlocked ")
                    .append(Helper.getLeaderFullRepresentation(player, playerLeader));
            MessageHelper.sendMessageToChannel(channel, message.toString());
            if (playerLeader.isExhausted()){
                MessageHelper.sendMessageToChannel(channel, "Leader is also exhausted");
            }
        } else {
            MessageHelper.sendMessageToChannel(channel, "Leader not found");
        }
    }

    public void unlockLeader(ButtonInteractionEvent event, String leader, Map activeMap, Player player) {
        Leader playerLeader = player.getLeader(leader);
        MessageChannel channel = activeMap.getMainGameChannel();
        if (channel == null || activeMap.isFoWMode()) channel = event.getChannel();
        if (playerLeader != null){
            playerLeader.setLocked(false);
            MessageHelper.sendMessageToChannel(channel, Helper.getFactionLeaderEmoji(player, playerLeader));
            StringBuilder message = new StringBuilder(Helper.getPlayerRepresentation(event, player))
                    .append(" unlocked ")
                    .append(Helper.getLeaderFullRepresentation(player, playerLeader));
             MessageHelper.sendMessageToChannel(channel, message.toString());
            if (playerLeader.isExhausted()){
                 MessageHelper.sendMessageToChannel(channel, "Leader is also exhausted");
            }
        } else {
             MessageHelper.sendMessageToChannel(channel, "Leader not found");
        }
    }
}
