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
import ti4.commands.planet.PlanetAdd;
import ti4.commands.player.SendDebt;
import ti4.commands.special.SleeperToken;
import ti4.commands.tokens.AddCC;
import ti4.commands.units.AddUnits;
import ti4.commands.units.RemoveUnits;
import ti4.generator.GenerateTile;
import ti4.generator.Mapper;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;

public class ButtonHelperFactionSpecific {

     public static void resolveVadenSCDebt(Player player, int sc, Game activeGame){
        for(Player p2 : activeGame.getRealPlayers()){
            if(p2.getSCs().contains(sc) && p2 != player && p2.hasAbility("fine_print")){
                SendDebt.sendDebt(player, p2, 1);
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getTrueIdentity(player, activeGame)+" you sent 1 debt token to "+ButtonHelper.getIdentOrColor(p2, activeGame)+" due to their fine print ability");
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame), ButtonHelper.getTrueIdentity(p2, activeGame)+" you collected 1 debt token from "+ButtonHelper.getIdentOrColor(player, activeGame)+" due to your fine print ability. This is technically optional, done automatically for conveinance.");
            }
            break;
        }
    }

    public static String getAllOwnedPlanetTypes(Player player, Game activeGame){

        String types = "";
        for(String planetName : player.getPlanets(activeGame)){
            if(planetName.contains("custodia")){
                continue;
            }
            Planet planet = (Planet) ButtonHelper.getUnitHolderFromPlanetName(planetName, activeGame);
            String planetType = planet.getOriginalPlanetType();
            if (("industrial".equalsIgnoreCase(planetType) || "cultural".equalsIgnoreCase(planetType) || "hazardous".equalsIgnoreCase(planetType)) && !types.contains(planetType)) {
                types = types + planetType;
            }
            if (planet.getTokenList().contains("attachment_titanspn.png")) {
                types = types + "cultural";
                types = types + "industrial";
                types = types + "hazardous";
            }
        }

        return types;
    }

    public static void offerMahactInfButtons(Player player, Game activeGame){
        String message = ButtonHelper.getTrueIdentity(player, activeGame)+" Resolve Mahact infantry loss using the buttons";
        Button convert2CommButton = Button.success("convert_1_comms" , "Convert 1 Commodity Into TG").withEmoji(Emoji.fromFormatted(Emojis.Wash));
        Button get2CommButton = Button.primary("gain_1_comm_from_MahactInf", "Gain 1 Commodity").withEmoji(Emoji.fromFormatted(Emojis.comm));
        List<Button> buttons = List.of(convert2CommButton, get2CommButton, Button.danger("deleteButtons", "Done resolving"));
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), message, buttons);
    }
     public static void KeleresIIHQCCGainCheck(Player player, Game activeGame) {
        for(Player p2 : activeGame.getRealPlayers()){
            if(p2 == player){
                continue;
            }
            if(p2.hasTech("iihq")){
                String finChecker = "FFCC_"+p2.getFaction() + "_";
                Button getTactic= Button.success(finChecker+"increase_tactic_cc", "Gain 1 Tactic CC");
                Button getFleet = Button.success(finChecker+"increase_fleet_cc", "Gain 1 Fleet CC");
                Button getStrat= Button.success(finChecker+"increase_strategy_cc", "Gain 1 Strategy CC");
                Button DoneGainingCC = Button.danger("deleteButtons", "Done Gaining CCs");
                List<Button> buttons = List.of(getTactic, getFleet, getStrat, DoneGainingCC);
                String trueIdentity = Helper.getPlayerRepresentation(p2, activeGame, activeGame.getGuild(), true);
                String message = trueIdentity+" Due to your IIHQ tech, you get to gain 2 commmand counters when someone scores an imperial point.";
                String message2 = trueIdentity + "! Your current CCs are "+Helper.getPlayerCCs(p2)+". Use buttons to gain CCs";
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame), message);
                MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(p2, activeGame), message2, buttons);
                break;
            }
        }

     }

    public static List<Button> getYinAgentButtons(Player player, Game activeGame, String pos) {
        List<Button> buttons = new ArrayList<>();
        Tile tile = activeGame.getTileByPosition(pos);
        String placePrefix = "placeOneNDone_skipbuild";
        String tp = tile.getPosition();
        Button ff2Button = Button.success("FFCC_"+player.getFaction()+"_"+placePrefix+"_2ff_"+tp, "Place 2 Fighters" );
        ff2Button = ff2Button.withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("fighter")));
        buttons.add(ff2Button);
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            if (unitHolder instanceof Planet planet){
                String pp = planet.getName();
                Button inf2Button = Button.success("FFCC_"+player.getFaction()+"_"+placePrefix+"_2gf_"+pp, "Place 2 Infantry on "+Helper.getPlanetRepresentation(pp, activeGame) );
                inf2Button = inf2Button.withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("infantry")));
                buttons.add(inf2Button);
            }
        }
        return buttons;
    }

    public static void resolveResearchAgreementCheck(Player player, String tech, Game activeGame){
        if(Helper.getPlayerFromColorOrFaction(activeGame, Mapper.getPromissoryNoteOwner("ra")) == player){
            if("".equals(Mapper.getTech(AliasHandler.resolveTech(tech)).getFaction())){
                for(Player p2 : activeGame.getRealPlayers()){
                    if(p2 == player){
                        continue;
                    }
                    if(p2.getPromissoryNotes().containsKey("ra") && !p2.getTechs().contains(tech)){
                        String msg = ButtonHelper.getTrueIdentity(p2, activeGame) + " the RA owner has researched the tech "+Helper.getTechRepresentation(AliasHandler.resolveTech(tech)) +"Use the below button if you want to play RA to get it.";
                        Button transact = Button.success("resolvePNPlay_ra_"+AliasHandler.resolveTech(tech), "Acquire "+ tech);
                        List<Button> buttons = new ArrayList<>();
                        buttons.add(transact);
                        buttons.add(Button.danger("deleteButtons","Decline"));
                        MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(activeGame), msg, buttons);
                    }
                }
            }
        }
    }
    public static void resolveMilitarySupportCheck(Player player,  Game activeGame){
        if(Helper.getPlayerFromColorOrFaction(activeGame, Mapper.getPromissoryNoteOwner("ms")) == player){
            for(Player p2 : activeGame.getRealPlayers()){
                if(p2 == player){
                    continue;
                }
                if(p2.getPromissoryNotes().containsKey("ms")){
                    String msg = ButtonHelper.getTrueIdentity(p2, activeGame) + " the Military Support owner has started their turn, use the button to play Military Support if you want";
                    Button transact = Button.success("resolvePNPlay_ms", "Play Military Support ");
                    List<Button> buttons = new ArrayList<>();
                    buttons.add(transact);
                    buttons.add(Button.danger("deleteButtons","Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(activeGame), msg, buttons);
                }
            }
        }
    }
    public static void resolveNekroCommanderCheck(Player player, String tech, Game activeGame){
        if(activeGame.playerHasLeaderUnlockedOrAlliance(player, "nekrocommander")){
            if("".equals(Mapper.getTech(AliasHandler.resolveTech(tech)).getFaction()) || !player.hasAbility("technological_singularity")){
                List<Button> buttons = new ArrayList<>();
                if(player.hasAbility("scheming")){
                    buttons.add(Button.success("draw_2_ACDelete", "Draw 2 AC (With Scheming)"));
                }else{
                    buttons.add(Button.success("draw_1_ACDelete", "Draw 1 AC"));
                }
                 buttons.add(Button.danger("deleteButtons", "Delete These Buttons"));
                MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), true)+ " You gained tech while having Nekro commander, use buttons to resolve. ", buttons);
            }
        }
    }

    public static List<Button> getButtonsForPossibleTechForNekro(Player nekro, List<String> currentList, Game activeGame){
        List<Button> techToGain = new ArrayList<>();
        for(String tech : currentList){
            techToGain.add(Button.success("getTech_"+Mapper.getTech(tech).getName(), Mapper.getTech(tech).getName()));
        }
        return techToGain;
    }
    public static List<String> getPossibleTechForNekroToGainFromPlayer(Player nekro, Player victim, List<String> currentList, Game activeGame){
        List<String> techToGain = new ArrayList<>(currentList);
        for(String tech : victim.getTechs()){
            if(!nekro.getTechs().contains(tech) && !techToGain.contains(tech) && !"iihq".equalsIgnoreCase(tech)){
                techToGain.add(tech);
            }
        }
        return techToGain;
    }
    public static void removeSleeper(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident){
        buttonID = buttonID.replace("removeSleeperFromPlanet_", "");
        String planet = buttonID;
        String message = ident + " removed a sleeper from " + planet;
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        new SleeperToken().addOrRemoveSleeper(event, activeGame, planet, player);
        event.getMessage().delete().queue();
    }
    public static void replacePDSWithFS(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident){
        buttonID = buttonID.replace("replacePDSWithFS_", "");
        String planet = buttonID;
        String message = ident + " replaced "+ Helper.getEmojiFromDiscord("pds") +" on " + Helper.getPlanetRepresentation(planet, activeGame)+ " with a "+ Helper.getEmojiFromDiscord("flagship");
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        new AddUnits().unitParsing(event, player.getColor(), activeGame.getTile(AliasHandler.resolveTile(planet)), "flagship", activeGame);
        String key = Mapper.getUnitID(AliasHandler.resolveUnit("pds"), player.getColor());
        activeGame.getTile(AliasHandler.resolveTile(planet)).removeUnit(planet,key, 1);
        event.getMessage().delete().queue();
    }
    public static void firstStepOfChaos(Game activeGame, Player p1, ButtonInteractionEvent event){
        List<Button> buttons = new ArrayList<>();
        List<Tile> tiles = ButtonHelper.getTilesOfPlayersSpecificUnit(activeGame, p1, "spacedock");
        if(tiles.isEmpty()){
            tiles = ButtonHelper.getTilesOfPlayersSpecificUnit(activeGame, p1, "cabalspacedock");
        }
        for(Tile tile : tiles){
            Button tileButton = Button.success("produceOneUnitInTile_"+tile.getPosition()+"_chaosM", tile.getRepresentationForButtons(activeGame,p1));
            buttons.add(tileButton);
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Select which tile you would like to chaos map in.", buttons);
    }

    
    public static void lastStepOfYinHero(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String trueIdentity){
        String planetNInf = buttonID.replace("yinHeroInfantry_", "");
        String planet = planetNInf.split("_")[0];
        String amount = planetNInf.split("_")[1];
        TextChannel mainGameChannel = activeGame.getMainGameChannel();
        Tile tile = activeGame.getTile(AliasHandler.resolveTile(planet));
        
        new AddUnits().unitParsing(event, player.getColor(),
                        activeGame.getTile(AliasHandler.resolveTile(planet)), amount +" inf " + planet,
            activeGame);
        MessageHelper.sendMessageToChannel(event.getChannel(), trueIdentity+" Chose to land "+amount+" infantry on "+Helper.getPlanetRepresentation(planet, activeGame));
        UnitHolder unitHolder = tile.getUnitHolders().get(planet);
        for(Player player2 : activeGame.getRealPlayers()){
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
                 
                if(!activeGame.isFoWMode()){
                    MessageCreateBuilder baseMessageObject = new MessageCreateBuilder().addContent(messageCombat);
                    String threadName =  activeGame.getName() + "-yinHero-" + activeGame.getRound() + "-planet-" + planet+"-"+player.getFaction()+"-vs-"+player2.getFaction();
                    mainGameChannel.sendMessage(baseMessageObject.build()).queue(message_ -> {
                        ThreadChannelAction threadChannel = mainGameChannel.createThreadChannel(threadName, message_.getId());
                        threadChannel = threadChannel.setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_HOUR);
                        threadChannel.queue(m5 -> {
                            List<ThreadChannel> threadChannels = activeGame.getActionsChannel().getThreadChannels();
                            if (threadChannels != null) {
                                for (ThreadChannel threadChannel_ : threadChannels) {
                                    if (threadChannel_.getName().equals(threadName)) {
                                        MessageHelper.sendMessageToChannel(threadChannel_, Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), true) + Helper.getPlayerRepresentation(player2, activeGame, activeGame.getGuild(), true) + " Please resolve the interaction here. Reminder that Yin Hero skips pds fire.");
                                        int context = 0;
                                        File systemWithContext = GenerateTile.getInstance().saveImage(activeGame, context, tile.getPosition(), event);
                                        MessageHelper.sendMessageWithFile(threadChannel_, systemWithContext, "Picture of system", false);
                                        List<Button> buttons = ButtonHelper.getButtonsForPictureCombats(activeGame,  tile.getPosition(), player, player2, "ground");
                                        MessageHelper.sendMessageToChannelWithButtons(threadChannel_, "", buttons);
                                        
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
    public static void putSleeperOn(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident){
        buttonID = buttonID.replace("putSleeperOnPlanet_", "");
        String planet = buttonID;
        String message = ident+" put a sleeper on " + Helper.getPlanetRepresentation(planet, activeGame);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        new SleeperToken().addOrRemoveSleeper(event, activeGame, planet, player);
        event.getMessage().delete().queue();
    }
    public static void oribtalDropFollowUp(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident){
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
    public static void oribtalDropExhaust(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident){
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), event.getMessage().getContentRaw());
        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(activeGame, player, event);
        Button DoneExhausting = Button.danger("finishComponentAction", "Done Exhausting Planets");
        buttons.add(DoneExhausting);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                "Use Buttons to Pay For The Mech", buttons);
        event.getMessage().delete().queue();
    }
     public static void resolveLetnevCommanderCheck(Player player, Game activeGame, GenericInteractionCreateEvent event) {
        if(activeGame.playerHasLeaderUnlockedOrAlliance(player, "letnevcommander")){
            int old = player.getTg();
            int newTg = player.getTg()+1;
            player.setTg(player.getTg()+1);
            String mMessage = Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), true)+" Since you have Barony commander unlocked, 1tg has been added automatically ("+old+"->"+newTg+")";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), mMessage);
            pillageCheck(player, activeGame);
        }
     }
     public static List<Button> getEmpyHeroButtons(Player player, Game activeGame){
        String finChecker = "FFCC_"+player.getFaction() + "_";
        List<Button> empties = new ArrayList<>();
        for(Tile tile : activeGame.getTileMap().values()){
            if(tile.getUnitHolders().values().size() > 1 || !FoWHelper.playerHasShipsInSystem(player, tile)){
                continue;
            }
            empties.add(Button.primary(finChecker+"exploreFront_"+tile.getPosition(), "Explore "+tile.getRepresentationForButtons(activeGame, player)));
        }
        return empties;
    }
    public static boolean isCabalBlockadedByPlayer(Player player, Game activeGame, Player cabal){
        List<Tile> tiles = ButtonHelper.getTilesOfPlayersSpecificUnit(activeGame, cabal, "csd");
        if(tiles.isEmpty()){
            tiles = ButtonHelper.getTilesOfPlayersSpecificUnit(activeGame, cabal, "sd");
        }
        if(tiles.isEmpty()){
            return false;
        }
        for(Tile tile : tiles){
            if(FoWHelper.playerHasShipsInSystem(player, tile) && !FoWHelper.playerHasShipsInSystem(cabal, tile)){
                return true;
            }
        }
        return false;
    }

    public static void cabalEatsUnit(Player player, Game activeGame, Player cabal, int amount, String unit, GenericInteractionCreateEvent event){
        String msg = Helper.getPlayerRepresentation(cabal, activeGame, activeGame.getGuild(), true)+" has failed to eat "+amount+" of the "+unit +"s owned by " + Helper.getPlayerRepresentation(player, activeGame) + " because they were blockaded. Wah-wah.";
        if(!isCabalBlockadedByPlayer(player, activeGame, cabal)){
            msg = Helper.getFactionIconFromDiscord(cabal.getFaction())+" has devoured "+amount+" of the "+unit +"s owned by " + player.getColor() + ". Chomp chomp.";
            String color = player.getColor();
            String unitP = AliasHandler.resolveUnit(unit);
            if (unitP.contains("ff") || unitP.contains("gf")) {
                color = cabal.getColor();
            }
            msg = msg.replace("Infantrys","infantry");
            if (unitP.contains("sd") || unitP.contains("pd") || cabal.getAllianceMembers().contains(player.getFaction())) {
                return;
            }
            
            new AddUnits().unitParsing(event, color, cabal.getNomboxTile(), amount +" " +unit, activeGame);
        }
        if(activeGame.isFoWMode()){
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(cabal, activeGame), msg);
        }else{
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(cabal, activeGame), msg);
        }
        
    }
   
    public static void executeCabalHero(String buttonID, Player player, Game activeGame, ButtonInteractionEvent event){
        String pos = buttonID.replace("cabalHeroTile_","");
        Tile tile = activeGame.getTileByPosition(pos);
        UnitHolder space = tile.getUnitHolders().get("space");
        HashMap<String, Integer> units1 = space.getUnits();
        for(Player p2 : activeGame.getRealPlayers()){
            if(p2 == player){
                continue;
            }
            if(FoWHelper.playerHasShipsInSystem(p2, tile) && !isCabalBlockadedByPlayer(p2, activeGame, player)){
                ButtonHelper.riftAllUnitsInASystem(pos, event, activeGame, p2, Helper.getFactionIconFromDiscord(p2.getFaction()), player);
            }
            if(FoWHelper.playerHasShipsInSystem(p2, tile) && isCabalBlockadedByPlayer(p2, activeGame, player)){
                String msg = Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), true)+" has failed to eat units owned by " + Helper.getPlayerRepresentation(player, activeGame) + " because they were blockaded. Wah-wah.";
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg);
            }
        }

    }

    public static void resolveSolCommander(Player player, Game activeGame, String buttonID, ButtonInteractionEvent event){
        String planet = buttonID.split("_")[1];
        Tile tile = activeGame.getTileByPosition(activeGame.getActiveSystem());
        new AddUnits().unitParsing(event, player.getColor(), tile, "1 inf "+planet, activeGame);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), ButtonHelper.getIdent(player) + " placed 1 infantry on "+Helper.getPlanetRepresentation(planet, activeGame) + " using Sol Commander");
    }

    public static void resolveInitialIndoctrinationQuestion(Player player, Game activeGame, String buttonID, ButtonInteractionEvent event){
        String planet = buttonID.split("_")[1];
        List<Button> options = new ArrayList<>();
        options.add(Button.success("indoctrinate_"+planet+"_infantry", "Indoctrinate to place an infantry"));
        options.add(Button.success("indoctrinate_"+planet+"_mech", "Indoctrinate to place a mech"));
        options.add(Button.danger("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), ButtonHelper.getTrueIdentity(player, activeGame)+" use buttons to resolve indoctrination", options);
    }
    public static void resolveFollowUpIndoctrinationQuestion(Player player, Game activeGame, String buttonID, ButtonInteractionEvent event){
        String planet = buttonID.split("_")[1];
        String unit = buttonID.split("_")[2];
        Tile tile = Helper.getTileFromPlanet(planet, activeGame);
        new AddUnits().unitParsing(event, player.getColor(), tile, "1 "+unit+" "+planet, activeGame);
        for(Player p2 : activeGame.getRealPlayers()){
            if(p2 == player){
                continue;
            }
            if(FoWHelper.playerHasInfantryOnPlanet(p2, tile, planet)){
                new RemoveUnits().unitParsing(event, p2.getColor(), tile, "1 infantry "+planet, activeGame);
            }
        }
        List<Button> options =ButtonHelper.getExhaustButtonsWithTG(activeGame, player, event);
        if(player.getLeaderIDs().contains("yincommander") && !player.hasLeaderUnlocked("yincommander")){
            ButtonHelper.commanderUnlockCheck(player, activeGame, "yin", event);
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), ButtonHelper.getIdent(player) + " replaced 1 of their opponent's infantry with 1 "+unit+" on "+Helper.getPlanetRepresentation(planet, activeGame) + " using indoctrination");
        options.add(Button.danger("deleteButtons", "Done Exhausting Planets"));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), ButtonHelper.getTrueIdentity(player, activeGame)+" pay for indoctrination.", options);
        event.getMessage().delete().queue();
    }

    


    public static List<Button> getCabalHeroButtons(Player player, Game activeGame){
        String finChecker = "FFCC_"+player.getFaction() + "_";
        List<Button> empties = new ArrayList<>();
        List<Tile> tiles = ButtonHelper.getTilesOfPlayersSpecificUnit(activeGame, player, "csd");
        List<Tile> adjtiles = new ArrayList<>();
        for(Tile tile : tiles){
            for(String pos : FoWHelper.getAdjacentTiles(activeGame, tile.getPosition(), player, false)){
                Tile tileToAdd = activeGame.getTileByPosition(pos);
                if(!adjtiles.contains(tileToAdd) && !tile.getPosition().equalsIgnoreCase(pos)){
                    adjtiles.add(tileToAdd);
                }
            }
        }
        for(Tile tile : adjtiles){
            empties.add(Button.primary(finChecker+"cabalHeroTile_"+tile.getPosition(), "Roll for units in "+tile.getRepresentationForButtons(activeGame, player)));
        }
        return empties;
    }
    public static void pillageCheck(Player player, Game activeGame) {
        if(player.getPromissoryNotesInPlayArea().contains("pop")){
            return;
        }
        if(Helper.getPlayerFromAbility(activeGame, "pillage") != null && !Helper.getPlayerFromAbility(activeGame, "pillage").getFaction().equalsIgnoreCase(player.getFaction())){
             
            Player pillager = Helper.getPlayerFromAbility(activeGame, "pillage");
            String finChecker = "FFCC_"+pillager.getFaction() + "_";
            if(player.getTg() > 2 && Helper.getNeighbouringPlayers(activeGame, player).contains(pillager)){
                List<Button> buttons = new ArrayList<>();
                String playerIdent = StringUtils.capitalize(player.getFaction());
                MessageChannel channel = activeGame.getMainGameChannel();
                if(activeGame.isFoWMode()){
                    playerIdent = StringUtils.capitalize(player.getColor());
                    channel = pillager.getPrivateChannel();
                }
                String message = Helper.getPlayerRepresentation(pillager, activeGame, activeGame.getGuild(), true) + " you may have the opportunity to pillage "+playerIdent+". Please check this is a valid pillage opportunity, and use buttons to resolve.";
                buttons.add(Button.danger(finChecker+"pillage_"+player.getColor()+"_unchecked","Pillage "+playerIdent));
                buttons.add(Button.success(finChecker+"deleteButtons","Decline Pillage Window"));
                MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
            }
        }

    }
    public static void resolveDarkPactCheck(Game activeGame, Player sender, Player receiver, int numOfComms, GenericInteractionCreateEvent event) {
        for(String pn : sender.getPromissoryNotesInPlayArea()){
            if("dark_pact".equalsIgnoreCase(pn) && activeGame.getPNOwner(pn).getFaction().equalsIgnoreCase(receiver.getFaction())){
                if(numOfComms == sender.getCommoditiesTotal()){
                    MessageChannel channel = activeGame.getActionsChannel();
                    if(activeGame.isFoWMode()){
                        channel = sender.getPrivateChannel();
                    }
                    String message =  Helper.getPlayerRepresentation(sender, activeGame, activeGame.getGuild(), true)+" Dark Pact triggered, your tgs have increased by 1 ("+sender.getTg()+"->"+(sender.getTg()+1)+")";
                    sender.setTg(sender.getTg()+1);
                    MessageHelper.sendMessageToChannel(channel, message);
                    message =  Helper.getPlayerRepresentation(receiver, activeGame, activeGame.getGuild(), true)+" Dark Pact triggered, your tgs have increased by 1 ("+receiver.getTg()+"->"+(receiver.getTg()+1)+")";
                    receiver.setTg(receiver.getTg()+1);
                    if(activeGame.isFoWMode()){
                        channel = receiver.getPrivateChannel();
                    }
                    MessageHelper.sendMessageToChannel(channel, message);
                    pillageCheck(sender, activeGame);
                    pillageCheck(receiver, activeGame);
                }
            }
        }
    }
    public static void resolveMuaatCommanderCheck(Player player, Game activeGame, GenericInteractionCreateEvent event) {
        if (activeGame.playerHasLeaderUnlockedOrAlliance(player, "muaatcommander")) {
            int old = player.getTg();
            int newTg = player.getTg()+1;
            player.setTg(player.getTg()+1);
            String mMessage = Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), true)+" Since you have Muaat commander unlocked, 1tg has been added automatically ("+old+"->"+newTg+")";
            if(activeGame.isFoWMode())
            {
                MessageHelper.sendMessageToChannel(player.getPrivateChannel(),mMessage);
            }
            else{
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), mMessage);
            }
            pillageCheck(player, activeGame);
        }
    }

    public static List<Button> getTilesToArboAgent(Player player, Game activeGame, GenericInteractionCreateEvent event) {
        String finChecker = "FFCC_"+player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, Tile> tileEntry : new HashMap<>(activeGame.getTileMap()).entrySet()) {
			if (FoWHelper.playerHasShipsInSystem(player, tileEntry.getValue())) {
                Tile tile = tileEntry.getValue();
                Button validTile = Button.success(finChecker+"arboAgentIn_"+tileEntry.getKey(), tile.getRepresentationForButtons(activeGame, player));
                buttons.add(validTile);
			}
		}
        Button validTile2 = Button.danger(finChecker+"deleteButtons", "Decline");
        buttons.add(validTile2);
        return buttons;
    }

    public static void cabalAgentInitiation(Game activeGame, Player p2){
        for (Player cabal : activeGame.getRealPlayers()) {
            if (cabal == p2){
                continue;
            }
            if (cabal.hasUnexhaustedLeader("cabalagent", activeGame)) {
                List<Button> buttons = new ArrayList<>();
                String msg = ButtonHelper.getTrueIdentity(cabal, activeGame) + " you have the ability to use cabal agent on "+ButtonHelper.getIdentOrColor(p2, activeGame)+" who has "+p2.getCommoditiesTotal()+" commodities";
                buttons.add(Button.success("startCabalAgent_"+p2.getFaction(), "Use Agent"));
                buttons.add(Button.danger("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(cabal.getCardsInfoThread(activeGame), msg, buttons);
            }
        }    
    }

    public static void startCabalAgent(Player cabal, Game activeGame, String buttonID, ButtonInteractionEvent event){
        String faction = buttonID.split("_")[1];
        Player p2 = Helper.getPlayerFromColorOrFaction(activeGame, faction);
        List<Button> buttons = getUnitsForCabalAgent(cabal, activeGame, event, p2);
        String msg = ButtonHelper.getTrueIdentity(cabal, activeGame) + " use buttons to capture a ship";
        MessageHelper.sendMessageToChannelWithButtons(cabal.getCardsInfoThread(activeGame), msg, buttons);
        event.getMessage().delete().queue();
    }
    public static List<Button> getUnitsForCabalAgent(Player player, Game activeGame, GenericInteractionCreateEvent event, Player p2) {
        List<Button> buttons = new ArrayList<>();
        int maxComms = p2.getCommoditiesTotal();
        String unit2;
        Button unitButton2;

        unit2 = "destroyer";
        if(maxComms> 0 && ButtonHelper.getNumberOfUnitsOnTheBoard(activeGame, p2, unit2) < 8){
            unitButton2 = Button.danger("cabalAgentCapture_"+unit2+"_"+p2.getFaction(), "Capture "+unit2).withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord(unit2)));
            buttons.add(unitButton2);
        }

        unit2 = "cruiser";
        if(maxComms> 1 && ButtonHelper.getNumberOfUnitsOnTheBoard(activeGame, p2, unit2) < 8){
            unitButton2 = Button.danger("cabalAgentCapture_"+unit2+"_"+p2.getFaction(), "Capture "+unit2).withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord(unit2)));
            buttons.add(unitButton2);
        }
        unit2 = "carrier";
        if(maxComms> 2 && ButtonHelper.getNumberOfUnitsOnTheBoard(activeGame, p2, unit2) < 4){
            
            unitButton2 = Button.danger("cabalAgentCapture_"+unit2+"_"+p2.getFaction(), "Capture "+unit2).withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord(unit2)));
            buttons.add(unitButton2);
        }
        unit2 = "dreadnought";
        if(maxComms> 3&& ButtonHelper.getNumberOfUnitsOnTheBoard(activeGame, p2, unit2) < 5){
           
            unitButton2 = Button.danger("cabalAgentCapture_"+unit2+"_"+p2.getFaction(), "Capture "+unit2).withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord(unit2)));
            buttons.add(unitButton2);
        }
        unit2 = "flagship";
        if(maxComms> 7 && ButtonHelper.getNumberOfUnitsOnTheBoard(activeGame, p2, unit2) < 1){
            
            unitButton2 = Button.danger("cabalAgentCapture_"+unit2+"_"+p2.getFaction(), "Capture "+unit2).withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord(unit2)));
            buttons.add(unitButton2);
        }

        return buttons;

    }
    public static void resolveCabalAgentCapture(String buttonID, Player player, Game activeGame, ButtonInteractionEvent event){
        String unit = buttonID.split("_")[1];
        String faction = buttonID.split("_")[2];
        Player p2 = Helper.getPlayerFromColorOrFaction(activeGame, faction);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame), ButtonHelper.getTrueIdentity(p2, activeGame)+" a "+unit+" of yours has been captured by a cabal agent. Any comms you had have been washed.");
        p2.setTg(p2.getTg()+p2.getCommodities());
        p2.setCommodities(0);
        cabalEatsUnit(p2, activeGame, player, 1, unit, event);
        event.getMessage().delete().queue();
    }

    public static List<Button> getUnitsToArboAgent(Player player, Game activeGame, GenericInteractionCreateEvent event, Tile tile) {
        String finChecker = "FFCC_"+player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        Map<String, String> unitRepresentation = Mapper.getUnitImageSuffixes();
        Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
        String cID = Mapper.getColorID(player.getColor());
        for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
            String name = entry.getKey();
            UnitHolder unitHolder = entry.getValue();
            HashMap<String, Integer> units = unitHolder.getUnits();
            if (unitHolder instanceof Planet planet) {
            }
            else{
                Map<String, Integer> tileUnits = new HashMap<>(units);
                for (Map.Entry<String, Integer> unitEntry : tileUnits.entrySet()) {
                    String key = unitEntry.getKey();
                    if (key.endsWith("gf.png") || key.endsWith("mf.png") ||  key.endsWith("ff.png")) {
                        continue;
                    }
                    for (String unitRepresentationKey : unitRepresentation.keySet()) {
                        if (key.endsWith(unitRepresentationKey) && key.contains(cID)) {
                            
                            String unitKey = key.replace(cID+"_", "");
                            int totalUnits = unitEntry.getValue();
                            unitKey  = unitKey.replace(".png", "");
                            unitKey = ButtonHelper.getUnitName(unitKey);
                            int damagedUnits = 0;
                            if(unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(key) != null){
                                damagedUnits = unitHolder.getUnitDamage().get(key);
                            }
                            for(int x = 1; x < damagedUnits +1; x++){
                                if(x > 1){
                                    break;
                                }
                                Button validTile2 = Button.danger(finChecker+"arboAgentOn_"+tile.getPosition()+"_"+unitKey+"damaged", "Remove A Damaged "+unitRepresentation.get(unitRepresentationKey)).withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord(unitRepresentation.get(unitRepresentationKey).toLowerCase().replace(" ", ""))));
                                buttons.add(validTile2);
                            }
                            totalUnits = totalUnits-damagedUnits;
                            for(int x = 1; x < totalUnits +1; x++){
                                if(x > 1){
                                    break;
                                }
                                Button validTile2 = Button.danger(finChecker+"arboAgentOn_"+tile.getPosition()+"_"+unitKey, "Remove "+x+" "+unitRepresentation.get(unitRepresentationKey)).withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord(unitRepresentation.get(unitRepresentationKey).toLowerCase().replace(" ", ""))));
                                buttons.add(validTile2);
                            }
                        }
                    }
                }
            }
        }
        Button validTile2 = Button.danger(finChecker+"deleteButtons", "Decline");
        buttons.add(validTile2);
        return buttons;
    }

    public static List<Button> getArboAgentReplacementOptions(Player player, Game activeGame, GenericInteractionCreateEvent event, Tile tile, String unit) {
        String finChecker = "FFCC_"+player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        String unitName = ButtonHelper.getUnitName(unit);
        boolean damaged = false;
        if(unit.contains("damaged")){
                unit = unit.replace("damaged", "");
                damaged = true;
        }
        String key = Mapper.getUnitID(AliasHandler.resolveUnit(unit), player.getColor());
        new RemoveUnits().removeStuff(event,tile, 1, "space", key, player.getColor(),damaged);
        String msg = Helper.getEmojiFromDiscord(unit.toLowerCase()) +" was removed via Arborec agent by "+ButtonHelper.getIdent(player);
        if(damaged){
            msg = "A damaged " + msg;
        }
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg);
        String unit2;
        Button unitButton2;
        unit2 = "destroyer";
        unitButton2 = Button.danger(finChecker+"arboAgentPutShip_"+unit2+"_"+tile.getPosition(), "Place "+unit2).withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord(unit2)));
        buttons.add(unitButton2);
        unit2 = "cruiser";
        unitButton2 = Button.danger(finChecker+"arboAgentPutShip_"+unit2+"_"+tile.getPosition(), "Place "+unit2).withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord(unit2)));
        buttons.add(unitButton2);
        unit2 = "carrier";
        unitButton2 = Button.danger(finChecker+"arboAgentPutShip_"+unit2+"_"+tile.getPosition(), "Place "+unit2).withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord(unit2)));
        buttons.add(unitButton2);
        
        if(!"destroyer".equals(unit)){
            unit2 = "dreadnought";
            unitButton2 = Button.danger(finChecker+"arboAgentPutShip_"+unit2+"_"+tile.getPosition(), "Place "+unit2).withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord(unit2)));
            buttons.add(unitButton2);
        }

        return buttons;

    }
    


    public static void offerTerraformButtons(Player player, Game activeGame, GenericInteractionCreateEvent event) {
        List<Button> buttons = new ArrayList<>();
        for(String planet : player.getPlanets()){
            UnitHolder unitHolder = activeGame.getPlanetsInfo().get(planet);
            Planet planetReal = (Planet) unitHolder;
            boolean oneOfThree = planetReal != null && planetReal.getOriginalPlanetType() != null && ("industrial".equalsIgnoreCase(planetReal.getOriginalPlanetType()) || "cultural".equalsIgnoreCase(planetReal.getOriginalPlanetType()) || "hazardous".equalsIgnoreCase(planetReal.getOriginalPlanetType()));
            if ( oneOfThree || planet.contains("custodiavigilia")) {
                buttons.add(Button.success("terraformPlanet_"+planet, Helper.getPlanetRepresentation(planet, activeGame)));
            }
        }
        String message = "Use buttons to select which planet to terraform";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
    }
    public static List<Button> getButtonsToTakeSomeonesAC(Game activeGame, Player thief, Player victim)
    {
        List<Button> takeACs = new ArrayList<>();
        String secretScoreMsg = "_ _\nClick a button to take an Action Card";
        List<Button> acButtons = ACInfo.getToBeStolenActionCardButtons(activeGame, victim);
        if (!acButtons.isEmpty()) {
            List<MessageCreateData> messageList = MessageHelper.getMessageCreateDataObjects(secretScoreMsg, acButtons);
            ThreadChannel cardsInfoThreadChannel = thief.getCardsInfoThread(activeGame);
            for (MessageCreateData message : messageList) {
                cardsInfoThreadChannel.sendMessage(message).queue();
            }
        }
        return takeACs;
    }
    public static void umbatTile(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident){
        String pos = buttonID.replace("umbatTile_", "");
        List<Button> buttons;
        buttons = Helper.getPlaceUnitButtons(event, player, activeGame,
                activeGame.getTileByPosition(pos), "muaatagent", "place");
        String message = Helper.getPlayerRepresentation(player, activeGame) + " Use the buttons to produce units. ";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        event.getMessage().delete().queue();
    }
    public static void hacanAgentRefresh(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident, String trueIdentity){
        String faction = buttonID.replace("hacanAgentRefresh_", "");
        Player p2 = Helper.getPlayerFromColorOrFaction(activeGame, faction);
        String message;
        if(p2 == player){
            p2.setCommodities(p2.getCommodities()+2);
            message = trueIdentity+"Increased your commodities by two";
        }else{
            p2.setCommodities(p2.getCommoditiesTotal());
            ButtonHelper.resolveMinisterOfCommerceCheck(activeGame, p2, event);
            cabalAgentInitiation(activeGame, p2);
            message = "Refreshed " +p2.getColor()+ "'s commodities";
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        event.getMessage().delete().queue();
    }

    

    public static void distantSuns(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident){
        String bID = buttonID.replace("distant_suns_", "");
        String[] info = bID.split("_");
        String message;
        if ("decline".equalsIgnoreCase(info[0])) {
            message = "Rejected Distant Suns Ability";
            new ExpPlanet().explorePlanet(event, Helper.getTileFromPlanet(info[1], activeGame), info[1], info[2],
                    player, true, activeGame, 1, false);
        } else {
            message = "Exploring twice";
            new ExpPlanet().explorePlanet(event, Helper.getTileFromPlanet(info[1], activeGame), info[1], info[2],
                    player, true, activeGame, 2, false);
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), message);
        event.getMessage().delete().queue();
    }
    public static void mageon(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident, String trueIdentity){
        buttonID = buttonID.replace("takeAC_", "");
        int acNum = Integer.parseInt(buttonID.split("_")[0]);

        String faction2 = buttonID.split("_")[1];
        Player player2 = Helper.getPlayerFromColorOrFaction(activeGame, faction2);
        if(!player2.getActionCards().containsValue(acNum)){
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find that AC, no AC added/lost");
            return;
       }
        String ident2 = Helper.getPlayerRepresentation(player2, activeGame, activeGame.getGuild(), false);
        String message2 = trueIdentity + " took AC #" + acNum + " from " + ident2;
        String acID = null;
        for (Map.Entry<String, Integer> so : player2.getActionCards().entrySet()) {
            if (so.getValue().equals(acNum)) {
                acID = so.getKey();
            }
        }
        if (activeGame.isFoWMode()) {
            message2 = "Someone took AC #" + acNum + " from " + player2.getColor();
            MessageHelper.sendMessageToChannel(player.getPrivateChannel(), message2);
            MessageHelper.sendMessageToChannel(player2.getPrivateChannel(), message2);
        } else {
            MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), message2);
        }
        player2.removeActionCard(acNum);
        player.setActionCard(acID);
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(activeGame), Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), true) + "Acquired " + acID);
        MessageHelper.sendMessageToChannel(player2.getCardsInfoThread(activeGame), "# "+ Helper.getPlayerRepresentation(player2, activeGame, activeGame.getGuild(), true)+" Lost " + acID +" to mageon (or perhaps Yssaril hero)");
        ACInfo.sendActionCardInfo(activeGame, player2);
        ACInfo.sendActionCardInfo(activeGame, player);
        if(player.getLeaderIDs().contains("yssarilcommander") && !player.hasLeaderUnlocked("yssarilcommander")){
            ButtonHelper.commanderUnlockCheck(player, activeGame, "yssaril", event);
        }
        event.getMessage().delete().queue();
    }
    public static void pillage(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident, String finsFactionCheckerPrefix){
        buttonID = buttonID.replace("pillage_", "");
        String colorPlayer = buttonID.split("_")[0];
        String checkedStatus = buttonID.split("_")[1];
        Player pillaged = Helper.getPlayerFromColorOrFaction(activeGame, colorPlayer);
        if(checkedStatus.contains("unchecked")){
                List<Button> buttons = new ArrayList<>();
            String message2 =  "Please confirm this is a valid pillage opportunity and that you wish to pillage.";
            buttons.add(Button.danger(finsFactionCheckerPrefix+"pillage_"+pillaged.getColor()+"_checked","Pillage a TG"));
            if(pillaged.getCommodities()>0){
                buttons.add(Button.danger(finsFactionCheckerPrefix+"pillage_"+pillaged.getColor()+"_checkedcomm","Pillage a Commodity"));
            }
            buttons.add(Button.success(finsFactionCheckerPrefix+"deleteButtons","Delete these buttons"));
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
        }else{
            MessageChannel channel1 = ButtonHelper.getCorrectChannel(pillaged, activeGame);
            MessageChannel channel2 = ButtonHelper.getCorrectChannel(player, activeGame);
            String pillagerMessage = Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), true) + " you pillaged, your tgs have gone from "+player.getTg() +" to "+(player.getTg()+1) +".";
            String pillagedMessage = Helper.getPlayerRepresentation(pillaged, activeGame, activeGame.getGuild(), true) + " you have been pillaged";

            if (pillaged.getCommodities()>0 && checkedStatus.contains("checkedcomm")){
                pillagedMessage = pillagedMessage+ ", your comms have gone from "+pillaged.getCommodities() +" to "+(pillaged.getCommodities()-1) +".";
                pillaged.setCommodities(pillaged.getCommodities()-1);
            } else {
                pillagedMessage = pillagedMessage+ ", your tgs have gone from "+pillaged.getTg() +" to "+(pillaged.getTg()-1) +".";
                pillaged.setTg(pillaged.getTg()-1);
            }
            player.setTg(player.getTg()+1);
            MessageHelper.sendMessageToChannel(channel2, pillagerMessage);
            MessageHelper.sendMessageToChannel(channel1, pillagedMessage);
            if (player.hasUnexhaustedLeader("mentakagent", activeGame)) {
                List<Button> buttons = new ArrayList<>();
                Button winnuButton = Button.success("exhaustAgent_mentakagent_"+pillaged.getFaction(), "Use Mentak Agent To Draw ACs for you and pillaged player").withEmoji(Emoji.fromFormatted(Helper.getFactionIconFromDiscord("mentak")));
                buttons.add(winnuButton);
                buttons.add(Button.danger("deleteButtons", "Done"));
                MessageHelper.sendMessageToChannelWithButtons(channel2, "Wanna use Mentak Agent?", buttons);
            }
        }
        event.getMessage().delete().queue();
    }
    public static void terraformPlanet(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident){
        String planet = buttonID.replace("terraformPlanet_","");
        UnitHolder unitHolder = activeGame.getPlanetsInfo().get(planet);
        Planet planetReal = (Planet) unitHolder;
        planetReal.addToken(Constants.ATTACHMENT_TITANSPN_PNG);
        MessageHelper.sendMessageToChannel(event.getChannel(), "Attached terraform to "+Helper.getPlanetRepresentation(planet, activeGame));
        event.getMessage().delete().queue();
    }
    public static List<Button> getMitosisOptions(Game activeGame, Player player){
        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.success("mitosisInf", "Place an Infantry"));
        if(ButtonHelper.getNumberOfUnitsOnTheBoard(activeGame, player, "mech") < 4){
            buttons.add(Button.primary("mitosisMech", "Replace an Infantry With a Mech (DEPLOY)"));
        }
        return buttons;
    }
    public static List<Button> getCreusIFFTypeOptions(Game activeGame, Player player){
        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.success("creussIFFStart_beta", "Beta").withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("CreussBeta"))));
        buttons.add(Button.danger("creussIFFStart_gamma", "Gamma").withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("CreussGamma"))));
         buttons.add(Button.secondary("creussIFFStart_alpha", "Alpha").withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("CreussAlpha"))));
        return buttons;
    }
    public static void resolveCreussIFFStart(Game activeGame, Player player, String buttonID, String ident, ButtonInteractionEvent event){
        String type = buttonID.split("_")[1];
         List<Button> buttons = getCreusIFFLocationOptions(activeGame, player, type);
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), ident+ " please select the tile you would like to put a wormhole in", buttons);
        event.getMessage().delete().queue();
    }
    public static void resolveCreussIFF(Game activeGame, Player player, String buttonID, String ident, ButtonInteractionEvent event){
        String type = buttonID.split("_")[1];
        String tokenName = "creuss"+type;
        String pos = buttonID.split("_")[2];
        Tile tile = activeGame.getTileByPosition(pos);
        String msg;
        if(activeGame.isFoWMode() && !isTileCreussIFFSuitable(activeGame, player, tile)){
            msg = "Tile was not suitable for the iff.";
            if(player.getTg() > 0){
                player.setTg(player.getTg() -1);
                msg = msg + " You lost a tg";
            }else{
                if(player.getTacticalCC() > 0){
                    player.setTacticalCC(player.getTacticalCC() -1);
                    msg = msg + " You lost a tactic cc";
                }else{
                    if(player.getFleetCC() > 0){
                        player.setFleetCC(player.getFleetCC() -1);
                        msg = msg + " You lost a fleet cc";
                    }
                }
            }
        }else{
            StringBuilder sb = new StringBuilder(Helper.getPlayerRepresentation(player, activeGame));
            tile.addToken(Mapper.getTokenID(tokenName), Constants.SPACE);
            sb.append(" moved ").append(Helper.getEmojiFromDiscord(tokenName)).append(" to ").append(tile.getRepresentationForButtons(activeGame, player));
            for (Tile tile_ : activeGame.getTileMap().values()) {
                if (!tile.equals(tile_) && tile_.removeToken(Mapper.getTokenID(tokenName), Constants.SPACE)) {
                    sb.append(" (from ").append(tile_.getRepresentationForButtons(activeGame, player)).append(")");
                    break;
                }
            }
            msg = sb.toString();
        }
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg);
        event.getMessage().delete().queue();
    }
    public static List<Button> getCreusIFFLocationOptions(Game activeGame, Player player, String type){
        List<Button> buttons = new ArrayList<>();
        for(Tile tile : activeGame.getTileMap().values()){
            if(isTileCreussIFFSuitable(activeGame, player, tile) || (activeGame.isFoWMode() && !FoWHelper.getTilePositionsToShow(activeGame, player).contains(tile.getPosition()))){
                buttons.add(Button.success("creussIFFResolve_"+type+"_"+tile.getPosition(), tile.getRepresentationForButtons(activeGame, player)));
            }
        }
        return buttons;
    }
    
    public static boolean isTileCreussIFFSuitable(Game activeGame, Player player, Tile tile){
        for(String planet : player.getPlanets(activeGame)){
            if(planet.toLowerCase().contains("custodia")){
                continue;
            }
            if(Helper.getTileFromPlanet(planet, activeGame) == null){
                continue;
            }
            if(Helper.getTileFromPlanet(planet, activeGame).getPosition().equalsIgnoreCase(tile.getPosition())){
                return true;
            }
        }
        for(Player p2 : activeGame.getRealPlayers()){
            if(p2 == player){
                continue;
            }
            if(FoWHelper.playerHasShipsInSystem(p2, tile)){
                return false;
            }
            Tile hs = activeGame.getTile(AliasHandler.resolveTile(p2.getFaction()));
            if(hs== null)
            {
                hs = ButtonHelper.getTileOfPlanetWithNoTrait(p2, activeGame);
            }
            if(hs != null && hs.getPosition().equalsIgnoreCase(tile.getPosition())){
                return false;
            }
        }
        return true;
    }
    public static void resolveMitosisInf(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident){
        List<Button> buttons = new ArrayList<>(Helper.getPlanetPlaceUnitButtons(player, activeGame, "infantry", "placeOneNDone_skipbuild"));
        String message = ButtonHelper.getTrueIdentity(player, activeGame)+" Use buttons to put 1 infantry on a planet";
        
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), ident + " is resolving mitosis");
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), message, buttons);
        event.getMessage().delete().queue();
    }
    public static void resolveMitosisMech(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident, String finChecker){
        List<Button> buttons = new ArrayList<>(getPlanetPlaceUnitButtonsForMechMitosis(player, activeGame, finChecker));
        String message = ButtonHelper.getTrueIdentity(player, activeGame)+" Use buttons to replace 1 infantry with a mech";
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), ident + " is resolving mitosis");
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), message, buttons);
        event.getMessage().delete().queue();
    }
    public static boolean doesAnyoneHaveThisLeader(String leaderID, Game activeGame){
        boolean someoneHasIt = false;
        for(Player player : activeGame.getRealPlayers()){
            if(player.hasLeader(leaderID)){
                someoneHasIt = true;
            }
        }
        return someoneHasIt;
    }
    public static List<Button> getPlanetPlaceUnitButtonsForMechMitosis(Player player, Game activeGame, String finChecker) {
        List<Button> planetButtons = new ArrayList<>();
        List<String> planets = new ArrayList<>(player.getPlanets(activeGame));
        List<String> tiles = new ArrayList<>();
        for (String planet : planets) {
            Tile tile =  activeGame.getTile(AliasHandler.resolveTile(planet));
            if(tiles.contains(tile.getPosition())){
                continue;
            }else{
                tiles.add(tile.getPosition());
            }
            for(UnitHolder unitHolder :tile.getUnitHolders().values()){
                if("space".equalsIgnoreCase(unitHolder.getName())){
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
                    Button button = Button.success(finChecker+"mitoMechPlacement_"+unitHolder.getName().toLowerCase().replace("'","").replace("-","").replace(" ",""), "Place mech on " + Helper.getPlanetRepresentation(unitHolder.getName(), activeGame));
                    planetButtons.add(button);
                }
            }
        }
        return planetButtons;
    }
    public static void resolveMitosisMechPlacement(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident){
        String planetName = buttonID.replace("mitoMechPlacement_","");
        new AddUnits().unitParsing(event, player.getColor(),
                        activeGame.getTile(AliasHandler.resolveTile(planetName)), "mech "+planetName, activeGame);
        String key = Mapper.getUnitID(AliasHandler.resolveUnit("infantry"), player.getColor());
        //activeMap.getTileByPosition(pos1).removeUnit(planet,key, amount);
        new RemoveUnits().removeStuff(event, activeGame.getTile(AliasHandler.resolveTile(planetName)), 1, planetName, key, player.getColor(), false);
        String successMessage = ident +" Replaced an infantry with a mech on "
        + Helper.getPlanetRepresentation(planetName, activeGame) + ".";
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), successMessage);
        event.getMessage().delete().queue();
    }

    public static void resolveMercerMove(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident){
        String planetDestination = buttonID.split("_")[1];
        String planetRemoval = buttonID.split("_")[2];
        String unit = buttonID.split("_")[3];
        new RemoveUnits().unitParsing(event, player.getColor(), Helper.getTileFromPlanet(planetRemoval, activeGame), unit+" "+planetRemoval, activeGame);
        new AddUnits().unitParsing(event, player.getColor(), Helper.getTileFromPlanet(planetDestination, activeGame), unit+" "+planetDestination, activeGame);
        MessageHelper.sendMessageToChannel(event.getChannel(), ident + " moved 1 "+unit + " from "+Helper.getPlanetRepresentation(planetRemoval, activeGame)+" to "+Helper.getPlanetRepresentation(planetDestination, activeGame));
    }

    public static void addArgentAgentButtons(Tile tile, Player player, Game activeGame){
        Set<String> tiles = FoWHelper.getAdjacentTiles(activeGame, tile.getPosition(), player, false);
        List<Button> unitButtons = new ArrayList<>();
        for(String pos : tiles){
            Tile tile2 = activeGame.getTileByPosition(pos);
           
            for(UnitHolder unitHolder : tile2.getUnitHolders().values()){
                if("space".equalsIgnoreCase(unitHolder.getName())){
                    continue;
                }
                Planet planetReal =  (Planet) unitHolder;
                String planet = planetReal.getName();    
                if (player.getPlanets(activeGame).contains(planet)) {
                    String pp = unitHolder.getName();
                    Button inf1Button = Button.success("FFCC_"+player.getFaction()+"_place_infantry_"+pp, "Produce 1 Infantry on "+Helper.getPlanetRepresentation(pp, activeGame));
                    inf1Button = inf1Button.withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("infantry")));
                    unitButtons.add(inf1Button);
                    Button mfButton = Button.success("FFCC_"+player.getFaction()+"_place_mech_"+pp, "Produce Mech on "+Helper.getPlanetRepresentation(pp, activeGame) );
                    mfButton = mfButton.withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("mech")));
                    unitButtons.add(mfButton);
                }
            }
        }
        unitButtons.add(Button.danger("deleteButtons_spitItOut", "Done Using Argent Agent"));
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getTrueIdentity(player, activeGame) + " use buttons to place ground forces via argent agent", unitButtons);
    }
    

    public static void exhaustAgent(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident){
        String agent = buttonID.replace("exhaustAgent_","");
        String rest = agent;
        String trueIdentity = ButtonHelper.getTrueIdentity(player, activeGame);
        if (agent.contains("_")) {
            agent = agent.substring(0, agent.indexOf("_"));
        }

        Leader playerLeader = player.getLeader(agent).orElse(null);
        if (playerLeader == null) {
            return;
        }

        MessageChannel channel2 = activeGame.getMainGameChannel();
        if (activeGame.isFoWMode()) {
            channel2 = player.getPrivateChannel();
        }
        playerLeader.setExhausted(true);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),Helper.getFactionLeaderEmoji(playerLeader));
        String messageText = Helper.getPlayerRepresentation(player, activeGame) +
            " exhausted " + Helper.getLeaderFullRepresentation(playerLeader);
        MessageHelper.sendMessageToChannel(channel2, messageText);
        if ("naazagent".equalsIgnoreCase(agent)) {
            List<Button> buttons = ButtonHelper.getButtonsToExploreAllPlanets(player, activeGame);
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),"Use buttons to explore", buttons);
        }

        if ("empyreanagent".equalsIgnoreCase(agent)) {
            Button getTactic = Button.success("increase_tactic_cc", "Gain 1 Tactic CC");
            Button getFleet = Button.success("increase_fleet_cc", "Gain 1 Fleet CC");
            Button getStrat = Button.success("increase_strategy_cc", "Gain 1 Strategy CC");
            Button DoneGainingCC = Button.danger("deleteButtons", "Done Gaining CCs");
            List<Button> buttons = List.of(getTactic, getFleet, getStrat, DoneGainingCC);
            String message2 = trueIdentity + "! Your current CCs are "+Helper.getPlayerCCs(player)+". Use buttons to gain CCs";
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
        }

        //TODO: Allow choosing someone else for this agent
        if ("nekroagent".equalsIgnoreCase(agent)) {
            player.setTg(player.getTg()+2);
            pillageCheck(player, activeGame);
            String message = trueIdentity+" increased your tgs by 2 ("+(player.getTg()-2)+"->"+player.getTg()+"). Use buttons in your cards info thread to discard an AC";
            MessageHelper.sendMessageToChannel(channel2, message);
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(activeGame), trueIdentity+" use buttons to discard", ACInfo.getDiscardActionCardButtons(activeGame, player, false));
        }

        if ("hacanagent".equalsIgnoreCase(agent)) {
            String message = trueIdentity+" select faction you wish to use your agent on";
            List<Button> buttons = AgendaHelper.getPlayerOutcomeButtons(activeGame, null, "hacanAgentRefresh", null);
            MessageHelper.sendMessageToChannelWithButtons(channel2, message, buttons);
        }

        if ("xxchaagent".equalsIgnoreCase(agent)) {
            String faction = rest.replace("xxchaagent_","");
            Player p2 = Helper.getPlayerFromColorOrFaction(activeGame, faction);
            String message = "Use buttons to ready a planet. Removing the infantry is not automated but is an option for you to do.";
            List<Button> ringButtons = ButtonHelper.getXxchaAgentReadyButtons(activeGame, p2);
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), true)+ message, ringButtons);
        }

        if ("yinagent".equalsIgnoreCase(agent)) {
            String posNFaction = rest.replace("yinagent_","");
            String pos = posNFaction.split("_")[0];
            String faction = posNFaction.split("_")[1];
            Player p2 = Helper.getPlayerFromColorOrFaction(activeGame, faction);
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),Helper.getPlayerRepresentation(p2, activeGame, activeGame.getGuild(), true)+" Use buttons to resolve yin agent", getYinAgentButtons(p2, activeGame, pos));
        }

        if ("naaluagent".equalsIgnoreCase(agent)) {
            String faction = rest.replace("naaluagent_","");
            Player p2 = Helper.getPlayerFromColorOrFaction(activeGame, faction);
            activeGame.setNaaluAgent(true);
            MessageChannel channel = event.getMessageChannel();
            if (activeGame.isFoWMode()) {
                channel = p2.getPrivateChannel();
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Sent buttons to the selected player");
            }
            String message = "Doing a tactical action. Please select the ring of the map that the system you want to activate is located in. Reminder that a normal 6 player map is 3 rings, with ring 1 being adjacent to Rex. Mallice is in the corner";
            List<Button> ringButtons = ButtonHelper.getPossibleRings(p2, activeGame);
            activeGame.resetCurrentMovedUnitsFrom1TacticalAction();
            MessageHelper.sendMessageToChannelWithButtons(channel,Helper.getPlayerRepresentation(p2, activeGame, activeGame.getGuild(), true)+" Use buttons to resolve tactical action from Naalu agent. Reminder it is not legal to do a tactical action in a home system.\n" + message, ringButtons);
        }

        if ("mentakagent".equalsIgnoreCase(agent)) {
            String faction = rest.replace("mentakagent_","");
            Player p2 = Helper.getPlayerFromColorOrFaction(activeGame, faction);
            String successMessage = ident+ " drew an AC.";
            String successMessage2 = ButtonHelper.getIdent(p2)+ " drew an AC.";
            activeGame.drawActionCard(player.getUserID());
            activeGame.drawActionCard(p2.getUserID());
            if (player.hasAbility("scheming")) {
                activeGame.drawActionCard(player.getUserID());
                successMessage += " Drew another AC for scheming. Please discard 1";
            }
            if (p2.hasAbility("scheming")) {
                activeGame.drawActionCard(p2.getUserID());
                successMessage2 += " Drew another AC for scheming. Please discard 1";
            }
            ButtonHelper.checkACLimit(activeGame, event, player);
            ButtonHelper.checkACLimit(activeGame, event, p2);
            String headerText = Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), true) + " you got an AC from Mentak Agent";
            MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, headerText);
            ACInfo.sendActionCardInfo(activeGame, player);
            String headerText2 = Helper.getPlayerRepresentation(p2, activeGame, activeGame.getGuild(), true) + " you got an AC from Mentak Agent";
            MessageHelper.sendMessageToPlayerCardsInfoThread(p2, activeGame, headerText2);
            ACInfo.sendActionCardInfo(activeGame, p2);
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), successMessage);
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame), successMessage2);
        }

        if ("sardakkagent".equalsIgnoreCase(agent)) {
            String posNPlanet = rest.replace("sardakkagent_","");
            String pos = posNPlanet.split("_")[0];
            String planetName = posNPlanet.split("_")[1];
            new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileByPosition(pos), "2 gf " + planetName, activeGame);
            String successMessage = ident + " placed 2 " + Helper.getEmojiFromDiscord("infantry") + " on " + Helper.getPlanetRepresentation(planetName, activeGame) + ".";
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), successMessage);
        }
        if ("argentagent".equalsIgnoreCase(agent)) {
            String pos = rest.replace("argentagent_","");
            Tile tile = activeGame.getTileByPosition(pos);
            addArgentAgentButtons(tile, player, activeGame);
        }
        if ("nomadagentmercer".equalsIgnoreCase(agent)) {
            String posNPlanet = rest.replace("nomadagentmercer_","");
            String pos = posNPlanet.split("_")[0];
            String planetName = posNPlanet.split("_")[1];
            List<Button> buttons = new ArrayList<>();
            for(String planet : player.getPlanets()){
                if (planet.equals(planetName) || planet.toLowerCase().contains("custodiavigilia")){
                    continue;
                }
                if(ButtonHelper.getNumberOfInfantryOnPlanet(planet, activeGame, player) > 0){
                    buttons.add(Button.success("mercerMove_"+planetName+"_"+planet+"_infantry","Move Infantry from "+Helper.getPlanetRepresentation(planet, activeGame) +" to "+Helper.getPlanetRepresentation(planetName, activeGame)));
                }
                if(ButtonHelper.getNumberOfMechsOnPlanet(planet, activeGame, player) > 0){
                    buttons.add(Button.success("mercerMove_"+planetName+"_"+planet+"_mech","Move mech from "+Helper.getPlanetRepresentation(planet, activeGame) +" to "+Helper.getPlanetRepresentation(planetName, activeGame)));
                }
            }
            buttons.add(Button.danger("deleteButtons", "Done moving to this planet"));
            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getTrueIdentity(player, activeGame) +" use buttons to resolve move of mercer units to this planet", buttons);
        }
        if ("l1z1xagent".equalsIgnoreCase(agent)) {
            String posNPlanet = rest.replace("l1z1xagent_","");
            String pos = posNPlanet.split("_")[0];
            String planetName = posNPlanet.split("_")[1];
            new RemoveUnits().unitParsing(event, player.getColor(), activeGame.getTileByPosition(pos), "1 infantry " + planetName, activeGame);
            new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileByPosition(pos), "1 mech " + planetName, activeGame);
            String successMessage = ident + " replaced 1 " + Helper.getEmojiFromDiscord("infantry") + " on " + Helper.getPlanetRepresentation(planetName, activeGame) + " with 1 mech.";
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), successMessage);
        }

        if ("muaatagent".equalsIgnoreCase(agent)) {
            String faction = rest.replace("muaatagent_","");
            Player p2 = Helper.getPlayerFromColorOrFaction(activeGame, faction);
            MessageChannel channel = event.getMessageChannel();
            if (activeGame.isFoWMode()) {
                channel = p2.getPrivateChannel();
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Sent buttons to the selected player");
            }
            String message = "Use buttons to select which tile to Umbat in";
            List<Tile> tiles = ButtonHelper.getTilesOfPlayersSpecificUnit(activeGame, p2, "warsun");
            List<Tile> tiles2 = ButtonHelper.getTilesOfPlayersSpecificUnit(activeGame, p2, "fs");
            for (Tile tile : tiles2) {
                if (!tiles.contains(tile)) {
                    tiles.add(tile);
                }
            }
            List<Button> buttons = new ArrayList<>();
            for (Tile tile : tiles) {
                Button starTile = Button.success("umbatTile_" + tile.getPosition(), tile.getRepresentationForButtons(activeGame, p2));
                buttons.add(starTile);
            }
            MessageHelper.sendMessageToChannelWithButtons(channel, Helper.getPlayerRepresentation(p2, activeGame, activeGame.getGuild(), true) + message, buttons);
        }
        
        if ("arborecagent".equalsIgnoreCase(agent)) {
            String faction = rest.replace("arborecagent_","");
            Player p2 = Helper.getPlayerFromColorOrFaction(activeGame, faction);
            MessageChannel channel = event.getMessageChannel();
            if(activeGame.isFoWMode()){
                channel = p2.getPrivateChannel();
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Sent buttons to the selected player");
            }
            String message = "Use buttons to select which tile to use arborec agent in";
            List<Button> buttons = getTilesToArboAgent(p2, activeGame, event);
            MessageHelper.sendMessageToChannelWithButtons(channel,Helper.getPlayerRepresentation(p2, activeGame, activeGame.getGuild(), true) + message, buttons);
        }

        String exhaustedMessage = event.getMessage().getContentRaw();
        if("".equalsIgnoreCase(exhaustedMessage)){
            exhaustedMessage ="Updated";
        }
        List<ActionRow> actionRow2 = new ArrayList<>();
        for (ActionRow row : event.getMessage().getActionRows()) {
            List<ItemComponent> buttonRow = row.getComponents();
            int buttonIndex = buttonRow.indexOf(event.getButton());
            if (buttonIndex > -1 && !"nomadagentmercer".equalsIgnoreCase(agent)) {
                buttonRow.remove(buttonIndex);
            }
            if (buttonRow.size() > 0) {
                actionRow2.add(ActionRow.of(buttonRow));
            }
        }
        if (actionRow2.size() > 0 && !exhaustedMessage.contains("select the user of the agent")) {
                event.getMessage().editMessage(exhaustedMessage).setComponents(actionRow2).queue();
        } else {
            event.getMessage().delete().queue();
        }
    }
    public static void yinAgent(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident, String trueIdentity){
        List<Button> buttons = ButtonHelper.getButtonsForAgentSelection(activeGame, buttonID);
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
    public static void resolveSardakkCommander(Game activeGame, Player p1, String buttonID, ButtonInteractionEvent event, String ident ){
        String mechorInf = buttonID.split("_")[1];
        String planet1= buttonID.split("_")[2];
        String planet2 = buttonID.split("_")[3];
        String planetRepresentation2 = Helper.getPlanetRepresentation(planet2, activeGame);
        String planetRepresentation = Helper.getPlanetRepresentation(planet1, activeGame);

        String message = ident + " moved 1 "+ mechorInf + " from " +planetRepresentation2 + " to "+planetRepresentation +" using Sardakk Commander";
         new RemoveUnits().unitParsing(event, p1.getColor(),
                            Helper.getTileFromPlanet(planet2, activeGame), "1 "+mechorInf + " "+planet2,
             activeGame);
        new AddUnits().unitParsing(event, p1.getColor(),
                            Helper.getTileFromPlanet(planet1, activeGame), "1 "+mechorInf + " "+planet1,
            activeGame);
       
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p1, activeGame), message);
        String exhaustedMessage = event.getMessage().getContentRaw();
        if("".equalsIgnoreCase(exhaustedMessage)){
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
    public static List<Button> getSardakkCommanderButtons(Game activeGame, Player player, GenericInteractionCreateEvent event) {
       
         Tile tile =  activeGame.getTileByPosition(activeGame.getActiveSystem());
        List<Button> buttons = new ArrayList<>();
        for(UnitHolder planetUnit : tile.getUnitHolders().values()){
            if("space".equalsIgnoreCase(planetUnit.getName())){
                continue;
            }
            Planet planetReal =  (Planet) planetUnit;
            String planet = planetReal.getName();
            String planetId = planetReal.getName();
            String planetRepresentation = Helper.getPlanetRepresentation(planetId, activeGame);
            for(String pos2 : FoWHelper.getAdjacentTiles(activeGame, tile.getPosition(), player, false)){
                Tile tile2 = activeGame.getTileByPosition(pos2);
                if(AddCC.hasCC(event, player.getColor(), tile2)){
                    continue;
                }
                 for(UnitHolder planetUnit2 : tile2.getUnitHolders().values()){
                    if("space".equalsIgnoreCase(planetUnit2.getName())){
                        continue;
                    }
                    Planet planetReal2 =  (Planet) planetUnit2;
                    String planet2 = planetReal2.getName();
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
                     String planetRepresentation2 = Helper.getPlanetRepresentation(planetId2, activeGame);
                     if(numInf > 0){
                            buttons.add(Button.success("sardakkcommander_infantry_"+planetId+"_"+planetId2, "Commit 1 infantry from "+planetRepresentation2+" to "+planetRepresentation).withEmoji(Emoji.fromFormatted(Helper.getFactionIconFromDiscord("sardakk"))));
                        }
                     if(numMechs > 0){
                            buttons.add(Button.primary("sardakkcommander_mech_"+planetId+"_"+planetId2, "Commit 1 mech from "+planetRepresentation2+" to "+planetRepresentation).withEmoji(Emoji.fromFormatted(Helper.getFactionIconFromDiscord("sardakk"))));
                        }
                 }
            }
        }
        return buttons;
     }

    public static List<Button> getXxchaPeaceAccordsButtons(Game activeGame, Player player, GenericInteractionCreateEvent event, String finChecker) {
        List<String> planetsChecked = new ArrayList<>();
        List<Button> buttons = new ArrayList<>();
        for(String planet : player.getPlanets(activeGame)){
            Tile tile =  Helper.getTileFromPlanet(planet, activeGame);
            for(String pos2 : FoWHelper.getAdjacentTiles(activeGame, tile.getPosition(), player, false)){
                Tile tile2 = activeGame.getTileByPosition(pos2);
                for(UnitHolder planetUnit2 : tile2.getUnitHolders().values()){
                    if("space".equalsIgnoreCase(planetUnit2.getName())){
                        continue;
                    }
                    Planet planetReal2 =  (Planet) planetUnit2;
                    String planet2 = planetReal2.getName(); 
                    String planetRepresentation2 = Helper.getPlanetRepresentation(planet2, activeGame);
                    if (!player.getPlanets(activeGame).contains(planet2) && !planetRepresentation2.contains("Mecatol") && (planetReal2.getUnits() == null || planetReal2.getUnits().isEmpty()) && !planetsChecked.contains(planet2)) {
                        buttons.add(Button.success(finChecker+"peaceAccords_"+planet2, "Use peace accords to take control of "+planetRepresentation2).withEmoji(Emoji.fromFormatted(Helper.getFactionIconFromDiscord("xxcha"))));
                        planetsChecked.add(planet2);
                    }
                }
            }
        }
        return buttons;
     }
     public static void resolvePeaceAccords(String buttonID, String ident, Player player, Game activeGame, ButtonInteractionEvent event){
        String planet = buttonID.split("_")[1];
        new PlanetAdd().doAction(player, planet, activeGame, event);
         String planetRepresentation2 = Helper.getPlanetRepresentation(planet, activeGame);
         String msg = ident + " claimed the planet "+planetRepresentation2 + " using the peace accords ability";
         MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg);
         event.getMessage().delete().queue();
     }
    public static List<Button> getSardakkAgentButtons(Game activeGame, Player player) {
       
         Tile tile =  activeGame.getTileByPosition(activeGame.getActiveSystem());
        List<Button> buttons = new ArrayList<>();
        for(UnitHolder planetUnit : tile.getUnitHolders().values()){
            if("space".equalsIgnoreCase(planetUnit.getName())){
                continue;
            }
            Planet planetReal =  (Planet) planetUnit;
            String planet = planetReal.getName();    
            if (player.getPlanets(activeGame).contains(planet)) {
                String planetId = planetReal.getName();
                String planetRepresentation = Helper.getPlanetRepresentation(planetId, activeGame);
                buttons.add(Button.success("exhaustAgent_sardakkagent_"+ activeGame.getActiveSystem()+"_"+planetId, "Use Sardakk Agent on "+planetRepresentation).withEmoji(Emoji.fromFormatted(Helper.getFactionIconFromDiscord("sardakk"))));
            }
        }

        return buttons;

    }
    public static List<Button> getMercerAgentInitialButtons(Game activeGame, Player player) {
         Tile tile =  activeGame.getTileByPosition(activeGame.getActiveSystem());
        List<Button> buttons = new ArrayList<>();
        for(UnitHolder planetUnit : tile.getUnitHolders().values()){
            if("space".equalsIgnoreCase(planetUnit.getName())){
                continue;
            }
            Planet planetReal =  (Planet) planetUnit;
            String planet = planetReal.getName();    
            if (player.getPlanets(activeGame).contains(planet)) {
                String planetId = planetReal.getName();
                String planetRepresentation = Helper.getPlanetRepresentation(planetId, activeGame);
                buttons.add(Button.success("exhaustAgent_nomadagentmercer_"+ activeGame.getActiveSystem()+"_"+planetId, "Use Nomad Agent General Mercer on "+planetRepresentation).withEmoji(Emoji.fromFormatted(Helper.getFactionIconFromDiscord("nomad"))));
            }
        }

        return buttons;
    }
    public static List<Button> getL1Z1XAgentButtons(Game activeGame, Player player) {
         Tile tile =  activeGame.getTileByPosition(activeGame.getActiveSystem());
        List<Button> buttons = new ArrayList<>();
        for(UnitHolder planetUnit : tile.getUnitHolders().values()){
            if("space".equalsIgnoreCase(planetUnit.getName())){
                continue;
            }
            Planet planetReal =  (Planet) planetUnit;
            String planet = planetReal.getName();    
            if (player.getPlanets(activeGame).contains(planet) && FoWHelper.playerHasInfantryOnPlanet(player, tile, planet)) {
                String planetId = planetReal.getName();
                String planetRepresentation = Helper.getPlanetRepresentation(planetId, activeGame);
                buttons.add(Button.success("exhaustAgent_l1z1xagent_"+ activeGame.getActiveSystem()+"_"+planetId, "Use L1Z1X Agent on "+planetRepresentation).withEmoji(Emoji.fromFormatted(Helper.getFactionIconFromDiscord("l1z1x"))));
            }
        }

        return buttons;

    }
    public static void giveKeleresCommsNTg(Game activeGame, GenericInteractionCreateEvent event){

        for(Player player : activeGame.getPlayers().values()){
            if(player.isRealPlayer() && player.hasAbility("council_patronage")){
                MessageChannel channel = activeGame.getActionsChannel();
                if(activeGame.isFoWMode()){
                    channel = player.getPrivateChannel();
                }
                player.setTg(player.getTg()+1);
                player.setCommodities(player.getCommoditiesTotal());
                String message = Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), true) + " due to your council patronage ability, 1tg has been added to your total and your commodities have been refreshed";
                MessageHelper.sendMessageToChannel(channel, message);
                pillageCheck(player, activeGame);
                ButtonHelper.resolveMinisterOfCommerceCheck(activeGame, player, event);
                cabalAgentInitiation(activeGame, player);

            }
        }
     }
     public static boolean isNextToEmpyMechs(Game activeGame, Player ACPlayer, Player EmpyPlayer)
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
         List<Tile> tiles = ButtonHelper.getTilesOfPlayersSpecificUnit(activeGame, EmpyPlayer, "mech");
         for(Tile tile : tiles)
         {
             
             Set<String> adjTiles = FoWHelper.getAdjacentTiles(activeGame, tile.getPosition(), EmpyPlayer, true);
             for(String adjTile : adjTiles)
             {
                 
                 Tile adjT = activeGame.getTileMap().get(adjTile);
                 if(FoWHelper.playerHasUnitsInSystem(ACPlayer, adjT))
                 {
                     return true;
                 }
             }
         }
         return false;
     }
    public static void yssarilCommander(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident){
        buttonID = buttonID.replace("yssarilcommander_", "");
        String enemyFaction = buttonID.split("_")[1];
        Player enemy = Helper.getPlayerFromColorOrFaction(activeGame, enemyFaction);
        String message = "";
        String type = buttonID.split("_")[0];
        if("ac".equalsIgnoreCase(type)){
            ShowAllAC.showAll(enemy, player, activeGame);
            message = "Yssaril commander used to look at ACs";
        }
        if("so".equalsIgnoreCase(type)){
            new ShowAllSO().showAll(enemy, player, activeGame);
            message = "Yssaril commander used to look at SOs";
        }
        if("pn".equalsIgnoreCase(type)){
            new ShowAllPN().showAll(enemy, player, activeGame, false);
            message = "Yssaril commander used to look at PNs";
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), message);
        if(activeGame.isFoWMode()){
            MessageHelper.sendMessageToChannel(enemy.getPrivateChannel(), message);
        }
        event.getMessage().delete().queue();
    }

    public static void starforgeTile(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident){
        String pos = buttonID.replace("starforgeTile_", "");
        List<Button> buttons = new ArrayList<>();
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
    public static void starforge(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident){
        String unitNPlace = buttonID.replace("starforge_", "");
        String unit = unitNPlace.split("_")[0];
        String pos = unitNPlace.split("_")[1];
        Tile tile = activeGame.getTileByPosition(pos);
        String successMessage = "Reduced strategy pool CCs by 1 (" + (player.getStrategicCC()) + "->"
                + (player.getStrategicCC() - 1) + ")";
        player.setStrategicCC(player.getStrategicCC() - 1);
        resolveMuaatCommanderCheck(player, activeGame, event);
        MessageHelper.sendMessageToChannel(event.getChannel(), successMessage);
        List<Button> buttons = ButtonHelper.getStartOfTurnButtons(player, activeGame, true, event);
        if ("destroyer".equals(unit)) {
            new AddUnits().unitParsing(event, player.getColor(), tile, "1 destroyer", activeGame);
            successMessage = "Produced 1 " + Helper.getEmojiFromDiscord("destroyer") + " in tile "
                    + tile.getRepresentationForButtons(activeGame, player) + ".";

        } else {
            new AddUnits().unitParsing(event, player.getColor(), tile, "2 ff", activeGame);
            successMessage = "Produced 2 " + Helper.getEmojiFromDiscord("fighter") + " in tile "
                    + tile.getRepresentationForButtons(activeGame, player) + ".";
        }
        if(!activeGame.getLaws().containsKey("articles_war")){
            successMessage = ButtonHelper.putInfWithMechsForStarforge(pos, successMessage, activeGame, player, event);
        }
        

        MessageHelper.sendMessageToChannel(event.getChannel(), successMessage);
        String message = "Use buttons to end turn or do another action";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        event.getMessage().delete().queue();
    }
    public static void arboAgentPutShip(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident){
        String unitNPlace = buttonID.replace("arboAgentPutShip_", "");
        String unit = unitNPlace.split("_")[0];
        String pos = unitNPlace.split("_")[1];
        Tile tile = activeGame.getTileByPosition(pos);
        String successMessage = "";

        switch (unit) {
            case "destroyer" -> {
                new AddUnits().unitParsing(event, player.getColor(), tile, "destroyer", activeGame);
                successMessage = ident + " Placed 1 " + Helper.getEmojiFromDiscord("destroyer") + " in tile "
                    + tile.getRepresentationForButtons(activeGame, player) + " via Arborec Agent.";
            }
            case "cruiser" -> {
                new AddUnits().unitParsing(event, player.getColor(), tile, "cruiser", activeGame);
                successMessage = ident + " Placed 1 " + Helper.getEmojiFromDiscord("cruiser") + " in tile "
                    + tile.getRepresentationForButtons(activeGame, player) + " via Arborec Agent.";
            }
            case "carrier" -> {
                new AddUnits().unitParsing(event, player.getColor(), tile, "carrier", activeGame);
                successMessage = ident + " Placed 1 " + Helper.getEmojiFromDiscord("carrier") + " in tile "
                    + tile.getRepresentationForButtons(activeGame, player) + " via Arborec Agent.";
            }
            case "dreadnought" -> {
                new AddUnits().unitParsing(event, player.getColor(), tile, "dreadnought", activeGame);
                successMessage = ident + " Placed 1 " + Helper.getEmojiFromDiscord("dreadnought") + " in tile "
                    + tile.getRepresentationForButtons(activeGame, player) + " via Arborec Agent.";
            }
        }

        MessageHelper.sendMessageToChannel(event.getChannel(), successMessage);
        event.getMessage().delete().queue();
    }
    public static void titansCommanderUsage(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident){
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
    public static void replaceSleeperWith(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident){
        buttonID = buttonID.replace("replaceSleeperWith_", "");
        String planetName = buttonID.split("_")[1];
        String unit = buttonID.split("_")[0];
        String message;
        new SleeperToken().addOrRemoveSleeper(event, activeGame, planetName, player);
        if("mech".equalsIgnoreCase(unit)){
            new AddUnits().unitParsing(event, player.getColor(), activeGame.getTile(AliasHandler.resolveTile(planetName)), "mech " + planetName + ", inf "+planetName, activeGame);
            message = ident + " replaced a sleeper on " + Helper.getPlanetRepresentation(planetName, activeGame) + " with a "+ Helper.getEmojiFromDiscord("mech") +" and "+ Helper.getEmojiFromDiscord("infantry");
        }else{
            new AddUnits().unitParsing(event, player.getColor(), activeGame.getTile(AliasHandler.resolveTile(planetName)), "pds " + planetName, activeGame);
            message = ident + " replaced a sleeper on " + Helper.getPlanetRepresentation(planetName, activeGame) + " with a "+ Helper.getEmojiFromDiscord("pds");
            if(player.getLeaderIDs().contains("titanscommander") && !player.hasLeaderUnlocked("titanscommander")){
                ButtonHelper.commanderUnlockCheck(player, activeGame, "titans", event);
            }
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        event.getMessage().delete().queue();
    }

}