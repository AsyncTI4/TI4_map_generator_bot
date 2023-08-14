package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Leader;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class AddAllianceMember extends PlayerSubcommandData {
    public AddAllianceMember() {
        super(Constants.ADD_ALLIANCE_MEMBER, "Add an alliance member");
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color with which you are in an alliance").setAutoComplete(true).setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeMap, player, event, null);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }
        Player player_ = Helper.getPlayer(activeMap, player, event);
        if (player_ == null) {
            sendMessage("Player to add to the alliance could not be found");
            return;
        }
        String currentMembers = player_.getAllianceMembers();
        if(!player_.getAllianceMembers().contains(player.getFaction())){
            player_.addAllianceMember(player.getFaction()+player.getAllianceMembers());
        }
        if(!player.getAllianceMembers().contains(player_.getFaction())){
            player.addAllianceMember(player_.getFaction()+currentMembers);
        }
        
        for (String leaderID : player_.getLeaderIDs() ){
            if (leaderID.contains("commander") && !player.hasLeader(leaderID)) {
                if(!leaderID.contains("mahact") && !player.hasAbility("edict")){
                    player.addLeader(leaderID);
                    player.getLeader(leaderID).setLocked(false);
                    
                }
                player_.getLeader(leaderID).setLocked(false);
            }
        }
        for (String leaderID : player.getLeaderIDs() ){
            if (leaderID.contains("commander") && !player_.hasLeader(leaderID)) {
                if(!leaderID.contains("mahact")&&!player_.hasAbility("edict")){
                    player_.addLeader(leaderID);
                    player_.getLeader(leaderID).setLocked(false);
                }
                player.getLeader(leaderID).setLocked(false);
            }
        }
        if(player.hasAbility("edict")){
            player.addMahactCC(player_.getColor());
        }
        if(player_.hasAbility("edict")){
            player_.addMahactCC(player.getColor());
        }
        String msg = Helper.getPlayerRepresentation(player, activeMap, activeMap.getGuild(), true) + Helper.getPlayerRepresentation(player_, activeMap, activeMap.getGuild(), true) + " pinging you into this";
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(activeMap),msg);
        MessageHelper.sendMessageToChannel(player_.getCardsInfoThread(activeMap),msg);
        
        sendMessage("Added "+player_.getFaction() + " as part of "+player.getFaction()+"'s alliance. This works 2 ways");
    }
}
