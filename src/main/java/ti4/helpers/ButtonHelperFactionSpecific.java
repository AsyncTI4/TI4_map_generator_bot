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
import ti4.commands.cardsso.SOInfo;
import ti4.commands.cardsso.ShowAllSO;
import ti4.commands.explore.ExpPlanet;
import ti4.commands.special.SleeperToken;
import ti4.commands.tokens.AddCC;
import ti4.commands.units.AddUnits;
import ti4.commands.units.RemoveUnits;
import ti4.generator.GenerateTile;
import ti4.generator.Mapper;
import ti4.map.Leader;
import ti4.map.Map;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;

public class ButtonHelperFactionSpecific {

    public static List<Button> getYinAgentButtons(Player player, Map activeMap, String pos) {
        List<Button> buttons = new ArrayList<>();
        Tile tile = activeMap.getTileByPosition(pos);
        String placePrefix = "placeOneNDone_skipbuild";
        String tp = tile.getPosition();
        Button ff2Button = Button.success("FFCC_"+player.getFaction()+"_"+placePrefix+"_2ff_"+tp, "Place 2 Fighters" );
        ff2Button = ff2Button.withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("fighter")));
        buttons.add(ff2Button);
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            if (unitHolder instanceof Planet planet){
                String pp = planet.getName();
                Button inf2Button = Button.success("FFCC_"+player.getFaction()+"_"+placePrefix+"_2gf_"+pp, "Place 2 Infantry on "+Helper.getPlanetRepresentation(pp, activeMap) );
                inf2Button = inf2Button.withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("infantry")));
                buttons.add(inf2Button);
            }
        }
        return buttons;
    }

    public static void resolveResearchAgreementCheck(Player player, String tech, Map activeMap){
        if(Helper.getPlayerFromColorOrFaction(activeMap, Mapper.getPromissoryNoteOwner("ra")) == player){
            if(Mapper.getTech(AliasHandler.resolveTech(tech)).getFaction().equals("")){
                for(Player p2 : activeMap.getRealPlayers()){
                    if(p2 == player){
                        continue;
                    }
                    if(p2.getPromissoryNotes().containsKey("ra") && !p2.getTechs().contains(tech)){
                        String msg = ButtonHelper.getTrueIdentity(p2, activeMap) + " the RA owner has researched the tech "+Helper.getTechRepresentation(AliasHandler.resolveTech(tech)) +"Use the below button if you want to play RA to get it.";
                        Button transact = Button.success("resolvePNPlay_ra_"+AliasHandler.resolveTech(tech), "Acquire "+ tech);
                        List<Button> buttons = new ArrayList<Button>();
                        buttons.add(transact);
                        buttons.add(Button.danger("deleteButtons","Decline"));
                        MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(activeMap), msg, buttons);
                    }
                }
            }
        }
    }
    public static void resolveMilitarySupportCheck(Player player,  Map activeMap){
        if(Helper.getPlayerFromColorOrFaction(activeMap, Mapper.getPromissoryNoteOwner("ms")) == player){
            for(Player p2 : activeMap.getRealPlayers()){
                if(p2 == player){
                    continue;
                }
                if(p2.getPromissoryNotes().containsKey("ms")){
                    String msg = ButtonHelper.getTrueIdentity(p2, activeMap) + " the Military Support owner has started their turn, use the button to play Military Support if you want";
                    Button transact = Button.success("resolvePNPlay_ms", "Play Military Support ");
                    List<Button> buttons = new ArrayList<Button>();
                    buttons.add(transact);
                    buttons.add(Button.danger("deleteButtons","Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(activeMap), msg, buttons);
                }
            }
        }
    }
    public static void resolveNekroCommanderCheck(Player player, String tech, Map activeMap){
        if(activeMap.playerHasLeaderUnlockedOrAlliance(player, "nekrocommander")){
            if(Mapper.getTech(AliasHandler.resolveTech(tech)).getFaction().equals("") || !player.hasAbility("technological_singularity")){
                List<Button> buttons = new ArrayList<Button>();
                if(player.hasAbility("scheming")){
                    buttons.add(Button.success("draw_2_ACDelete", "Draw 2 AC (With Scheming)"));
                }else{
                    buttons.add(Button.success("draw_1_ACDelete", "Draw 1 AC"));
                }
                 buttons.add(Button.danger("deleteButtons", "Delete These Buttons"));
                MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeMap), Helper.getPlayerRepresentation(player, activeMap, activeMap.getGuild(), true)+ " You gained tech while having Nekro commander, use buttons to resolve. ", buttons);
            }
        }
    }

    public static List<Button> getButtonsForPossibleTechForNekro(Player nekro, List<String> currentList, Map activeMap){
        List<Button> techToGain = new ArrayList<Button>();
        for(String tech : currentList){
            techToGain.add(Button.success("getTech_"+Mapper.getTech(tech).getName(), Mapper.getTech(tech).getName()));
        }
        return techToGain;
    }
    public static List<String> getPossibleTechForNekroToGainFromPlayer(Player nekro, Player victim, List<String> currentList, Map activeMap){
        List<String> techToGain = new ArrayList<String>();
        techToGain.addAll(currentList);
        for(String tech : victim.getTechs()){
            if(!nekro.getTechs().contains(tech) && !techToGain.contains(tech) && !tech.equalsIgnoreCase("iihq")){
                techToGain.add(tech);
            }
        }
        return techToGain;
    }
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
                                        List<Button> buttons = ButtonHelper.getButtonsForPictureCombats(activeMap,  tile.getPosition(), p1, player2, "ground");
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
     public static void resolveLetnevCommanderCheck(Player player, Map activeMap, GenericInteractionCreateEvent event) {
        if(activeMap.playerHasLeaderUnlockedOrAlliance(player, "letnevcommander")){
            int old = player.getTg();
            int newTg = player.getTg()+1;
            player.setTg(player.getTg()+1);
            String mMessage = Helper.getPlayerRepresentation(player, activeMap, activeMap.getGuild(), true)+" Since you have Barony commander unlocked, 1tg has been added automatically ("+old+"->"+newTg+")";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), mMessage);
            ButtonHelperFactionSpecific.pillageCheck(player, activeMap);
        }
     }
     public static List<Button> getEmpyHeroButtons(Player player, Map activeMap){
        String finChecker = "FFCC_"+player.getFaction() + "_";
        List<Button> empties = new ArrayList<Button>();
        for(Tile tile :activeMap.getTileMap().values()){
            if(tile.getUnitHolders().values().size() > 1 || !FoWHelper.playerHasShipsInSystem(player, tile)){
                continue;
            }
            empties.add(Button.primary(finChecker+"exploreFront_"+tile.getPosition(), "Explore "+tile.getRepresentationForButtons(activeMap, player)));
        }
        return empties;
    }
    public static boolean isCabalBlockadedByPlayer(Player player, Map activeMap, Player cabal){
        List<Tile> tiles = ButtonHelper.getTilesOfPlayersSpecificUnit(activeMap, cabal, "csd");
        for(Tile tile : tiles){
            if(FoWHelper.playerHasShipsInSystem(player, tile) && !FoWHelper.playerHasShipsInSystem(cabal, tile)){
                return true;
            }
        }
        return false;
    }

    public static void cabalEatsUnit(Player player, Map activeMap, Player cabal, int amount, String unit, GenericInteractionCreateEvent event){
        String msg = Helper.getPlayerRepresentation(cabal, activeMap, activeMap.getGuild(), true)+" has failed to eat "+amount+" of the "+unit +"s owned by " + Helper.getPlayerRepresentation(player, activeMap) + " because they were blockaded. Wah-wah.";
        if(!isCabalBlockadedByPlayer(player, activeMap, cabal)){
            msg = Helper.getFactionIconFromDiscord(cabal.getFaction())+" has devoured "+amount+" of the "+unit +"s owned by " + player.getColor() + ". Chomp chomp.";
            String color = player.getColor();
            String unitP = AliasHandler.resolveUnit(unit);
            if (unitP.contains("ff") || unitP.contains("gf")) {
                color = cabal.getColor();
            }
            msg = msg.replace("Infantrys","infantry");
            if (unitP.contains("sd") || unitP.contains("pds")) {
                return;
            }
            
            new AddUnits().unitParsing(event, color, cabal.getNomboxTile(), amount +" " +unit, activeMap);
        }
        if(activeMap.isFoWMode()){
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(cabal, activeMap), msg);
        }else{
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        }
        
    }
    public static void executeCabalHero(String buttonID, Player player, Map activeMap, ButtonInteractionEvent event){
        String pos = buttonID.replace("cabalHeroTile_","");
        Tile tile = activeMap.getTileByPosition(pos);
        Player cabal = player;
        UnitHolder space = tile.getUnitHolders().get("space");
        HashMap<String, Integer> units1 = space.getUnits();
        String cID = Mapper.getColorID(player.getColor());
        HashMap<String, Integer> units = new HashMap<String, Integer>();
        units.putAll(units1);
        for(Player p2 : activeMap.getRealPlayers()){
            if(FoWHelper.playerHasShipsInSystem(p2, tile) && !ButtonHelperFactionSpecific.isCabalBlockadedByPlayer(p2, activeMap, cabal)){
                ButtonHelper.riftAllUnitsInASystem(pos, event, activeMap, p2, Helper.getFactionIconFromDiscord(p2.getFaction()), cabal);
            }
            if(FoWHelper.playerHasShipsInSystem(p2, tile) && ButtonHelperFactionSpecific.isCabalBlockadedByPlayer(p2, activeMap, cabal)){
                String msg = Helper.getPlayerRepresentation(player, activeMap, activeMap.getGuild(), true)+" has failed to eat units owned by " + Helper.getPlayerRepresentation(player, activeMap) + " because they were blockaded. Wah-wah.";
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeMap), msg);
            }
        }

    }

    public static List<Button> getCabalHeroButtons(Player player, Map activeMap){
        String finChecker = "FFCC_"+player.getFaction() + "_";
        List<Button> empties = new ArrayList<Button>();
        List<Tile> tiles = ButtonHelper.getTilesOfPlayersSpecificUnit(activeMap, player, "csd");
        List<Tile> adjtiles = new ArrayList<Tile>();
        for(Tile tile : tiles){
            for(String pos : FoWHelper.getAdjacentTiles(activeMap, tile.getPosition(), player, false)){
                Tile tileToAdd = activeMap.getTileByPosition(pos);
                if(!adjtiles.contains(tileToAdd) && !tile.getPosition().equalsIgnoreCase(pos)){
                    adjtiles.add(tileToAdd);
                }
            }
        }
        for(Tile tile : adjtiles){
            empties.add(Button.primary(finChecker+"cabalHeroTile_"+tile.getPosition(), "Roll for units in "+tile.getRepresentationForButtons(activeMap, player)));
        }
        return empties;
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
                    MessageChannel channel = activeMap.getActionsChannel();
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
        if(!player2.getActionCards().values().contains((Integer) acNum)){
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find that AC, no AC added/lost");
            return;
       }
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
        MessageHelper.sendMessageToChannel(player2.getCardsInfoThread(activeMap), "# "+ Helper.getPlayerRepresentation(player2, activeMap, activeMap.getGuild(), true)+" Lost " + acID +" to mageon (or perhaps Yssaril hero)");
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
            buttons.add(Button.danger(finsFactionCheckerPrefix+"pillage_"+pillaged.getColor()+"_checked","Pillage a TG"));
            if(pillaged.getCommodities()>0){
                buttons.add(Button.danger(finsFactionCheckerPrefix+"pillage_"+pillaged.getColor()+"_checkedcomm","Pillage a Commodity"));
            }
            buttons.add(Button.success(finsFactionCheckerPrefix+"deleteButtons","Delete these buttons"));
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
        }else{
            MessageChannel channel1 = ButtonHelper.getCorrectChannel(pillaged, activeMap);
            MessageChannel channel2 = ButtonHelper.getCorrectChannel(player, activeMap);
            String pillagerMessage = Helper.getPlayerRepresentation(player, activeMap, activeMap.getGuild(), true) + " you pillaged, your tgs have gone from "+player.getTg() +" to "+(player.getTg()+1) +".";
            String pillagedMessage = Helper.getPlayerRepresentation(pillaged, activeMap, activeMap.getGuild(), true) + " you have been pillaged";

            if(pillaged.getCommodities()>0 && checkedStatus.contains("checkedcomm")){
                pillagedMessage = pillagedMessage+ ", your comms have gone from "+pillaged.getCommodities() +" to "+(pillaged.getCommodities()-1) +".";
                pillaged.setCommodities(pillaged.getCommodities()-1);
            } else {
                pillagedMessage = pillagedMessage+ ", your tgs have gone from "+pillaged.getTg() +" to "+(pillaged.getTg()-1) +".";
                pillaged.setTg(pillaged.getTg()-1);
            }
            player.setTg(player.getTg()+1);
            MessageHelper.sendMessageToChannel(channel2, pillagerMessage);
            MessageHelper.sendMessageToChannel(channel1, pillagedMessage);
            if (player.hasLeader("mentakagent")&&!player.getLeaderByID("mentakagent").isExhausted()) {
                List<Button> buttons = new ArrayList<Button>();
                Button winnuButton = Button.success("exhaustAgent_mentakagent_"+pillaged.getFaction(), "Use Mentak Agent To Draw ACs for you and pillaged player").withEmoji(Emoji.fromFormatted(Helper.getFactionIconFromDiscord("mentak")));
                buttons.add(winnuButton);
                buttons.add(Button.danger("deleteButtons", "Done"));
                MessageHelper.sendMessageToChannelWithButtons(channel2, "Wanna use Mentak Agent?", buttons);
            }
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
    public static List<Button> getMitosisOptions(Map activeMap, Player player){
        List<Button> buttons = new ArrayList<Button>();
        buttons.add(Button.success("mitosisInf", "Place an Infantry"));
        if(ButtonHelper.getNumberOfUnitsOnTheBoard(activeMap, player, "mech") < 4){
            buttons.add(Button.primary("mitosisMech", "Replace an Infantry With a Mech (DEPLOY)"));
        }
        return buttons;
    }
    public static void resolveMitosisInf(String buttonID, ButtonInteractionEvent event, Map activeMap, Player player, String ident){
        List<Button> buttons = new ArrayList<Button>();
        buttons.addAll(Helper.getPlanetPlaceUnitButtons(player, activeMap, "infantry", "placeOneNDone_skipbuild"));
        String message = ButtonHelper.getTrueIdentity(player, activeMap)+" Use buttons to put 1 infantry on a planet";
        
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeMap), ident + " is resolving mitosis");
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeMap), message, buttons);
        event.getMessage().delete().queue();
    }
    public static void resolveMitosisMech(String buttonID, ButtonInteractionEvent event, Map activeMap, Player player, String ident, String finChecker){
        List<Button> buttons = new ArrayList<Button>();
        buttons.addAll(ButtonHelperFactionSpecific.getPlanetPlaceUnitButtonsForMechMitosis(player, activeMap, finChecker));
        String message = ButtonHelper.getTrueIdentity(player, activeMap)+" Use buttons to replace 1 infantry with a mech";
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeMap), ident + " is resolving mitosis");
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeMap), message, buttons);
        event.getMessage().delete().queue();
    }
    public static List<Button> getPlanetPlaceUnitButtonsForMechMitosis(Player player, Map activeMap, String finChecker) {
        List<Button> planetButtons = new ArrayList<>();
        List<String> planets = new ArrayList<>(player.getPlanets(activeMap));
        List<String> tiles = new ArrayList<String>();
        for (String planet : planets) {
            Tile tile =  activeMap.getTile(AliasHandler.resolveTile(planet));
            if(tiles.contains(tile.getPosition())){
                continue;
            }else{
                tiles.add(tile.getPosition());
            }
            for(UnitHolder unitHolder :tile.getUnitHolders().values()){
                if(unitHolder.getName().equalsIgnoreCase("space")){
                    continue;
                }
                String colorID = Mapper.getColorID(player.getColor());
                int numInf = 0;
                String infKey = colorID + "_gf.png";
                if (unitHolder.getUnits() != null) {
                    if (unitHolder.getUnits().get(infKey) != null) {
                        numInf = unitHolder.getUnits().get(infKey);
                    }
                }
                if (numInf > 0) {
                    Button button = Button.success(finChecker+"mitoMechPlacement_"+unitHolder.getName().toLowerCase().replace("'","").replace("-","").replace(" ",""), "Place mech on " + Helper.getPlanetRepresentation(unitHolder.getName(), activeMap));
                    planetButtons.add(button);
                }
            }
        }
        return planetButtons;
    }
    public static void resolveMitosisMechPlacement(String buttonID, ButtonInteractionEvent event, Map activeMap, Player player, String ident){
        String planetName = buttonID.replace("mitoMechPlacement_","");
        new AddUnits().unitParsing(event, player.getColor(),
                        activeMap.getTile(AliasHandler.resolveTile(planetName)), "mech "+planetName, activeMap);
        String key = Mapper.getUnitID(AliasHandler.resolveUnit("infantry"), player.getColor());
        //activeMap.getTileByPosition(pos1).removeUnit(planet,key, amount);
        new RemoveUnits().removeStuff(event,activeMap.getTile(AliasHandler.resolveTile(planetName)), 1, planetName, key, player.getColor(), false);
        String successMessage = ident +" Replaced an infantry with a mech on "
        + Helper.getPlanetRepresentation(planetName, activeMap) + ".";
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeMap), successMessage);
        event.getMessage().delete().queue();
    }
    

    public static void exhaustAgent(String buttonID, ButtonInteractionEvent event, Map activeMap, Player player, String ident){
        String agent = buttonID.replace("exhaustAgent_","");
            String rest = agent;
        String trueIdentity = ButtonHelper.getTrueIdentity(player, activeMap);
            if(agent.contains("_"))
            {
                agent = agent.substring(0, agent.indexOf("_"));
            }
            Leader playerLeader = player.getLeader(agent);
            MessageChannel channel2 =activeMap.getMainGameChannel();
            if(activeMap.isFoWMode()){
                channel2 = player.getPrivateChannel();
            }
            playerLeader.setExhausted(true);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),Helper.getFactionLeaderEmoji(playerLeader));
            StringBuilder messageText = new StringBuilder(Helper.getPlayerRepresentation(player, activeMap))
                    .append(" exhausted ").append(Helper.getLeaderFullRepresentation(playerLeader));
            MessageHelper.sendMessageToChannel(channel2,messageText.toString());
            if(agent.equalsIgnoreCase("naazagent")){
                List<Button> buttons = ButtonHelper.getButtonsToExploreAllPlanets(player, activeMap);
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),"Use buttons to explore", buttons);
            }
            if(agent.equalsIgnoreCase("empyreanagent")){
                Button getTactic= Button.success("increase_tactic_cc", "Gain 1 Tactic CC");
                Button getFleet = Button.success("increase_fleet_cc", "Gain 1 Fleet CC");
                Button getStrat= Button.success("increase_strategy_cc", "Gain 1 Strategy CC");
                Button DoneGainingCC = Button.danger("deleteButtons", "Done Gaining CCs");
                List<Button> buttons = List.of(getTactic, getFleet, getStrat, DoneGainingCC);
                String message2 = trueIdentity + "! Your current CCs are "+Helper.getPlayerCCs(player)+". Use buttons to gain CCs";
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
            }
            if(agent.equalsIgnoreCase("nekroagent")){
                player.setTg(player.getTg()+2);
                ButtonHelperFactionSpecific.pillageCheck(player, activeMap);
                String message = trueIdentity+" increased your tgs by 2 ("+(player.getTg()-2)+"->"+player.getTg()+"). Use buttons in your cards info thread to discard an AC";
                MessageHelper.sendMessageToChannel(channel2, message);
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(activeMap), trueIdentity+" use buttons to discard", ACInfo.getDiscardActionCardButtons(activeMap, player, false));
            }
            if(agent.equalsIgnoreCase("hacanagent")){
               
                String message = trueIdentity+" select faction you wish to use your agent on";
                List<Button> buttons = AgendaHelper.getPlayerOutcomeButtons(activeMap, null, "hacanAgentRefresh", null);
                MessageHelper.sendMessageToChannelWithButtons(channel2, message, buttons);
            }
            if(agent.equalsIgnoreCase("xxchaagent")){
                String faction = rest.replace("xxchaagent_","");
                Player p2 = Helper.getPlayerFromColorOrFaction(activeMap, faction);
                String message = "Use buttons to ready a planet. Removing the infantry is not automated but is an option for you to do.";
                List<Button> ringButtons = ButtonHelper.getXxchaAgentReadyButtons(activeMap, p2);
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),Helper.getPlayerRepresentation(player, activeMap, activeMap.getGuild(), true)+ message, ringButtons);
            }
            if(agent.equalsIgnoreCase("yinagent")){
                String posNFaction = rest.replace("yinagent_","");
                String pos = posNFaction.split("_")[0];
                String faction = posNFaction.split("_")[1];
                Player p2 = Helper.getPlayerFromColorOrFaction(activeMap, faction);
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),Helper.getPlayerRepresentation(p2, activeMap, activeMap.getGuild(), true)+" Use buttons to resolve yin agent", ButtonHelperFactionSpecific.getYinAgentButtons(p2, activeMap, pos));
            }
            if(agent.equalsIgnoreCase("naaluagent")){
                String faction = rest.replace("naaluagent_","");
                Player p2 = Helper.getPlayerFromColorOrFaction(activeMap, faction);
                activeMap.setNaaluAgent(true);
                MessageChannel channel = event.getMessageChannel();
                if(activeMap.isFoWMode()){
                    channel = p2.getPrivateChannel();
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Sent buttons to the selected player");
                }
                String message = "Doing a tactical action. Please select the ring of the map that the system you want to activate is located in. Reminder that a normal 6 player map is 3 rings, with ring 1 being adjacent to Rex. Mallice is in the corner";
                List<Button> ringButtons = ButtonHelper.getPossibleRings(p2, activeMap);
                activeMap.resetCurrentMovedUnitsFrom1TacticalAction();
                MessageHelper.sendMessageToChannelWithButtons(channel,Helper.getPlayerRepresentation(p2, activeMap, activeMap.getGuild(), true)+" Use buttons to resolve tactical action from Naalu agent. Reminder it is not legal to do a tactical action in a home system.\n" + message, ringButtons);
            }
            if(agent.equalsIgnoreCase("mentakagent")){
                String faction = rest.replace("mentakagent_","");
                Player p2 = Helper.getPlayerFromColorOrFaction(activeMap, faction);
                activeMap.setNaaluAgent(true);
                String successMessage = ident+ " drew an AC.";
                String successMessage2 = ButtonHelper.getIdent(p2)+ " drew an AC.";
                activeMap.drawActionCard(player.getUserID());   
                activeMap.drawActionCard(p2.getUserID()); 
                if(player.hasAbility("scheming")){
                    activeMap.drawActionCard(player.getUserID());   
                    successMessage = successMessage + " Drew another AC for scheming. Please discard 1";
                }
                if(p2.hasAbility("scheming")){
                    activeMap.drawActionCard(p2.getUserID());   
                    successMessage2 = successMessage2 + " Drew another AC for scheming. Please discard 1";
                }
                ButtonHelper.checkACLimit(activeMap, event, player);
                ButtonHelper.checkACLimit(activeMap, event, p2);
                String headerText = Helper.getPlayerRepresentation(player, activeMap, activeMap.getGuild(), true) + " you got an AC from Mentak Agent";
                MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeMap, headerText);
                ACInfo.sendActionCardInfo(activeMap, player);
                String headerText2 = Helper.getPlayerRepresentation(p2, activeMap, activeMap.getGuild(), true) + " you got an AC from Mentak Agent";
                MessageHelper.sendMessageToPlayerCardsInfoThread(p2, activeMap, headerText2);
                ACInfo.sendActionCardInfo(activeMap, p2);
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeMap), successMessage);
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeMap), successMessage2);
            }
            if(agent.equalsIgnoreCase("sardakkagent")){
                String posNPlanet = rest.replace("sardakkagent_","");
                String pos = posNPlanet.split("_")[0];
                String planetName = posNPlanet.split("_")[1];
                new AddUnits().unitParsing(event, player.getColor(),
                            activeMap.getTileByPosition(pos), "2 gf " + planetName,
                            activeMap);
                String successMessage = ident+ " placed 2 " + Helper.getEmojiFromDiscord("infantry") + " on "+ Helper.getPlanetRepresentation(planetName, activeMap) + ".";
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeMap), successMessage);
            }
            if(agent.equalsIgnoreCase("muaatagent")){
                String faction = rest.replace("muaatagent_","");
                Player p2 = Helper.getPlayerFromColorOrFaction(activeMap, faction);
                MessageChannel channel = event.getMessageChannel();
                if(activeMap.isFoWMode()){
                    channel = p2.getPrivateChannel();
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Sent buttons to the selected player");
                }
                String message = "Use buttons to select which tile to Umbat in";
                List<Tile> tiles = ButtonHelper.getTilesOfPlayersSpecificUnit(activeMap, p2, "warsun");
                List<Tile> tiles2 = ButtonHelper.getTilesOfPlayersSpecificUnit(activeMap, p2, "fs");
                for(Tile tile : tiles2){
                    if(!tiles.contains(tile)){
                        tiles.add(tile);
                    }
                }
                List<Button> buttons = new ArrayList<Button>();
                for(Tile tile : tiles)
                {
                    Button starTile = Button.success("umbatTile_"+tile.getPosition(), tile.getRepresentationForButtons(activeMap, p2));
                    buttons.add(starTile);
                }
                MessageHelper.sendMessageToChannelWithButtons(channel,Helper.getPlayerRepresentation(p2, activeMap, activeMap.getGuild(), true) + message, buttons);
            }
            String exhaustedMessage = event.getMessage().getContentRaw();
            if(exhaustedMessage == null || exhaustedMessage.equalsIgnoreCase("")){
                exhaustedMessage ="Updated";
            }
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
            if(actionRow2.size() > 0 && !exhaustedMessage.contains("select the user of the agent")){
                 event.getMessage().editMessage(exhaustedMessage).setComponents(actionRow2).queue();
            }else{
                event.getMessage().delete().queue();
            }
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
    public static void resolveSardakkCommander(Map activeMap, Player p1, String buttonID, ButtonInteractionEvent event, String ident ){
        String mechorInf = buttonID.split("_")[1];
        String planet1= buttonID.split("_")[2];
        String planet2 = buttonID.split("_")[3];
        String planetRepresentation2 = Helper.getPlanetRepresentation(planet2, activeMap);
        String planetRepresentation = Helper.getPlanetRepresentation(planet1, activeMap);

        String message = ident + " moved 1 "+ mechorInf + " from " +planetRepresentation2 + " to "+planetRepresentation +" using Sardakk Commander";
         new RemoveUnits().unitParsing(event, p1.getColor(),
                            Helper.getTileFromPlanet(planet2, activeMap), "1 "+mechorInf + " "+planet2,
                            activeMap);
        new AddUnits().unitParsing(event, p1.getColor(),
                            Helper.getTileFromPlanet(planet1, activeMap), "1 "+mechorInf + " "+planet1,
                            activeMap);
       
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p1, activeMap), message);
        String exhaustedMessage = event.getMessage().getContentRaw();
        if(exhaustedMessage == null || exhaustedMessage.equalsIgnoreCase("")){
            exhaustedMessage ="Updated";
        }
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
        if(actionRow2.size() > 0 && !exhaustedMessage.contains("select the user of the agent")){
             event.getMessage().editMessage(exhaustedMessage).setComponents(actionRow2).queue();
        }else{
            event.getMessage().delete().queue();
        }
    }
    public static List<Button> getSardakkCommanderButtons(Map activeMap, Player player, GenericInteractionCreateEvent event) {
       
         Tile tile =  activeMap.getTileByPosition(activeMap.getActiveSystem());
        List<Button> buttons = new ArrayList<Button>();
        for(UnitHolder planetUnit : tile.getUnitHolders().values()){
            if(planetUnit.getName().equalsIgnoreCase("space")){
                continue;
            }
            Planet planetReal =  (Planet) planetUnit;
            String planet = planetReal.getName();    
            if (planetReal != null) {
                String planetId = planetReal.getName();
                String planetRepresentation = Helper.getPlanetRepresentation(planetId, activeMap);
                for(String pos2 : FoWHelper.getAdjacentTiles(activeMap, tile.getPosition(), player, false)){
                    Tile tile2 = activeMap.getTileByPosition(pos2);
                    if(AddCC.hasCC(event, player.getColor(), tile2)){
                        continue;
                    }
                     for(UnitHolder planetUnit2 : tile2.getUnitHolders().values()){
                        if(planetUnit2.getName().equalsIgnoreCase("space")){
                            continue;
                        }
                        Planet planetReal2 =  (Planet) planetUnit2;
                        String planet2 = planetReal2.getName(); 
                        if (planetReal2 != null) { 
                            int numMechs = 0;
                            int numInf = 0;
                            String colorID = Mapper.getColorID(player.getColor());
                            String mechKey = colorID + "_mf.png";
                            String infKey = colorID + "_gf.png";
                            if (planetUnit2.getUnits() != null) {
                                if (planetUnit2.getUnits().get(mechKey) != null) {
                                    numMechs =  planetUnit2.getUnits().get(mechKey);
                                }
                                if ( planetUnit2.getUnits().get(infKey) != null) {
                                    numInf = planetUnit2.getUnits().get(infKey);
                                }
                            }
                            String planetId2 = planetReal2.getName();
                            String planetRepresentation2 = Helper.getPlanetRepresentation(planetId2, activeMap);
                            if(numInf > 0){
                                buttons.add(Button.success("sardakkcommander_infantry_"+planetId+"_"+planetId2, "Commit 1 infantry from "+planetRepresentation2+" to "+planetRepresentation).withEmoji(Emoji.fromFormatted(Helper.getFactionIconFromDiscord("sardakk"))));
                            }
                            if(numMechs > 0){
                                buttons.add(Button.primary("sardakkcommander_mech_"+planetId+"_"+planetId2, "Commit 1 mech from "+planetRepresentation2+" to "+planetRepresentation).withEmoji(Emoji.fromFormatted(Helper.getFactionIconFromDiscord("sardakk"))));
                            }
                         }
                    }
                }
                
            }
        }
        return buttons;
     }
    public static List<Button> getSardakkAgentButtons(Map activeMap, Player player) {
       
         Tile tile =  activeMap.getTileByPosition(activeMap.getActiveSystem());
        List<Button> buttons = new ArrayList<Button>();
        for(UnitHolder planetUnit : tile.getUnitHolders().values()){
            if(planetUnit.getName().equalsIgnoreCase("space")){
                continue;
            }
            Planet planetReal =  (Planet) planetUnit;
            String planet = planetReal.getName();    
            if (planetReal != null  && player.getPlanets(activeMap).contains(planet)) {
                String planetId = planetReal.getName();
                String planetRepresentation = Helper.getPlanetRepresentation(planetId, activeMap);
                buttons.add(Button.success("exhaustAgent_sardakkagent_"+activeMap.getActiveSystem()+"_"+planetId, "Use Sardakk Agent on "+planetRepresentation).withEmoji(Emoji.fromFormatted(Helper.getFactionIconFromDiscord("sardakk"))));
            }
        }

        return buttons;

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