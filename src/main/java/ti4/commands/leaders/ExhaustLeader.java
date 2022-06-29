package ti4.commands.leaders;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.map.Leader;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class ExhaustLeader extends LeaderAction {
    public ExhaustLeader() {
        super(Constants.EXHAUST_LEADER, "Exhaust leader");
    }

    @Override
    void action(SlashCommandInteractionEvent event, String leader, Map activeMap, Player player) {
        Leader playerLeader = player.getLeader(leader);
        if (playerLeader != null){
            playerLeader.setExhausted(true);
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Leader not found");
        }
    }
}
