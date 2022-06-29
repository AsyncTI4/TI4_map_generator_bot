package ti4.commands.leaders;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.map.Leader;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class RefreshLeader extends LeaderAction {
    public RefreshLeader() {
        super(Constants.REFRESH_LEADER, "Refresh leader");
    }

    @Override
    void action(SlashCommandInteractionEvent event, String leader, Map activeMap, Player player) {
        Leader playerLeader = player.getLeader(leader);
        if (playerLeader != null){
            if (playerLeader.isLocked()){
                MessageHelper.sendMessageToChannel(event.getChannel(), "Leader is locked");
                return;
            }
            refreshLeader(player, playerLeader);
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Leader not found");
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
