package ti4.commands.leaders;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class PurgeLeader extends LeaderAction {
    public PurgeLeader() {
        super(Constants.PURGE_LEADER, "Purge leader");
    }

    @Override
    void action(SlashCommandInteractionEvent event, String leaderID, Game activeGame, Player player) {
        Leader playerLeader = player.unsafeGetLeader(leaderID);
        boolean purged = player.removeLeader(playerLeader);
        if (purged) {
            MessageHelper.sendMessageToEventChannel(event, Emojis.getFactionLeaderEmoji(playerLeader));
          String message = player.getRepresentation() +
              " purged " + Helper.getLeaderShortRepresentation(playerLeader);
            MessageHelper.sendMessageToEventChannel(event, message);
        } else {
            MessageHelper.sendMessageToEventChannel(event, "Leader not found");
        }
    }
}
