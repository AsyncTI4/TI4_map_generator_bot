package ti4.commands.leaders;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.map.Leader;
import ti4.map.Game;
import ti4.map.Player;

public class HeroUnplay extends LeaderAction {
    public HeroUnplay() {
        super(Constants.INACTIVE_LEADER, "Set leader as inactive");
    }

    @Override
    void action(SlashCommandInteractionEvent event, String leaderID, Game activeGame, Player player) {
        Leader playerLeader = player.unsafeGetLeader(leaderID);
        if (playerLeader != null){
            playerLeader.setActive(false);
            sendMessage("Leader deactivated/unplayed");
        } else {
            sendMessage("Leader not found");
        }
    }
}
