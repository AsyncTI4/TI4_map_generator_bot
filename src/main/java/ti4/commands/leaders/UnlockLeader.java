package ti4.commands.leaders;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.cardspn.PNInfo;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.LeaderModel;

public class UnlockLeader extends LeaderAction {
    public UnlockLeader() {
        super(Constants.UNLOCK_LEADER, "Unlock leader");
    }

    @Override
    void action(SlashCommandInteractionEvent event, String leaderID, Game activeGame, Player player) {
        unlockLeader(event, leaderID, activeGame, player);
    }

    public static void unlockLeader(GenericInteractionCreateEvent event, String leaderID, Game activeGame, Player player) {
        Leader playerLeader = player.unsafeGetLeader(leaderID);
        MessageChannel channel = activeGame.getMainGameChannel();
        if (activeGame.isFoWMode())
            channel = player.getPrivateChannel();

        if (playerLeader == null) {
            MessageHelper.sendMessageToChannel(channel, "Leader not found");
            return;
        }
        playerLeader.setLocked(false);

        LeaderModel leaderModel = playerLeader.getLeaderModel().orElse(null);

        boolean showFlavourText = Constants.VERBOSITY_VERBOSE.equals(activeGame.getOutputVerbosity());
        
        if (leaderModel != null) {
            MessageHelper.sendMessageToChannel(channel, player.getRepresentation() + " unlocked:");
            channel.sendMessageEmbeds(leaderModel.getRepresentationEmbed(false, true, true, showFlavourText)).queue();
        } else {
            MessageHelper.sendMessageToChannel(channel, Emojis.getFactionLeaderEmoji(playerLeader));
            String message = player.getRepresentation() + " unlocked " + Helper.getLeaderFullRepresentation(playerLeader);
            MessageHelper.sendMessageToChannel(channel, message);
        }
        if(leaderID.contains("bentorcommander")){
            player.setCommoditiesTotal(player.getCommoditiesTotal()+1);
            MessageHelper.sendMessageToChannel(channel, ButtonHelper.getIdent(player)+"Set Commodity Total to "+player.getCommoditiesTotal());
        }
        if(leaderID.contains("naalucommander")){
            PNInfo.sendPromissoryNoteInfo(activeGame, player, false);
            MessageHelper.sendMessageToChannel(channel, player.getRepresentation(true, true)+ " you can use Naalu Commander via button in your cards info thread");

        }
        

        if (playerLeader.isExhausted()) {
            MessageHelper.sendMessageToChannel(channel, "Leader is also exhausted");
        }
    }
}
