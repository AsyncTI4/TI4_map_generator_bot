package ti4.commands.leaders;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.map.Leader;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class SetHeroActiveLeader extends LeaderAction {
    public SetHeroActiveLeader() {
        super(Constants.ACTIVE_LEADER, "Set leader as active");
    }

    @Override
    void action(SlashCommandInteractionEvent event, String leader, Map activeMap, Player player) {
        Leader playerLeader = player.getLeader(leader);
        if ("letnev".equals(player.getFaction()) || "nomad".equals(player.getFaction())) {
            if (playerLeader != null && Constants.HERO.equals(playerLeader.getName())) {
                playerLeader.setActive(true);
                MessageHelper.sendMessageToChannel(event.getChannel(), "Leader will be PURGED after status cleanup");
                return;
            } else {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Leader not found");
                return;
            }
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), "Just Letnev and Nomad can activate Hero. To unlock hero use unlock subcommand");
    }
}
