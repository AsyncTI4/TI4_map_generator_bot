package ti4.commands.game;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.commands.cardspn.PNInfo;
import ti4.commands.tokens.AddCC;
import ti4.commands.tokens.RemoveCC;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.model.PromissoryNoteModel;

public class Eliminate extends AddRemovePlayer {

    private StringBuilder sb = new StringBuilder();
    public Eliminate() {
        super(Constants.ELIMINATE, "Eliminate player from game");
    }


    protected String getResponseMessage(Game activeGame, User user) {
        return sb.toString();
    }

    @Override
    protected void action(SlashCommandInteractionEvent event, Game activeGame, User user) {
        sb = new StringBuilder();
        removeUser(event, activeGame, Constants.PLAYER1);
        removeUser(event, activeGame, Constants.PLAYER2);
        removeUser(event, activeGame, Constants.PLAYER3);
        removeUser(event, activeGame, Constants.PLAYER4);
        removeUser(event, activeGame, Constants.PLAYER5);
        removeUser(event, activeGame, Constants.PLAYER6);
        removeUser(event, activeGame, Constants.PLAYER7);
        removeUser(event, activeGame, Constants.PLAYER8);
    }

    private void removeUser(SlashCommandInteractionEvent event, Game activeGame, String playerID) {
        OptionMapping option;
        option = event.getOption(playerID);

        // OptionMapping option2 = event.getOption(Constants.CONFIRM);
        // if(!option2.getAsString().equalsIgnoreCase("yes"))
        // {
        //     MessageHelper.sendMessageToChannel(event.getChannel(), "Please confirm with yes");
        //     return;
        // }
        if (option != null){
            
            // OptionMapping removeOption = event.getOption(Constants.FACTION_COLOR);
            
            // if (removeOption == null) {
            //     MessageHelper.replyToMessage(event, "Specify player to remove and replacement");
            //     return;
            // }
            
            // Player player = Helper.getPlayer(activeGame, null, event);
            User extraUser = option.getAsUser();
            Player player = activeGame.getPlayer(extraUser.getId());
            HashMap<String, PromissoryNoteModel> PNss = Mapper.getPromissoryNotes();
            if(player != null && player.getFaction() != null){
                //send back all the PNs of others that the player was holding
                Set<String> pns = new HashSet<>(player.getPromissoryNotes().keySet());
                for(String pnID :pns){

                    PromissoryNoteModel pn = PNss.get(pnID);
                    if(pn!= null && !pn.getOwner().equalsIgnoreCase(player.getColor()) && !pn.getOwner().equalsIgnoreCase(player.getFaction())){
                        Player p2 = Helper.getPlayerFromColorOrFaction(activeGame, pn.getOwner());
                        player.removePromissoryNote(pnID);
                        p2.setPromissoryNote(pnID);
                        PNInfo.sendPromissoryNoteInfo(activeGame, p2, false);
                    }
                }
                
                //Purge all the PNs of the eliminated player that other players were holding
                for(Player p2 : activeGame.getPlayers().values()){
                    pns = new HashSet<>(p2.getPromissoryNotes().keySet());
                    for(String pnID : pns){
                        PromissoryNoteModel pn = PNss.get(pnID);
                        if(pn!= null &&(pn.getOwner().equalsIgnoreCase(player.getColor()) || pn.getOwner().equalsIgnoreCase(player.getFaction()))){
                            p2.removePromissoryNote(pnID);
                            PNInfo.sendPromissoryNoteInfo(activeGame, p2, false);
                        }
                    }
                }
                //Remove all of the players units and ccs from the board
                for(Tile tile : activeGame.getTileMap().values()){
                    tile.removeAllUnits(player.getColor());
                    if (AddCC.hasCC(event, player.getColor(), tile)) {
                        RemoveCC.removeCC(event, player.getColor(), tile, activeGame);
                    }
                }
                //discard all of a players ACs
                LinkedHashMap<String, Integer> acs = new LinkedHashMap<>(player.getActionCards());
                for(Map.Entry<String, Integer> ac : acs.entrySet()){
                    boolean removed = activeGame.discardActionCard(player.getUserID(), ac.getValue());
                    String sb = "Player: " + player.getUserName() + " - " +
                        "Discarded Action Card:" + "\n" +
                        Mapper.getActionCard(ac.getKey()).getRepresentation() + "\n";
                    MessageHelper.sendMessageToChannel(event.getChannel(), sb);
                }
                //unscore all of a players SOs
                acs = new LinkedHashMap<>(player.getSecretsScored());
                for(int so : acs.values()){
                    boolean scored = activeGame.unscoreSecretObjective(player.getUserID(), so);
                }
                //discard all of a players SOs

                acs = new LinkedHashMap<>(player.getSecrets());
                for(int so : acs.values()){
                    boolean removed = activeGame.discardSecretObjective(player.getUserID(), so);
                }
                 //return SCs
                Set<Integer> scs = new HashSet<>(player.getSCs());
                for(int sc : scs){
                   player.removeSC(sc);
                }
            }
            activeGame.removePlayer(player.getUserID());
            Guild guild = event.getGuild();
            Member removedMember = guild.getMemberById(player.getUserID());
            List<Role> roles = guild.getRolesByName(activeGame.getName(), true);
            if (removedMember != null && roles.size() == 1) {
                guild.removeRoleFromMember(removedMember, roles.get(0)).queue();
            }
            sb.append("Eliminated player: ").append(player.getUserName()).append(" from game: ").append(activeGame.getName()).append("\n");
            MessageHelper.sendMessageToChannel(event.getChannel(),sb.toString());
        }
    }
}