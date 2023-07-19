package ti4.helpers;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.ThreadChannelAction;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import java.io.File;
import java.util.*;

import org.apache.commons.lang3.StringUtils;

import ti4.commands.cardsac.ACInfo;
import ti4.commands.cardsac.ShowAllAC;
import ti4.commands.cardspn.ShowAllPN;
import ti4.commands.cardsso.ShowAllSO;
import ti4.commands.explore.ExpPlanet;
import ti4.commands.special.SleeperToken;
import ti4.commands.units.AddUnits;
import ti4.generator.GenerateTile;
import ti4.generator.Mapper;
import ti4.map.Map;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;

public class ButtonHelperFactionSpecific {

    public static void removeSleeper(String buttonID, ButtonInteractionEvent event, Map activeMap, Player player, String ident){
        buttonID = buttonID.replace("removeSleeperFromPlanet_", "");
        String planet = buttonID;
        String message = ident + " removed a sleeper from " + planet;
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        new SleeperToken().addOrRemoveSleeper(event, activeMap, planet, player);
        event.getMessage().delete().queue();
    }
    public static void replacePDSWithFS(String buttonID, ButtonInteractionEvent event, Map activeMap, Player player, String ident){
        buttonID = buttonID.replace("replacePDSWithFS_", "");
        String planet = buttonID;
        String message = ident + " replaced "+ Helper.getEmojiFromDiscord("pds") +" on " + Helper.getPlanetRepresentation(planet,activeMap)+ " with a "+ Helper.getEmojiFromDiscord("flagship");
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        new AddUnits().unitParsing(event, player.getColor(),activeMap.getTile(AliasHandler.resolveTile(planet)), "flagship",activeMap);
        String key = Mapper.getUnitID(AliasHandler.resolveUnit("pds"), player.getColor());
        activeMap.getTile(AliasHandler.resolveTile(planet)).removeUnit(planet,key, 1);
        event.getMessage().delete().queue();
    }
    public static void firstStepOfChaos(Map activeMap, Player p1, ButtonInteractionEvent event){
        List<Button> buttons = new ArrayList<Button>();
        List<Tile> tiles = ButtonHelper.getTilesOfPlayersSpecificUnit(activeMap, p1, "spacedock");
        if(tiles.isEmpty()){
            tiles = ButtonHelper.getTilesOfPlayersSpecificUnit(activeMap, p1, "cabalspacedock");
        }
        for(Tile tile : tiles){
            Button tileButton = Button.success("produceOneUnitInTile_"+tile.getPosition()+"_chaosM", tile.getRepresentationForButtons(activeMap,p1));
            buttons.add(tileButton);
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Select which tile you would like to chaos map in.", buttons);
    }

    
    public static void lastStepOfYinHero(String buttonID, ButtonInteractionEvent event, Map activeMap, Player player, String trueIdentity){
        String planetNInf = buttonID.replace("yinHeroInfantry_", "");
        String planet = planetNInf.split("_")[0];
        String amount = planetNInf.split("_")[1];
        MessageChannel mainGameChannel = activeMap.getMainGameChannel();
        Tile tile = activeMap.getTile(AliasHandler.resolveTile(planet));
        
        new AddUnits().unitParsing(event, player.getColor(),
                        activeMap.getTile(AliasHandler.resolveTile(planet)), amount +" inf " + planet,
                        activeMap);
        MessageHelper.sendMessageToChannel(event.getChannel(), trueIdentity+" Chose to land "+amount+" infantry on "+Helper.getPlanetRepresentation(planet, activeMap));
        UnitHolder unitHolder = tile.getUnitHolders().get(planet);
        for(Player player2 : activeMap.getRealPlayers()){
            if(player2 == player){
                continue;
            }
            String colorID = Mapper.getColorID(player2.getColor());
            String mechKey = colorID + "_mf.png";
            String infKey = colorID + "_gf.png";
            int numMechs = 0;
            int numInf = 0;
            if (unitHolder.getUnits() != null) {
                if (unitHolder.getUnits().get(mechKey) != null) {
                    numMechs = unitHolder.getUnits().get(mechKey);
                }
                if (unitHolder.getUnits().get(infKey) != null) {
                    numInf = unitHolder.getUnits().get(infKey);
                }
            }
            
            if(numInf > 0 || numMechs > 0){
                String messageCombat = "Resolve ground combat.";
                 
                if(!activeMap.isFoWMode()){
                    MessageCreateBuilder baseMessageObject = new MessageCreateBuilder().addContent(messageCombat);
                    TextChannel textChannel = (TextChannel)mainGameChannel;
                    String threadName =  activeMap.getName() + "-yinHero-" + activeMap.getRound() + "-planet-" + planet+"-"+player.getFaction()+"-vs-"+player2.getFaction();
                    Player p1 = player;
                    mainGameChannel.sendMessage(baseMessageObject.build()).queue(message_ -> {
                        ThreadChannelAction threadChannel = textChannel.createThreadChannel(threadName, message_.getId());
                        threadChannel = threadChannel.setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_HOUR);
                        threadChannel.queue(m5 -> {
                            List<ThreadChannel> threadChannels = activeMap.getActionsChannel().getThreadChannels();
                            if (threadChannels != null) {
                                for (ThreadChannel threadChannel_ : threadChannels) {
                                    if (threadChannel_.getName().equals(threadName)) {
                                        MessageHelper.sendMessageToChannel((MessageChannel) threadChannel_, Helper.getPlayerRepresentation(p1, activeMap, activeMap.getGuild(), true) + Helper.getPlayerRepresentation(player2, activeMap, activeMap.getGuild(), true) + " Please resolve the interaction here. Reminder that Yin Hero skips pds fire.");
                                        int context = 0;
                                        File systemWithContext = GenerateTile.getInstance().saveImage(activeMap, context, tile.getPosition(), event);
                                        MessageHelper.sendMessageWithFile((MessageChannel) threadChannel_, systemWithContext, "Picture of system", false);
                                        List<Button> buttons = ButtonHelper.getButtonsForPictureCombats(activeMap,  tile.getPosition());
                                        MessageHelper.sendMessageToChannelWithButtons((MessageChannel) threadChannel_, "", buttons);
                                        
                                    }
                                }
                            }
                        });
                    });
                }
                break;
            }
            
        }
        
        
        event.getMessage().delete().queue();
    }
    public static void putSleeperOn(String buttonID, ButtonInteractionEvent event, Map activeMap, Player player, String ident){
        buttonID = buttonID.replace("putSleeperOnPlanet_", "");
        String planet = buttonID;
        String message = ident+" put a sleeper on " + Helper.getPlanetRepresentation(planet,activeMap);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        new SleeperToken().addOrRemoveSleeper(event, activeMap, planet, player);
        event.getMessage().delete().queue();
    }
    public static void oribtalDropFollowUp(String buttonID, ButtonInteractionEvent event, Map activeMap, Player player, String ident){
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), event.getMessage().getContentRaw());
        List<Button> startButtons = new ArrayList<>();
        Button tacticalAction = Button.success("dropAMechToo", "Spend 3 resource to Drop a Mech Too");
        startButtons.add(tacticalAction);
        Button componentAction = Button.danger("finishComponentAction", "Decline Mech");
        startButtons.add(componentAction);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "Decide whether to drop mech",
                startButtons);
        event.getMessage().delete().queue();
    }
    public static void oribtalDropExhaust(String buttonID, ButtonInteractionEvent event, Map activeMap, Player player, String ident){
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), event.getMessage().getContentRaw());
        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(activeMap, player, event);
        Button DoneExhausting = Button.danger("finishComponentAction", "Done Exhausting Planets");
        buttons.add(DoneExhausting);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                "Use Buttons to Pay For The Mech", buttons);
        event.getMessage().delete().queue();
    }
    public static void pillageCheck(Player player, Map activeMap) {
        if(player.getPromissoryNotesInPlayArea().contains("pop")){
            return;
        }
        if(Helper.getPlayerFromAbility(activeMap, "pillage") != null && !Helper.getPlayerFromAbility(activeMap, "pillage").getFaction().equalsIgnoreCase(player.getFaction())){
             
            Player pillager = Helper.getPlayerFromAbility(activeMap, "pillage");
            String finChecker = "FFCC_"+pillager.getFaction() + "_";
            if(player.getTg() > 2 && Helper.getNeighbouringPlayers(activeMap, player).contains(pillager)){
                List<Button> buttons = new ArrayList<Button>();
                String playerIdent = StringUtils.capitalize(player.getFaction());
                MessageChannel channel = activeMap.getMainGameChannel();
                if(activeMap.isFoWMode()){
                    playerIdent = StringUtils.capitalize(player.getColor());
                    channel = pillager.getPrivateChannel();
                }
                String message = Helper.getPlayerRepresentation(pillager, activeMap, activeMap.getGuild(), true) + " you may have the opportunity to pillage "+playerIdent+". Please check this is a valid pillage opportunity, and use buttons to resolve.";
                buttons.add(Button.danger(finChecker+"pillage_"+player.getColor()+"_unchecked","Pillage "+playerIdent));
                buttons.add(Button.success(finChecker+"deleteButtons","Decline Pillage Window"));
                MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
            }
        }

    }
    public static void resolveDarkPactCheck(Map activeMap, Player sender, Player receiver, int numOfComms, GenericInteractionCreateEvent event) {
        for(String pn : sender.getPromissoryNotesInPlayArea()){
            if(pn.equalsIgnoreCase("dark_pact") && activeMap.getPNOwner(pn).getFaction().equalsIgnoreCase(receiver.getFaction())){
                if(numOfComms == sender.getCommoditiesTotal()){
                    MessageChannel channel = event.getMessageChannel();
                    if(activeMap.isFoWMode()){
                        channel = sender.getPrivateChannel();
                    }
                    String message =  Helper.getPlayerRepresentation(sender, activeMap, activeMap.getGuild(), true)+" Dark Pact triggered, your tgs have increased by 1 ("+sender.getTg()+"->"+(sender.getTg()+1)+")";
                    sender.setTg(sender.getTg()+1);
                    MessageHelper.sendMessageToChannel(channel, message);
                    message =  Helper.getPlayerRepresentation(receiver, activeMap, activeMap.getGuild(), true)+" Dark Pact triggered, your tgs have increased by 1 ("+receiver.getTg()+"->"+(receiver.getTg()+1)+")";
                    receiver.setTg(receiver.getTg()+1);
                    if(activeMap.isFoWMode()){
                        channel = receiver.getPrivateChannel();
                    }
                    MessageHelper.sendMessageToChannel(channel, message);
                    ButtonHelperFactionSpecific.pillageCheck(sender, activeMap);
                    ButtonHelperFactionSpecific.pillageCheck(receiver, activeMap);    
                }
            }
        }
    }
    public static void resolveMuaatCommanderCheck(Player player, Map activeMap, GenericInteractionCreateEvent event) {
        if (activeMap.playerHasLeaderUnlockedOrAlliance(player, "muaatcommander")) {
            int old = player.getTg();
            int newTg = player.getTg()+1;
            player.setTg(player.getTg()+1);
            String mMessage = Helper.getPlayerRepresentation(player, activeMap, activeMap.getGuild(), true)+" Since you have Muaat commander unlocked, 1tg has been added automatically ("+old+"->"+newTg+")";
            if(activeMap.isFoWMode())
            {
                MessageHelper.sendMessageToChannel(player.getPrivateChannel(),mMessage);
            }
            else{
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), mMessage);
            }
            ButtonHelperFactionSpecific.pillageCheck(player, activeMap);
        }
    }
    public static void offerTerraformButtons(Player player, Map activeMap, GenericInteractionCreateEvent event) {
        List<Button> buttons = new ArrayList<Button>();
        for(String planet : player.getPlanets()){
            UnitHolder unitHolder = activeMap.getPlanetsInfo().get(planet);
            Planet planetReal = (Planet) unitHolder;
            boolean oneOfThree = false;
            if (planetReal != null && planetReal.getOriginalPlanetType() != null && (planetReal.getOriginalPlanetType().equalsIgnoreCase("industrial") || planetReal.getOriginalPlanetType().equalsIgnoreCase("cultural") || planetReal.getOriginalPlanetType().equalsIgnoreCase("hazardous"))) {
                oneOfThree = true;
            }
            if ( oneOfThree || planet.contains("custodiavigilia")) {
                buttons.add(Button.success("terraformPlanet_"+planet, Helper.getPlanetRepresentation(planet, activeMap)));
            }
        }
        String message = "Use buttons to select which planet to terraform";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
    }
    public static List<Button> getButtonsToTakeSomeonesAC(Map activeMap, Player thief, Player victim)
    {
        List<Button> takeACs = new ArrayList<>();
        String secretScoreMsg = "_ _\nClick a button to take an Action Card";
        List<Button> acButtons = ACInfo.getToBeStolenActionCardButtons(activeMap, victim);
        if (acButtons != null && !acButtons.isEmpty()) {
            List<MessageCreateData> messageList = MessageHelper.getMessageCreateDataObjects(secretScoreMsg, acButtons);
            ThreadChannel cardsInfoThreadChannel = thief.getCardsInfoThread(activeMap);
            for (MessageCreateData message : messageList) {
                cardsInfoThreadChannel.sendMessage(message).queue();
            }
        }
        return takeACs;
    }
    public static void umbatTile(String buttonID, ButtonInteractionEvent event, Map activeMap, Player player, String ident){
        String pos = buttonID.replace("umbatTile_", "");
        List<Button> buttons = new ArrayList<Button>();
        buttons = Helper.getPlaceUnitButtons(event, player, activeMap,
                activeMap.getTileByPosition(pos), "muaatagent", "place");
        String message = Helper.getPlayerRepresentation(player, activeMap) + " Use the buttons to produce units. ";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        event.getMessage().delete().queue();
    }
    public static void hacanAgentRefresh(String buttonID, ButtonInteractionEvent event, Map activeMap, Player player, String ident, String trueIdentity){
        String faction = buttonID.replace("hacanAgentRefresh_", "");
        Player p2 = Helper.getPlayerFromColorOrFaction(activeMap, faction);
        String message = "";
        if(p2.hasLeader("hacanagent")){
            p2.setCommodities(p2.getCommodities()+2);
            message = trueIdentity+"Increased your commodities by two";
        }else{
            p2.setCommodities(p2.getCommoditiesTotal());
            ButtonHelper.resolveMinisterOfCommerceCheck(activeMap, p2, event);
            message = "Refreshed " +p2.getColor()+ "'s commodities";
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        event.getMessage().delete().queue();
    }

    public static void distantSuns(String buttonID, ButtonInteractionEvent event, Map activeMap, Player player, String ident){
        String bID = buttonID.replace("distant_suns_", "");
        String[] info = bID.split("_");
        String message = "";
        if (info[0].equalsIgnoreCase("decline")) {
            message = "Rejected Distant Suns Ability";
            new ExpPlanet().explorePlanet(event, Helper.getTileFromPlanet(info[1], activeMap), info[1], info[2],
                    player, true, activeMap, 1, false);
        } else {
            message = "Exploring twice";
            new ExpPlanet().explorePlanet(event, Helper.getTileFromPlanet(info[1], activeMap), info[1], info[2],
                    player, true, activeMap, 2, false);
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), message);
        event.getMessage().delete().queue();
    }
    public static void mageon(String buttonID, ButtonInteractionEvent event, Map activeMap, Player player, String ident, String trueIdentity){
        buttonID = buttonID.replace("takeAC_", "");
        int acNum = Integer.parseInt(buttonID.split("_")[0]);
        String faction2 = buttonID.split("_")[1];
        Player player2 = Helper.getPlayerFromColorOrFaction(activeMap, faction2);
        String ident2 = Helper.getPlayerRepresentation(player2, activeMap, activeMap.getGuild(), false);
        String message2 = trueIdentity + " took AC #" + acNum + " from " + ident2;
        String acID = null;
        for (java.util.Map.Entry<String, Integer> so : player2.getActionCards().entrySet()) {
            if (so.getValue().equals(acNum)) {
                acID = so.getKey();
            }
        }
        if (activeMap.isFoWMode()) {
            message2 = "Someone took AC #" + acNum + " from " + player2.getColor();
            MessageHelper.sendMessageToChannel(player.getPrivateChannel(), message2);
            MessageHelper.sendMessageToChannel(player2.getPrivateChannel(), message2);
        } else {
            MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(), message2);
        }
        player2.removeActionCard(acNum);
        player.setActionCard(acID);
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(activeMap), Helper.getPlayerRepresentation(player, activeMap, activeMap.getGuild(), true) + "Acquired " + acID);
        MessageHelper.sendMessageToChannel(player2.getCardsInfoThread(activeMap), "# "+ Helper.getPlayerRepresentation(player2, activeMap, activeMap.getGuild(), true)+" Lost " + acID +" to mageon");
        ACInfo.sendActionCardInfo(activeMap, player2);
        ACInfo.sendActionCardInfo(activeMap, player);
        event.getMessage().delete().queue();
    }
    public static void pillage(String buttonID, ButtonInteractionEvent event, Map activeMap, Player player, String ident, String finsFactionCheckerPrefix){
        buttonID = buttonID.replace("pillage_", "");
        String colorPlayer = buttonID.split("_")[0];
        String checkedStatus = buttonID.split("_")[1];
        Player pillaged = Helper.getPlayerFromColorOrFaction(activeMap, colorPlayer);
        if(checkedStatus.contains("unchecked")){
                List<Button> buttons = new ArrayList<Button>();
            String message2 =  "Please confirm this is a valid pillage opportunity and that you wish to pillage.";
            buttons.add(Button.danger(finsFactionCheckerPrefix+"pillage_"+pillaged.getColor()+"_checked","Pillage"));
            buttons.add(Button.success(finsFactionCheckerPrefix+"deleteButtons","Delete these buttons"));
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
        }else{
            MessageChannel channel1 = activeMap.getMainGameChannel();
            MessageChannel channel2 = activeMap.getMainGameChannel();
            String pillagerMessage = Helper.getPlayerRepresentation(player, activeMap, activeMap.getGuild(), true) + " you pillaged, your tgs have gone from "+player.getTg() +" to "+(player.getTg()+1) +".";
            String pillagedMessage = Helper.getPlayerRepresentation(pillaged, activeMap, activeMap.getGuild(), true) + " you have been pillaged";
            if(activeMap.isFoWMode()){
                channel1 = pillaged.getPrivateChannel();
                channel2 = player.getPrivateChannel();
            }
            if(pillaged.getCommodities()>0){
                pillagedMessage = pillagedMessage+ ", your comms have gone from "+pillaged.getCommodities() +" to "+(pillaged.getCommodities()-1) +".";
                pillaged.setCommodities(pillaged.getCommodities()-1);

            } else {
                pillagedMessage = pillagedMessage+ ", your tgs have gone from "+pillaged.getTg() +" to "+(pillaged.getTg()-1) +".";
                pillaged.setTg(pillaged.getTg()-1);
            }
            player.setTg(player.getTg()+1);
            MessageHelper.sendMessageToChannel(channel2, pillagerMessage);
            MessageHelper.sendMessageToChannel(channel1, pillagedMessage);
        }
        event.getMessage().delete().queue();
    }
    public static void terraformPlanet(String buttonID, ButtonInteractionEvent event, Map activeMap, Player player, String ident){
        String planet = buttonID.replace("terraformPlanet_","");
        UnitHolder unitHolder = activeMap.getPlanetsInfo().get(planet);
        Planet planetReal = (Planet) unitHolder;
        planetReal.addToken(Constants.ATTACHMENT_TITANSPN_PNG);
        MessageHelper.sendMessageToChannel(event.getChannel(), "Attached terraform to "+Helper.getPlanetRepresentation(planet, activeMap));
        event.getMessage().delete().queue();
    }
    public static void yinAgent(String buttonID, ButtonInteractionEvent event, Map activeMap, Player player, String ident, String trueIdentity){
        List<Button> buttons = ButtonHelper.getButtonsForAgentSelection(activeMap, buttonID);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), trueIdentity+ " Use buttons to select faction to give agent to.", buttons);
        String exhaustedMessage = event.getMessage().getContentRaw();
                    List<ActionRow> actionRow2 = new ArrayList<>();
                for (ActionRow row : event.getMessage().getActionRows()) {
                    List<ItemComponent> buttonRow = row.getComponents();
                    int buttonIndex = buttonRow.indexOf(event.getButton());
                    if (buttonIndex > -1) {
                        buttonRow.remove(buttonIndex);
                    }
                    if (buttonRow.size() > 0) {
                        actionRow2.add(ActionRow.of(buttonRow));
                    }
                }
        event.getMessage().editMessage(exhaustedMessage).setComponents(actionRow2).queue();
    }
    public static void giveKeleresCommsNTg(Map activeMap, GenericInteractionCreateEvent event){

        for(Player player : activeMap.getPlayers().values()){
            if(player.isRealPlayer() && player.hasAbility("council_patronage")){
                MessageChannel channel = activeMap.getActionsChannel();
                if(activeMap.isFoWMode()){
                    channel = player.getPrivateChannel();
                }
                player.setTg(player.getTg()+1);
                player.setCommodities(player.getCommoditiesTotal());
                String message = Helper.getPlayerRepresentation(player, activeMap, activeMap.getGuild(), true) + " due to your council patronage ability, 1tg has been added to your total and your commodities have been refreshed";
                MessageHelper.sendMessageToChannel(channel, message);
                ButtonHelperFactionSpecific.pillageCheck(player, activeMap);
                ButtonHelper.resolveMinisterOfCommerceCheck(activeMap, player, event);

            }
        }
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
         List<Tile> tiles = ButtonHelper.getTilesOfPlayersSpecificUnit(activeMap, EmpyPlayer, "mech");
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
    public static void yssarilCommander(String buttonID, ButtonInteractionEvent event, Map activeMap, Player player, String ident){
        buttonID = buttonID.replace("yssarilcommander_", "");
        String enemyFaction = buttonID.split("_")[1];
        Player enemy = Helper.getPlayerFromColorOrFaction(activeMap, enemyFaction);
        String message = "";
        String type = buttonID.split("_")[0];
        if(type.equalsIgnoreCase("ac")){
            ShowAllAC.showAll(enemy, player, activeMap);
            message = "Yssaril commander used to look at ACs";
        }
        if(type.equalsIgnoreCase("so")){
            new ShowAllSO().showAll(enemy, player, activeMap);
            message = "Yssaril commander used to look at SOs";
        }
        if(type.equalsIgnoreCase("pn")){
            new ShowAllPN().showAll(enemy, player, activeMap, false);
            message = "Yssaril commander used to look at PNs";
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), message);
        if(activeMap.isFoWMode()){
            MessageHelper.sendMessageToChannel(enemy.getPrivateChannel(), message);
        }
        event.getMessage().delete().queue();
    }

    public static void starforgeTile(String buttonID, ButtonInteractionEvent event, Map activeMap, Player player, String ident){
        String pos = buttonID.replace("starforgeTile_", "");
        List<Button> buttons = new ArrayList<Button>();
        Button starforgerStroter = Button.danger("starforge_destroyer_" + pos, "Starforge Destroyer")
                .withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("destroyer")));
        buttons.add(starforgerStroter);
        Button starforgerFighters = Button.danger("starforge_fighters_" + pos, "Starforge 2 Fighters")
                .withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("fighter")));
        buttons.add(starforgerFighters);
        String message = "Use the buttons to select what you would like to starforge.";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        event.getMessage().delete().queue();
    }
    public static void starforge(String buttonID, ButtonInteractionEvent event, Map activeMap, Player player, String ident){
        String unitNPlace = buttonID.replace("starforge_", "");
        String unit = unitNPlace.split("_")[0];
        String pos = unitNPlace.split("_")[1];
        Tile tile = activeMap.getTileByPosition(pos);
        String successMessage = "Reduced strategy pool CCs by 1 (" + (player.getStrategicCC()) + "->"
                + (player.getStrategicCC() - 1) + ")";
        player.setStrategicCC(player.getStrategicCC() - 1);
        ButtonHelperFactionSpecific.resolveMuaatCommanderCheck(player, activeMap, event);
        MessageHelper.sendMessageToChannel(event.getChannel(), successMessage);
        List<Button> buttons = ButtonHelper.getStartOfTurnButtons(player, activeMap, true, event);
        if (unit.equals("destroyer")) {
            new AddUnits().unitParsing(event, player.getColor(), tile, "1 destroyer", activeMap);
            successMessage = "Produced 1 " + Helper.getEmojiFromDiscord("destroyer") + " in tile "
                    + tile.getRepresentationForButtons(activeMap, player) + ".";

        } else {
            new AddUnits().unitParsing(event, player.getColor(), tile, "2 ff", activeMap);
            successMessage = "Produced 2 " + Helper.getEmojiFromDiscord("fighter") + " in tile "
                    + tile.getRepresentationForButtons(activeMap, player) + ".";
        }
        successMessage = ButtonHelper.putInfWithMechsForStarforge(pos, successMessage, activeMap, player, event);

        MessageHelper.sendMessageToChannel(event.getChannel(), successMessage);
        String message = "Use buttons to end turn or do another action";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        event.getMessage().delete().queue();
    }
    public static void titansCommanderUsage(String buttonID, ButtonInteractionEvent event, Map activeMap, Player player, String ident){
        int cTG = player.getTg();
        int fTG = cTG+1;
        player.setTg(fTG);
        String msg = " used Titans commander to gain a tg ("+cTG+"->"+fTG+"). ";
        String exhaustedMessage = event.getMessage().getContentRaw();
            List<ActionRow> actionRow2 = new ArrayList<>();
        for (ActionRow row : event.getMessage().getActionRows()) {
            List<ItemComponent> buttonRow = row.getComponents();
            int buttonIndex = buttonRow.indexOf(event.getButton());
            if (buttonIndex > -1) {
                buttonRow.remove(buttonIndex);
            }
            if (buttonRow.size() > 0) {
                actionRow2.add(ActionRow.of(buttonRow));
            }
        }
        if (!exhaustedMessage.contains("Click the names")) {
            exhaustedMessage = exhaustedMessage + ", "+msg;
        } else {
            exhaustedMessage = ident + msg;
        }
        event.getMessage().editMessage(exhaustedMessage).setComponents(actionRow2).queue();
    }
    public static void replaceSleeperWith(String buttonID, ButtonInteractionEvent event, Map activeMap, Player player, String ident){
        buttonID = buttonID.replace("replaceSleeperWith_", "");
        String planetName = buttonID.split("_")[1];
        String unit = buttonID.split("_")[0];
        String message = "";
        new SleeperToken().addOrRemoveSleeper(event, activeMap, planetName, player);
        if(unit.equalsIgnoreCase("mech")){
            new AddUnits().unitParsing(event, player.getColor(),activeMap.getTile(AliasHandler.resolveTile(planetName)), "mech " + planetName + ", inf "+planetName,activeMap);
            message = ident + " replaced a sleeper on " + Helper.getPlanetRepresentation(planetName,activeMap) + " with a "+ Helper.getEmojiFromDiscord("mech") +" and "+ Helper.getEmojiFromDiscord("infantry");
        }else{
            new AddUnits().unitParsing(event, player.getColor(),activeMap.getTile(AliasHandler.resolveTile(planetName)), "pds " + planetName,activeMap);
            message = ident + " replaced a sleeper on " + Helper.getPlanetRepresentation(planetName,activeMap) + " with a "+ Helper.getEmojiFromDiscord("pds");
            if(player.getLeaderIDs().contains("titanscommander") && !player.hasLeaderUnlocked("titanscommander")){
                ButtonHelper.commanderUnlockCheck(player, activeMap, "titans", event);
            }
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        event.getMessage().delete().queue();
    }

}