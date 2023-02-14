package ti4.commands.leaders;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Leader;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class RefreshLeader extends LeaderAction {
    public RefreshLeader() {
        super(Constants.REFRESH_LEADER, "Ready leader");
    }

    @Override
    void action(SlashCommandInteractionEvent event, String leader, Map activeMap, Player player) {
        Leader playerLeader = player.getLeader(leader);    
        if (playerLeader != null){
            if (playerLeader.isLocked()){
                editReplyMessage("Leader is locked");
                return;
            }
            int tgCount = playerLeader.getTgCount();
            refreshLeader(player, playerLeader);
            editReplyMessage(Helper.getPlayerFactionLeaderEmoji(player, leader));
            StringBuilder message = new StringBuilder(Helper.getPlayerRepresentation(event, player))
                    .append(" readied ")
                    .append(playerLeader.getId()). append(" ")
                    .append(playerLeader.getName());
            if (tgCount > 0) {
                message.append(" - ").append(String.valueOf(tgCount)).append(Emojis.tg).append(" transferred from leader to player");
            }
            MessageHelper.sendMessageToChannel(event.getChannel(), message.toString());
        } else {
            editReplyMessage("Leader not found");
        }
    }

    public static void refreshLeader(Player player, Leader playerLeader) {
        int tgCount = playerLeader.getTgCount();
        playerLeader.setExhausted(false);
        if (tgCount > 0) {
            int tg = player.getTg();
            tg += tgCount;
            player.setTg(tg);
            playerLeader.setTgCount(0);
        }
    }
}
