package ti4.helpers;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.util.*;

import org.apache.commons.lang3.StringUtils;

import ti4.commands.cardsac.ACInfo;
import ti4.commands.cardspn.PNInfo;
import ti4.commands.explore.SendFragments;
import ti4.generator.Mapper;
import ti4.map.Leader;
import ti4.map.Map;
import ti4.map.MapSaveLoadManager;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;


public class ButtonHelper {
    public static List<Button> getStartOfTurnButtons(Player player, Map activeMap, boolean doneActionThisTurn) {
        String finChecker = "FFCC_"+player.getFaction() + "_";
        List<Button> startButtons = new ArrayList<>();
        Button tacticalAction = Button.success(finChecker+"tacticalAction", "Tactical Action");
        Button componentAction = Button.success(finChecker+"componentAction", "Component Action");
        startButtons.add(tacticalAction);
        startButtons.add(componentAction);
        boolean hadAnyUnplayedSCs = false;
        for(Integer SC : player.getSCs())
        {
            if(!activeMap.getPlayedSCs().contains(SC))
            {
                hadAnyUnplayedSCs = true;
                Button strategicAction = Button.success(finChecker+"strategicAction_"+SC, "Play SC #"+SC);
                startButtons.add(strategicAction);
            }
        }

        if(!hadAnyUnplayedSCs && !doneActionThisTurn)
        {
            Button pass = Button.danger(finChecker+"passForRound", "Pass");
            startButtons.add(pass);
        }

        Button transaction = Button.primary("transaction", "Transaction");
        startButtons.add(transaction);

        return startButtons;
    }
    public static List<Button> getPlayersToTransact(Map activeMap, Player p) {
        List<Button> playerButtons = new ArrayList<>();
        String finChecker = "FFCC_"+p.getFaction() + "_";
        for (Player player : activeMap.getPlayers().values()) {
            if (player.isRealPlayer()) {
                if(player.getFaction().equalsIgnoreCase(p.getFaction()))
                {
                    continue;
                }
                String faction = player.getFaction();
                if (faction != null && Mapper.isFaction(faction)) {
                    Button button = null;
                    if (!activeMap.isFoWMode()) {
                        button = Button.secondary(finChecker+"transactWith_"+faction, " ");
                        
                        String factionEmojiString = Helper.getFactionIconFromDiscord(faction);
                        button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                    } else {
                            button = Button.secondary(finChecker+"transactWith_"+player.getColor(), player.getColor());
                    }
                    playerButtons.add(button);
                }
                
            }
        }
        return playerButtons;
    }

    public static List<Button> getStuffToTransButtons(Map activeMap, Player p1, Player p2) {
        String finChecker = "FFCC_"+p1.getFaction() + "_";
        List<Button> stuffToTransButtons = new ArrayList<>();
        if(p1.getTg() > 0)
        {
            Button transact= Button.success(finChecker+"transact_TGs_"+p2.getFaction(), "TGs");
            stuffToTransButtons.add(transact);
        }
        if(p1.getCommodities() > 0)
        {
            Button transact = Button.success(finChecker+"transact_Comms_"+p2.getFaction(), "Commodities");
            stuffToTransButtons.add(transact);
        }
        if((p1.hasAbility("arbiters") || p2.hasAbility("arbiters")) && p1.getAc() > 0)
        {
            Button transact = Button.success(finChecker+"transact_ACs_"+p2.getFaction(), "Action Cards");
            stuffToTransButtons.add(transact);
        }
        if(p1.getPnCount() > 0)
        {
            Button transact = Button.success(finChecker+"transact_PNs_"+p2.getFaction(), "Promissory Notes");
            stuffToTransButtons.add(transact);
        }
        if(p1.getFragments().size() > 0)
        {
            Button transact = Button.success(finChecker+"transact_Frags_"+p2.getFaction(), "Fragments");
            stuffToTransButtons.add(transact);
        }
        return stuffToTransButtons;
    }


    public static void resolveSpecificTransButtons(Map activeMap, Player p1, String buttonID, ButtonInteractionEvent event) {
        String finChecker = "FFCC_"+p1.getFaction() + "_";
   
        List<Button> stuffToTransButtons = new ArrayList<>();
        buttonID = buttonID.replace("transact_", "");
        String thingToTrans = buttonID.substring(0, buttonID.indexOf("_"));
        String factionToTrans = buttonID.substring(buttonID.indexOf("_")+1, buttonID.length());
        Player p2 = Helper.getPlayerFromColorOrFaction(activeMap, factionToTrans);

        switch(thingToTrans)
        {
            case "TGs" -> {

                String message = "Click the amount of tgs you would like to send";
                for(int x = 1; x < p1.getTg()+1; x++)
                {
                    Button transact = Button.success(finChecker+"send_TGs_"+p2.getFaction() + "_"+x, ""+x);
                    stuffToTransButtons.add(transact);
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),message, stuffToTransButtons);
            }
            case "Comms" -> {
                String message = "Click the amount of commodities you would like to send";
                for(int x = 1; x < p1.getCommodities()+1; x++)
                {
                    Button transact = Button.success(finChecker+"send_Comms_"+p2.getFaction() + "_"+x, ""+x);
                    stuffToTransButtons.add(transact);
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),message, stuffToTransButtons);
            }
            case "ACs" -> {
                String message = Helper.getPlayerRepresentation(p1, activeMap, activeMap.getGuild(), true)+" Click the AC you would like to send";
                for(String acShortHand : p1.getActionCards().keySet())
                {
                    Button transact = Button.success(finChecker+"send_ACs_"+p2.getFaction() + "_"+p1.getActionCards().get(acShortHand), Mapper.getActionCardName(acShortHand));
                    stuffToTransButtons.add(transact);
                }
                MessageHelper.sendMessageToChannelWithButtons(p1.getCardsInfoThread(activeMap),message, stuffToTransButtons);
            }
            case "PNs" -> {
                String message =  Helper.getPlayerRepresentation(p1, activeMap, activeMap.getGuild(), true)+" Click the PN you would like to send";
                for(String pnShortHand : p1.getPromissoryNotes().keySet())
                {
                    Button transact = Button.success(finChecker+"send_PNs_"+p2.getFaction() + "_"+p1.getPromissoryNotes().get(pnShortHand), ""+p1.getPromissoryNotes().get(pnShortHand));
                    stuffToTransButtons.add(transact);
                }
                MessageHelper.sendMessageToChannelWithButtons(p1.getCardsInfoThread(activeMap),message, stuffToTransButtons);
            }
            case "Frags" -> {
                String message = "Click the amount of fragments you would like to send";
                
                if(p1.getCrf() > 0)
                {
                    for(int x = 1; x < p1.getCrf()+1; x++)
                    {
                        Button transact = Button.primary(finChecker+"send_Frags_"+p2.getFaction() + "_CRF"+x, "Cultural Fragments ("+x+")");
                        stuffToTransButtons.add(transact);
                    }
                }
                if(p1.getIrf() > 0)
                {
                    for(int x = 1; x < p1.getIrf()+1; x++)
                    {
                        Button transact = Button.success(finChecker+"send_Frags_"+p2.getFaction() + "_IRF"+x, "Industrial Fragments ("+x+")");
                        stuffToTransButtons.add(transact);
                    }
                }
                if(p1.getHrf() > 0)
                {
                    for(int x = 1; x < p1.getHrf()+1; x++)
                    {
                        Button transact = Button.danger(finChecker+"send_Frags_"+p2.getFaction() + "_HRF"+x, "Hazardous Fragments ("+x+")");
                        stuffToTransButtons.add(transact);
                    }
                }
                
                if(p1.getVrf() > 0)
                {
                    for(int x = 1; x < p1.getVrf()+1; x++)
                    {
                        Button transact = Button.secondary(finChecker+"send_Frags_"+p2.getFaction() + "_URF"+x, "Frontier Fragments ("+x+")");
                        stuffToTransButtons.add(transact);
                    }
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),message, stuffToTransButtons);

            }
        }
        
    }

    public static void resolveSpecificTransButtonPress(Map activeMap, Player p1, String buttonID, ButtonInteractionEvent event) {
        String finChecker = "FFCC_"+p1.getFaction() + "_";
        buttonID = buttonID.replace("send_", "");
        List<Button> goAgainButtons = new ArrayList<>();

        String thingToTrans = buttonID.substring(0, buttonID.indexOf("_"));
        buttonID = buttonID.replace(thingToTrans+"_", "");
        String factionToTrans = buttonID.substring(0, buttonID.indexOf("_"));
        String amountToTrans = buttonID.substring(buttonID.indexOf("_")+1, buttonID.length());
        Player p2 = Helper.getPlayerFromColorOrFaction(activeMap, factionToTrans);
        String message2 = "";
        String ident = Helper.getFactionIconFromDiscord(p1.getFaction());
        switch(thingToTrans)
        {
            case "TGs" -> {

                int tgAmount = Integer.parseInt(amountToTrans);
                p1.setTg(p1.getTg()-tgAmount);
                p2.setTg(p2.getTg()+tgAmount);
                message2 = ident + " sent " + tgAmount+ " TGs to "+Helper.getPlayerDependingOnFog(activeMap,p2);
            }
            case "Comms" -> {
                int tgAmount = Integer.parseInt(amountToTrans);
                p1.setCommodities(p1.getCommodities()-tgAmount);
                p2.setTg(p2.getTg()+tgAmount);
                message2 = ident + " sent " + tgAmount+ " Commodities to "+Helper.getPlayerDependingOnFog(activeMap,p2);
            }
            case "ACs" -> {
                message2 =ident + " sent AC #" + amountToTrans+ " to "+Helper.getPlayerDependingOnFog(activeMap,p2);
               int acNum = Integer.parseInt(amountToTrans);
               String acID = null;
               for (java.util.Map.Entry<String, Integer> so : p1.getActionCards().entrySet()) {
                   if (so.getValue().equals(acNum)) {
                       acID = so.getKey();
                   }
               }
                p1.removeActionCard(acNum);
                p2.setActionCard(acID);
                ACInfo.sendActionCardInfo(activeMap, p2);
                ACInfo.sendActionCardInfo(activeMap, p1);
            }
            case "PNs" -> {
                String id = null;
		        int pnIndex;
                pnIndex = Integer.parseInt(amountToTrans);
                for (java.util.Map.Entry<String, Integer> so : p1.getPromissoryNotes().entrySet()) {
                    if (so.getValue().equals(pnIndex)) {
                        id = so.getKey();
                    }
                }
                p1.removePromissoryNote(id);
                p2.setPromissoryNote(id);
                boolean sendSftT = false;
                boolean sendAlliance = false;
                String promissoryNoteOwner = Mapper.getPromissoryNoteOwner(id);
                if ((id.endsWith("_sftt") || id.endsWith("_an")) && !promissoryNoteOwner.equals(p2.getFaction())
                        && !promissoryNoteOwner.equals(p2.getColor())) {
                    p2.setPromissoryNotesInPlayArea(id);
                    if (id.endsWith("_sftt")) {
                        sendSftT = true;
                    } else {
                        sendAlliance = true;
                    }
                }
                PNInfo.sendPromissoryNoteInfo(activeMap, p1, false);
                PNInfo.sendPromissoryNoteInfo(activeMap, p2, false);
                String text = sendSftT ? "**Support for the Throne** " : (sendAlliance ? "**Alliance** " : "");
                message2 = Helper.getPlayerRepresentation(p1, activeMap) + " sent " + Emojis.PN + text + "PN to " + Helper.getPlayerRepresentation(p2, activeMap);   
            }
            case "Frags" -> {
               
                String fragType = amountToTrans.substring(0, 3);
                int fragNum = Integer.parseInt(amountToTrans.charAt(3)+"");
                String trait = 	switch (fragType){
                    case "CRF"-> "cultural"; 
                    case  "HRF"-> "hazardous";
                    case "IRF"->  "industrial" ;
                    case  "URF"->  "frontier" ;
                    default -> "";
                };
                new SendFragments().sendFrags(event, p1, p2, trait, fragNum, activeMap);
                message2 = "";
            }
        }
        Button button = Button.secondary(finChecker+"transactWith_"+p2.getColor(), "Send something else to player?");
        Button done = Button.secondary("deleteButtons", "Done With This Transaction");
                    
        goAgainButtons.add(button);
        goAgainButtons.add(done);
        if(activeMap.isFoWMode())
        {
            MessageHelper.sendMessageToChannel(p1.getPrivateChannel(), message2);
            MessageHelper.sendMessageToChannelWithButtons(p1.getPrivateChannel(),ident+" Use Buttons To Complete Transaction", goAgainButtons);
            MessageHelper.sendMessageToChannel(p2.getPrivateChannel(), message2);
        }
        else
        {
            MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(), message2);
            MessageHelper.sendMessageToChannelWithButtons(activeMap.getMainGameChannel(),ident+" Use Buttons To Complete Transaction", goAgainButtons);
        }
        MapSaveLoadManager.saveMap(activeMap, event);

        
        
    }

    

}