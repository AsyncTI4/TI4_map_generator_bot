package ti4.service.leader;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.LeaderModel;
import ti4.service.emoji.LeaderEmojis;
import ti4.service.info.CardsInfoService;

@UtilityClass
public class UnlockLeaderService {

    public static void unlockLeader(String leaderID, Game game, Player player) {
        Leader playerLeader = player.unsafeGetLeader(leaderID);
        MessageChannel channel = game.getMainGameChannel();
        if (game.isFowMode()) {
            channel = player.getPrivateChannel();
        }

        if (playerLeader == null) {
            MessageHelper.sendMessageToChannel(channel, "Leader not found");
            return;
        }
        playerLeader.setLocked(false);

        LeaderModel leaderModel = playerLeader.getLeaderModel().orElse(null);
        boolean showFlavourText = Constants.VERBOSITY_VERBOSE.equals(game.getOutputVerbosity());

        if (leaderModel != null) {
            MessageHelper.sendMessageToChannel(channel, player.getRepresentation() + " has unlocked their " + leaderModel.getType() + ".");
            channel.sendMessageEmbeds(leaderModel.getRepresentationEmbed(false, true, true, showFlavourText)).queue();
        } else {
            MessageHelper.sendMessageToChannel(channel, LeaderEmojis.getLeaderEmoji(playerLeader).toString());
            String message = player.getRepresentation() + " unlocked " + Helper.getLeaderFullRepresentation(playerLeader) + ".";
            MessageHelper.sendMessageToChannel(channel, message);
        }

        if (leaderID.contains("bentorcommander")) {
            player.setCommoditiesTotal(player.getCommoditiesTotal() + 1);
            MessageHelper.sendMessageToChannel(channel, player.getFactionEmoji() + ", your commodity value has been set to " + player.getCommoditiesTotal() + ".");
        }

        if (leaderID.contains("naalucommander")) {
            CardsInfoService.sendVariousAdditionalButtons(game, player);
            MessageHelper.sendMessageToChannel(channel, player.getRepresentationUnfogged() + ", you may use M'aban, the Naalu Commander, via button in your `#cards-info` thread.");
        }

        if (leaderID.equals("xxchahero")) {
            if (game.getPhaseOfGame().contains("status")) {
                MessageHelper.sendMessageToChannel(channel,
                    "Reminder, " + player.getRepresentationUnfogged() + ", that officially Xxekir Grom remains locked until after both objectives have been scored;"
                        + " you cannot use the ability to pay for any requirements of the unlocking objectives (if they're spendies).");
            } else {
                MessageHelper.sendMessageToChannel(channel,
                    "Reminder, " + player.getRepresentationUnfogged() + ", that officially Xxekir Grom remains locked until after the objective has been scored;"
                        + " you cannot use the ability to pay for any requirements of the unlocking objective (if it's a spendie).");
            }
        }

        if (playerLeader.isExhausted()) {
            MessageHelper.sendMessageToChannel(channel, "Leader is also exhausted");
        }
    }
}
