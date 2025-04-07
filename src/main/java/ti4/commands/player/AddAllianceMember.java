package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.CommandHelper;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

class AddAllianceMember extends GameStateSubcommand {

    public AddAllianceMember() {
        super(Constants.ADD_ALLIANCE_MEMBER, "Add an alliance member", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.TARGET_FACTION_OR_COLOR,
            "Faction or Color with which you are in an alliance").setAutoComplete(true).setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
        if (!player.isRealPlayer()) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }
        Player otherPlayer = CommandHelper.getOtherPlayerFromEvent(game, event);
        if (otherPlayer == null) {
            MessageHelper.replyToMessage(event, "Unable to determine who the target player is.");
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
                    if(!leaderID.contains("celdauri")){
                        game.addFakeCommander(leaderID);
                    }
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
            MessageHelper.sendMessageToChannel(otherPlayer.getCorrectChannel(), "Heads up, " + otherPlayer.getRepresentation()
                + ", in an alliance game with Mahact as an alliance partner, you do not get Mahact's alliance. "
                + " They instead grab a command token from your reinforcements and put it in their fleet pool."
                + " This is because Dane thought Mahact's commander was too powerful to share.");
        }
        if (otherPlayer.hasAbility("edict")) {
            otherPlayer.addMahactCC(player.getColor());
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Heads up, " + player.getRepresentation()
                + ", in an alliance game with Mahact as an alliance partner, you do not get Mahact's alliance. "
                + " They instead grab a command token from your reinforcements and put it in their fleet pool."
                + " This is because Dane thought Mahact's commander was too powerful to share.");

        }
        String msg = player.getRepresentationUnfogged() + otherPlayer.getRepresentationUnfogged()
            + " pinging you into this.";
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg);
        MessageHelper.sendMessageToChannel(otherPlayer.getCardsInfoThread(), msg);

        MessageHelper.sendMessageToEventChannel(event, "Added " + otherPlayer.getFaction() + " as part of " + player.getFaction()
            + "'s alliance. This works 2 ways.");
    }
}
