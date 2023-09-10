package ti4.commands.leaders;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;

public class PurgeLeader extends LeaderAction {
    public PurgeLeader() {
        super(Constants.PURGE_LEADER, "Purge leader");
    }

    @Override
    void action(SlashCommandInteractionEvent event, String leaderID, Game activeGame, Player player) {
        Leader playerLeader = player.unsafeGetLeader(leaderID);
        boolean purged = player.removeLeader(playerLeader);
        if (purged) {
            sendMessage(Helper.getFactionLeaderEmoji(playerLeader));
          String message = Helper.getPlayerRepresentation(player, activeGame) +
              " purged " + Helper.getLeaderShortRepresentation(playerLeader);
            sendMessage(message);
        } else {
            sendMessage("Leader not found");
        }
    }
}
