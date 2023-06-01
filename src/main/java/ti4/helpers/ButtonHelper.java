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
import ti4.map.Tile;
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

    public static void getAllPossibleCompButtons(Map activeMap, Player p1, ButtonInteractionEvent event) {
        String finChecker = "FFCC_"+p1.getFaction() + "_";
        String prefix = "componentActionRes_";
        List<Button> compButtons = new ArrayList<>();
        //techs
        for(String tech : p1.getTechs())
        {
            String techRep = Mapper.getTechRepresentations().get(tech);

            //Columns: key = Proper Name | type | prerequisites | faction | text
            StringTokenizer techRepTokenizer = new StringTokenizer(techRep,"|");
            String techName = techRepTokenizer.nextToken();
            String techType = techRepTokenizer.nextToken();
            String techPrerequisites = techRepTokenizer.nextToken();
            String techFaction = techRepTokenizer.nextToken();
            String factionEmoji = "";
            if (!techFaction.equals(" ")) factionEmoji = Helper.getFactionIconFromDiscord(techFaction);
            String techEmoji = Helper.getEmojiFromDiscord(techType + "tech");
            String techText = techRepTokenizer.nextToken();
            if(techText.contains("ACTION"))
            {
                Button tButton = Button.danger(finChecker+prefix+"tech_"+tech, "Exhaust "+techName).withEmoji(Emoji.fromFormatted(techEmoji));
                compButtons.add(tButton);
            }
        }
        //leaders
        for(Leader leader:p1.getLeaders())
        {
            if(!leader.isExhausted() && !leader.isLocked())
            {
                String leaderID = leader.getId();

                String leaderRep =  Mapper.getLeaderRepresentations().get(leaderID.toString());
                //leaderID = 0:LeaderName ; 1:LeaderTitle ; 2:BacksideTitle/HeroAbility ; 3:AbilityWindow ; 4:AbilityText
                String[] leaderRepSplit = leaderRep.split(";");
                String leaderName = leaderRepSplit[0];
                String leaderTitle = leaderRepSplit[1];
                String heroAbilityName = leaderRepSplit[2];
                String leaderAbilityWindow = leaderRepSplit[3];
                String leaderAbilityText = leaderRepSplit[4];
                String leaderUnlockCondition = leaderRepSplit[5];
                
                String factionEmoji =Helper.getFactionLeaderEmoji(leader);
                if(leaderAbilityWindow.equalsIgnoreCase("ACTION:") || leaderName.contains("Ssruu"))
                {
                    Button lButton = Button.secondary(finChecker+prefix+"leader_"+leaderID, "Use "+leaderName).withEmoji(Emoji.fromFormatted(factionEmoji));
                    compButtons.add(lButton);
                }
            }
        }
        //Relics
        for(String relic : p1.getRelics())
        {
            String relicText = Mapper.getRelic(relic);
            String[] relicData = relicText.split(";");
            if(relicData[1].contains("Action:"))
            {
                Button rButton = Button.danger(finChecker+prefix+"relic_"+relic, "Purge "+relicData[1]);
                compButtons.add(rButton);
            }
        }
        //PNs
        for(String pn : p1.getPromissoryNotes().keySet()){
            if(!Mapper.getPromissoryNoteOwner(pn).equalsIgnoreCase(p1.getFaction()) && !p1.getPromissoryNotesInPlayArea().contains(pn))
            {
                String pnText = Mapper.getPromissoryNote(pn, true);
                if(pnText.contains("Action:"))
                {
                    Button pnButton = Button.danger(finChecker+prefix+"pn_"+p1.getPromissoryNotes().get(pn), "Use "+pnText.substring(0, pnText.indexOf(";")));
                    compButtons.add(pnButton);
                }
            }
        }
        //Abilities
        if(p1.hasAbility("star_forge") && p1.getStrategicCC() > 0)
        {
            Button abilityButton = Button.success(finChecker+prefix+"ability_starForge", "Starforge");
            compButtons.add(abilityButton);
        }
        if(p1.hasAbility("orbital_drop") && p1.getStrategicCC() > 0)
        {
            Button abilityButton = Button.success(finChecker+prefix+"ability_orbitalDrop", "Orbital Drop");
            compButtons.add(abilityButton);
        }
        if(p1.hasAbility("stall_tactics") && p1.getActionCards().size() > 0)
        {
            Button abilityButton = Button.success(finChecker+prefix+"ability_stallTactics", "Stall Tactics");
            compButtons.add(abilityButton);
        }
        if(p1.hasAbility("fabrication") && p1.getFragments().size() > 0)
        {
            Button abilityButton = Button.success(finChecker+prefix+"ability_fabrication", "Purge 1 Frag for a CC");
            compButtons.add(abilityButton);
        }
        //Get Relic
        if(p1.enoughFragsForRelic())
        {
            Button getRelicButton = Button.success(finChecker+prefix+"getRelic_", "Get Relic");
            compButtons.add(getRelicButton);
        }
       //ACs
        Button acButton = Button.secondary(finChecker+prefix+"actionCards_", "Play \"ACTION:\" AC");
        compButtons.add(acButton);
        //Generic
        Button genButton = Button.secondary(finChecker+prefix+"generic_", "Generic Component Action");
        compButtons.add(genButton);
    }

    public static boolean isNextToEmpyMechs(Map activeMap, Player ACPlayer, Player EmpyPlayer)
    {
        if(ACPlayer == null || EmpyPlayer == null)
        {
            return false;
        }
        boolean isNextTo = false;
        if(ACPlayer.getFaction().equalsIgnoreCase(EmpyPlayer.getFaction()))
        {
            return false;
        }
        List<Tile> tiles = getTilesOfPlayersSpecificUnit(activeMap, EmpyPlayer, "mech");
        for(Tile tile : tiles)
        {
            
            Set<String> adjTiles = FoWHelper.getAdjacentTiles(activeMap, tile.getPosition(), EmpyPlayer, true);
            for(String adjTile : adjTiles)
            {
                
                Tile adjT = activeMap.getTileMap().get(adjTile);
                if(FoWHelper.playerHasUnitsInSystem(ACPlayer, adjT))
                {
                    isNextTo = true;
                    return isNextTo;
                }
            }
        }


        return isNextTo;
    }

    public static List<Tile> getTilesOfPlayersSpecificUnit(Map activeMap, Player p1, String unit)
    {

        List<Tile> tiles = new ArrayList<Tile>();

        for(Tile tile : activeMap.getTileMap().values())
        {
            boolean tileHasIt = false;
            String unitKey = Mapper.getUnitID(AliasHandler.resolveUnit(unit), p1.getColor());
            for(UnitHolder unitH : tile.getUnitHolders().values())
            {
                if(unitH.getUnits().containsKey(unitKey))
                {
                    tileHasIt = true;
                }
            }
            if(tileHasIt && !tiles.contains(tile))
            {
                tiles.add(tile);
            }
        }


        return tiles;
        
    }

    public static void resolvePressedCompButton(Map activeMap, Player p1, ButtonInteractionEvent event, String buttonID) {
        String prefix = "componentActionRes_";
        String finChecker = "FFCC_"+p1.getFaction() + "_";
        buttonID = buttonID.replace(prefix, "");

        String firstPart = buttonID.substring(0, buttonID.indexOf("_"));
        buttonID = buttonID.replace(firstPart+"_", "");

        switch(firstPart) {
            case "tech" -> {
                p1.exhaustTech(buttonID);
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), (Helper.getPlayerRepresentation(p1, activeMap) + " exhausted tech: " + Helper.getTechRepresentation(buttonID)));
            }
            case "leader" -> {
                Leader playerLeader = p1.getLeader(buttonID);
		
                if(buttonID.contains("agent")){
                    playerLeader.setExhausted(true);
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(),Helper.getFactionLeaderEmoji(playerLeader));
                    StringBuilder messageText = new StringBuilder(Helper.getPlayerRepresentation(p1, activeMap))
                            .append(" exhausted ").append(Helper.getLeaderFullRepresentation(playerLeader));
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(),messageText.toString());
                    
                }
                else {
                    StringBuilder message = new StringBuilder(Helper.getPlayerRepresentation(p1, activeMap)).append(" played ").append(Helper.getLeaderFullRepresentation(playerLeader));
                    if ("letnevhero".equals(playerLeader.getId()) || "nomadhero".equals(playerLeader.getId())) {
                        playerLeader.setLocked(false);
                        playerLeader.setActive(true);
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(),message.toString() + " - Leader will be PURGED after status cleanup");
                    } else {
                        boolean purged = p1.removeLeader(playerLeader);
                        if (purged) {
                            MessageHelper.sendMessageToChannel(event.getMessageChannel(),message.toString() + " - Leader " + buttonID + " has been purged");
                        } else {
                            MessageHelper.sendMessageToChannel(event.getMessageChannel(),"Leader was not purged - something went wrong");
                        }
                        if ("titanshero".equals(playerLeader.getId())) {
                            MessageHelper.sendMessageToChannel(event.getMessageChannel(),"`Use the following command to add the attachment: /add_token token:titanshero`");
                        }
                    }

                }
            }
            case "relic" -> {
                String relicId = buttonID;
                Player player = p1;
                if (player.getRelics().contains(relicId)) {
                    player.removeRelic(relicId);
                    player.removeExhaustedRelic(relicId);
                    String relicName = Mapper.getRelic(relicId).split(";")[0];
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(),"Purged " + Emojis.Relic + " relic: " + relicName);
                } else {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(),"Invalid relic or player does not have specified relic");
                }
            }
            case "pn" -> {
                boolean longPNDisplay = false;
                String id = buttonID;
                Player player = p1;
                String promissoryNote = Mapper.getPromissoryNote(id, true);
                String[] pn = promissoryNote.split(";");
                String pnOwner = Mapper.getPromissoryNoteOwner(id);
                if (pn.length > 3 && pn[3].equals("playarea")) {
                    player.setPromissoryNotesInPlayArea(id);
                } else {
                    player.removePromissoryNote(id);
                    for (Player player_ : activeMap.getPlayers().values()) {
                        String playerColor = player_.getColor();
                        String playerFaction = player_.getFaction();
                        if (playerColor != null && playerColor.equals(pnOwner) || playerFaction != null && playerFaction.equals(pnOwner)) {
                            player_.setPromissoryNote(id);
                            PNInfo.sendPromissoryNoteInfo(activeMap, player_, false);
                            pnOwner = player_.getFaction();
                            break;
                        }
                    }
                }

                String emojiToUse = activeMap.isFoWMode() ? "" : Helper.getFactionIconFromDiscord(pnOwner);
                StringBuilder sb = new StringBuilder(Helper.getPlayerRepresentation(player, activeMap) + " played promissory note:\n");
                sb.append(emojiToUse + Emojis.PN);
                String pnText = "";

                //Handle AbsolMode Political Secret
                if (activeMap.isAbsolMode() && id.endsWith("_ps")) {
                    pnText = "Political Secret" + Emojis.Absol + ":  *When you cast votes:* You may exhaust up to 3 of the {colour} player's planets and cast additional votes equal to the combined influence value of the exhausted planets. Then return this card to the {colour} player.";
                } else {
                    pnText = Mapper.getPromissoryNote(id, longPNDisplay);
                }
                sb.append(pnText).append("\n");

                //TERRAFORM TIP
                if (id.equalsIgnoreCase("terraform")) {
                    sb.append("`/add_token token:titanspn`\n");
                }

                //Fog of war ping
                if (activeMap.isFoWMode()) {
                    // Add extra message for visibility
                    FoWHelper.pingAllPlayersWithFullStats(activeMap, event, player, sb.toString());
                }
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),sb.toString());
                PNInfo.sendPromissoryNoteInfo(activeMap, player, false);
            }
            case "ability" -> {
                if(buttonID.equalsIgnoreCase("starForge")){

                } else if(buttonID.equalsIgnoreCase("orbitalDrop")){

                }else if(buttonID.equalsIgnoreCase("fabrication")){

                }else if(buttonID.equalsIgnoreCase("stallTactics")){

                }
            }
            case "getRelic" -> {

                List<Button> purgeFragButtons = new ArrayList<>();
                String message = "Click the fragments you'd like to purge. ";
                int numToBeat = 2 - p1.getVrf();
                if((p1.hasAbility("fabrication") || p1.getPromissoryNotes().containsKey("bmf")))
                {
                    numToBeat = numToBeat -1;
                    if( p1.getPromissoryNotes().containsKey("bmf"))
                    {
                        Button transact = Button.primary(finChecker+"playBMF", "Play BMF");
                        purgeFragButtons.add(transact);
                    }
                    
                }
                if(p1.getCrf() > numToBeat)
                {
                    for(int x = numToBeat+1; x < p1.getCrf()+1; x++)
                    {
                        Button transact = Button.primary(finChecker+"purge_Frags_CRF"+x, "Cultural Fragments ("+x+")");
                        purgeFragButtons.add(transact);
                    }
                }
                if(p1.getIrf()> numToBeat)
                {
                    for(int x = numToBeat+1; x < p1.getIrf()+1; x++)
                    {
                        Button transact = Button.success(finChecker+"purge_Frags_IRF"+x, "Industrial Fragments ("+x+")");
                        purgeFragButtons.add(transact);
                    }
                }
                if(p1.getHrf() > numToBeat)
                {
                    for(int x = numToBeat+1; x < p1.getHrf()+1; x++)
                    {
                        Button transact = Button.danger(finChecker+"purge_Frags_HRF"+x, "Hazardous Fragments ("+x+")");
                        purgeFragButtons.add(transact);
                    }
                }
                
                if(p1.getVrf() > 0)
                {
                    for(int x = 1; x < p1.getVrf()+1; x++)
                    {
                        Button transact = Button.secondary(finChecker+"purge_Frags_URF"+x, "Frontier Fragments ("+x+")");
                        purgeFragButtons.add(transact);
                    }
                }
                Button transact2 = Button.danger(finChecker+"drawRelicFromFrag", "Finish Purging and Draw Relic");
                purgeFragButtons.add(transact2);
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),message, purgeFragButtons);
            }
            case "generic" -> {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),"Doing unspecified component action. Maybe ping Fin to add this. ");
            }
            case "actionCards" -> {


            }


        }

        

    }
    

}