package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class AddAllianceMember extends PlayerSubcommandData {
    public AddAllianceMember() {
        super(Constants.ADD_ALLIANCE_MEMBER, "Add an alliance member");
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR,
            "Faction or Color with which you are in an alliance").setAutoComplete(true).setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = game.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(game, player, event, null);
        if (player == null || player.isNotRealPlayer()) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }
        Player player_ = Helper.getPlayer(game, player, event);
        if (player_ == null || player_.isNotRealPlayer()) {
            MessageHelper.sendMessageToEventChannel(event, "Player to add to the alliance could not be found");
            return;
        }
        String currentMembers = player_.getAllianceMembers();
        if (!player_.getAllianceMembers().contains(player.getFaction())) {
            player_.addAllianceMember(player.getFaction() + player.getAllianceMembers());
        }
        if (!player.getAllianceMembers().contains(player_.getFaction())) {
            player.addAllianceMember(player_.getFaction() + currentMembers);
        }

        for (String leaderID : player_.getLeaderIDs()) {
            if (leaderID.contains("commander") && !player.hasLeader(leaderID)) {
                if (!leaderID.contains("mahact") && !player.hasAbility("edict")) {
                    player.addLeader(leaderID);
                    player.getLeader(leaderID).ifPresent(leader -> leader.setLocked(false));
                }
                player_.getLeader(leaderID).ifPresent(leader -> leader.setLocked(false));
            }
        }
        for (String leaderID : player.getLeaderIDs()) {
            if (leaderID.contains("commander") && !player_.hasLeader(leaderID)) {
                if (!leaderID.contains("mahact") && !player_.hasAbility("edict")) {
                    player_.addLeader(leaderID);
                    player_.getLeader(leaderID).ifPresent(leader -> leader.setLocked(false));
                }
                player.getLeader(leaderID).ifPresent(leader -> leader.setLocked(false));
            }
        }
        if (player.hasAbility("edict")) {
            player.addMahactCC(player_.getColor());
            MessageHelper.sendMessageToChannel(player_.getCorrectChannel(), player_.getRepresentation() + " heads up, in an alliance game with Mahact as an alliance partner, you do not get to share their commander ability."
                + " Instead, they start with 1 of your command tokens in their fleet pool."
                + " This is because Dane thought that Il Na Viroset, the Mahact commander, was too powerful to share (or did so for thematic reasons).");
        }
        if (player_.hasAbility("edict")) {
            player_.addMahactCC(player.getColor());
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " heads up, in an alliance game with Mahact as an alliance partner, you do not get to share their commander ability."
                + " Instead, they start with 1 of your command tokens in their fleet pool."
                + " This is because Dane thought that Il Na Viroset, the Mahact commander, was too powerful to share (or did so for thematic reasons).");

        }
        String msg = player.getRepresentation(true, true) + player_.getRepresentation(true, true)
            + " pinging you into this.";
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg);
        MessageHelper.sendMessageToChannel(player_.getCardsInfoThread(), msg);

        MessageHelper.sendMessageToEventChannel(event, player.getFaction() + " has entered into an alliance with " + player_.getFaction() + ".");
    }
}
