package ti4.commands.game;

import java.util.HashMap;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.cardspn.PNInfo;
import ti4.commands.tokens.AddCC;
import ti4.commands.tokens.RemoveCC;
import ti4.generator.Mapper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.model.PromissoryNoteModel;

public class Eliminate extends AddRemovePlayer {

    private StringBuilder sb = new StringBuilder();
    public Eliminate() {
        super(Constants.ELIMINATE, "Eliminate player from game");
        addOptions(new OptionData(OptionType.STRING, Constants.CONFIRM, "Confirm you want to eliminate with YES").setRequired(true));

    }


    protected String getResponseMessage(Map activeMap, User user) {
        return sb.toString();
    }

    @Override
    protected void action(SlashCommandInteractionEvent event, Map activeMap, User user) {
        sb = new StringBuilder();
        removeUser(event, activeMap, Constants.PLAYER1);
        removeUser(event, activeMap, Constants.PLAYER2);
        removeUser(event, activeMap, Constants.PLAYER3);
        removeUser(event, activeMap, Constants.PLAYER4);
        removeUser(event, activeMap, Constants.PLAYER5);
        removeUser(event, activeMap, Constants.PLAYER6);
        removeUser(event, activeMap, Constants.PLAYER7);
        removeUser(event, activeMap, Constants.PLAYER8);
    }

    private void removeUser(SlashCommandInteractionEvent event, Map activeMap, String playerID) {
        OptionMapping option;
        option = event.getOption(playerID);

        OptionMapping option2 = event.getOption(Constants.CONFIRM);
        if(!option2.getAsString().equalsIgnoreCase("yes"))
        {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Please confirm with yes");
            return;
        }
        if (option != null){
            
            User extraUser = option.getAsUser();
            Player player = activeMap.getPlayer(extraUser.getId());
            HashMap<String, PromissoryNoteModel> PNs = Mapper.getPromissoryNotes();
            if(player != null && player.getFaction() != null){
                //send back all the PNs of others that the player was holding
                for(String pnID : player.getPromissoryNotes().keySet()){
                    PromissoryNoteModel pn = PNs.get(pnID);
                    if(!pn.getOwner().equalsIgnoreCase(player.getFaction())){
                        Player p2 = Helper.getPlayerFromColorOrFaction(activeMap, pn.getOwner());
                        player.removePromissoryNote(pnID);
                        p2.setPromissoryNote(pnID);
                        PNInfo.sendPromissoryNoteInfo(activeMap, p2, false);
                    }
                }
                //Purge all the PNs of the eliminated player that other players were holding
                for(Player p2 : activeMap.getPlayers().values()){
                    for(String pnID : p2.getPromissoryNotes().keySet()){
                        PromissoryNoteModel pn = PNs.get(pnID);
                        if(pn.getOwner().equalsIgnoreCase(player.getFaction())){
                            p2.removePromissoryNote(pnID);
                            PNInfo.sendPromissoryNoteInfo(activeMap, p2, false);
                        }
                    }
                }
                //Remove all of the players units and ccs from the board
                for(Tile tile : activeMap.getTileMap().values()){
                    tile.removeAllUnits(player.getColor());
                    if (AddCC.hasCC(event, player.getColor(), tile)) {
                        RemoveCC.removeCC(event, player.getColor(), tile, activeMap);
                    }
                }
                //discard all of a players ACs
                for(java.util.Map.Entry<String, Integer> ac : player.getActionCards().entrySet()){
                    boolean removed = activeMap.discardActionCard(player.getUserID(), ac.getValue());
                    StringBuilder sb = new StringBuilder();
                    sb.append("Player: ").append(player.getUserName()).append(" - ");
                    sb.append("Discarded Action Card:").append("\n");
                    sb.append(Mapper.getActionCard(ac.getKey()).getRepresentation()).append("\n");
                    MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
                }
                //unscore all of a players SOs
                for(int so : player.getSecretsScored().values()){
                    boolean scored = activeMap.unscoreSecretObjective(extraUser.getId(), so);
                }
                //discard all of a players SOs
                for(int so : player.getSecrets().values()){
                    boolean removed = activeMap.discardSecretObjective(player.getUserID(), so);
                }
            }
            activeMap.removePlayer(extraUser.getId());
            sb.append("Eliminated player: ").append(extraUser.getName()).append(" from game: ").append(activeMap.getName()).append("\n");
        }
    }
}