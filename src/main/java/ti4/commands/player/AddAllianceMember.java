package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.CommandHelper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class AddAllianceMember extends PlayerSubcommandData {
    public AddAllianceMember() {
        super(Constants.ADD_ALLIANCE_MEMBER, "Add an alliance member");
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TARGET_FACTION_OR_COLOR,
            "Faction or Color with which you are in an alliance").setAutoComplete(true).setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = CommandHelper.getPlayerFromEvent(game, event);
        if (player == null || player.isNotRealPlayer()) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }
        Player otherPlayer = CommandHelper.getOtherPlayerFromEvent(game, event);
        if (otherPlayer == null || otherPlayer.isNotRealPlayer()) {
            MessageHelper.sendMessageToEventChannel(event, "Player to add to the alliance could not be found");
            return;
        }
        String currentMembers = otherPlayer.getAllianceMembers();
        if (!otherPlayer.getAllianceMembers().contains(player.getFaction())) {
            otherPlayer.addAllianceMember(player.getFaction() + player.getAllianceMembers());
        }
        if (!player.getAllianceMembers().contains(otherPlayer.getFaction())) {
            player.addAllianceMember(otherPlayer.getFaction() + currentMembers);
        }

        for (String leaderID : otherPlayer.getLeaderIDs()) {
            if (leaderID.contains("commander") && !player.hasLeader(leaderID)) {
                if (!leaderID.contains("mahact") && !player.hasAbility("edict")) {
                    player.addLeader(leaderID);
                    player.getLeader(leaderID).ifPresent(leader -> leader.setLocked(false));
                }
                otherPlayer.getLeader(leaderID).ifPresent(leader -> leader.setLocked(false));
            }
        }
        for (String leaderID : player.getLeaderIDs()) {
            if (leaderID.contains("commander") && !otherPlayer.hasLeader(leaderID)) {
                if (!leaderID.contains("mahact") && !otherPlayer.hasAbility("edict")) {
                    otherPlayer.addLeader(leaderID);
                    otherPlayer.getLeader(leaderID).ifPresent(leader -> leader.setLocked(false));
                }
                player.getLeader(leaderID).ifPresent(leader -> leader.setLocked(false));
            }
        }
        if (player.hasAbility("edict")) {
            player.addMahactCC(otherPlayer.getColor());
            MessageHelper.sendMessageToChannel(otherPlayer.getCorrectChannel(), otherPlayer.getRepresentation() + " heads up, in an alliance game with Mahact as an alliance partner, you do not get mahacts alliance, they in fact grab a CC from your pool. This is because Dane thought Mahact's commander was too powerful to share.");
        }
        if (otherPlayer.hasAbility("edict")) {
            otherPlayer.addMahactCC(player.getColor());
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " heads up, in an alliance game with Mahact as an alliance partner, you do not get mahacts alliance, they in fact grab a CC from your pool. This is because Dane thought Mahact's commander was too powerful to share.");

        }
        String msg = player.getRepresentationUnfogged() + otherPlayer.getRepresentationUnfogged()
            + " pinging you into this";
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg);
        MessageHelper.sendMessageToChannel(otherPlayer.getCardsInfoThread(), msg);

        MessageHelper.sendMessageToEventChannel(event, "Added " + otherPlayer.getFaction() + " as part of " + player.getFaction()
            + "'s alliance. This works 2 ways");
    }
}
