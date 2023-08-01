package ti4.commands.status;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.leaders.RefreshLeader;
import ti4.commands.cardspn.PNInfo;
import ti4.generator.GenerateMap;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.map.*;
import ti4.message.MessageHelper;
import ti4.helpers.ButtonHelper;
import ti4.generator.Mapper;
import ti4.model.PromissoryNoteModel;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

public class Cleanup extends StatusSubcommandData {
    public Cleanup() {
        super(Constants.CLEANUP, "Status phase cleanup");
        addOptions(new OptionData(OptionType.STRING, Constants.CONFIRM, "Confirm command with YES").setRequired(true));
    }

    @Override

    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping option = event.getOption(Constants.CONFIRM);
        if (option == null || !"YES".equals(option.getAsString())){
            MessageHelper.replyToMessage(event, "Must confirm with YES");
            return;
        }
        Map activeMap = getActiveMap();
        runStatusCleanup(activeMap);
    }

    public void runStatusCleanup(Map activeMap) {

        HashMap<String, Tile> tileMap = activeMap.getTileMap();
        for (Tile tile : tileMap.values()) {
            tile.removeAllCC();
            HashMap<String, UnitHolder> unitHolders = tile.getUnitHolders();
            for (UnitHolder unitHolder : unitHolders.values()) {
                unitHolder.removeAllCC();
                unitHolder.removeAllUnitDamage();
            }
        }
        HashMap<Integer, Boolean> scPlayed = activeMap.getScPlayed();
        for (java.util.Map.Entry<Integer, Boolean> sc : scPlayed.entrySet()) {
            sc.setValue(false);
        }

        returnEndStatusPNs(activeMap); // return any PNs with "end of status phase" return timing

        LinkedHashMap<String, Player> players = activeMap.getPlayers();

        for (Player player : players.values()) {
            player.setPassed(false);
            Set<Integer> SCs = player.getSCs();
            for (int sc : SCs) {
                activeMap.setScTradeGood(sc, 0);
            }            
            player.clearSCs();
            player.clearFollowedSCs();
            player.cleanExhaustedTechs();
            player.cleanExhaustedPlanets(true);
            player.cleanExhaustedRelics();
            player.clearExhaustedAbilities();
            List<Leader> leads = new ArrayList<Leader>();
            leads.addAll(player.getLeaders());
            for (Leader leader : leads) {
                if (!leader.isLocked()){
                    if (leader.isActive()){
                        player.removeLeader(leader.getId());
                    } else {
                        RefreshLeader.refreshLeader(player, leader, activeMap);
                    }
                }
            }
        }
        int round = activeMap.getRound();
        round++;
        
        activeMap.setRound(round);
    }
  

    public void returnEndStatusPNs(Map activeMap) {
        LinkedHashMap<String, Player> players = activeMap.getPlayers();
         for (Player player : players.values()) {
           List<String> pns = new ArrayList<String>();
            pns.addAll(player.getPromissoryNotesInPlayArea());
            for(String pn: pns){
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeMap), "Checking a new pn");
                Player pnOwner = activeMap.getPNOwner(pn);
                if(!pnOwner.isRealPlayer() ){
                    continue;
                }
                PromissoryNoteModel pnModel = Mapper.getPromissoryNotes().get(pn);
                if(pnModel.getText().contains("eturn this card") && pnModel.getText().contains("end of the status phase")){
                        player.removePromissoryNote(pn);
                        pnOwner.setPromissoryNote(pn);  
                        PNInfo.sendPromissoryNoteInfo(activeMap, pnOwner, false);
		                PNInfo.sendPromissoryNoteInfo(activeMap, player, false);
                        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeMap), pnModel.getName() + " was returned");
                    }
                }
            }
    }    

    @Override

    public void reply(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        int prevRound = activeMap.getRound() - 1;

        StatusCommand.reply(event, "End of round " + prevRound + " status phase.");
    }
}
