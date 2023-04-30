package ti4.buttons;

import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.*;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.*;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import ti4.MapGenerator;
import ti4.MessageListener;
import ti4.commands.agenda.RevealAgenda;
import ti4.commands.cardsac.ACInfo_Legacy;
import ti4.map.UnitHolder;
import ti4.commands.cardsac.PlayAC;
import ti4.commands.cardsso.ScoreSO;
import ti4.commands.explore.ExploreSubcommandData;
import ti4.commands.status.Cleanup;
import ti4.commands.status.RevealStage1;
import ti4.commands.status.RevealStage2;
import ti4.commands.status.ScorePublic;
import ti4.commands.units.AddUnits;
import ti4.commands.player.PlanetAdd;
import ti4.commands.player.PlanetRefresh;
import ti4.helpers.Constants;
import ti4.helpers.AliasHandler;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.MapSaveLoadManager;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.map.Tile;
import java.util.*;
import java.util.concurrent.TimeUnit;
import ti4.generator.Mapper;

import javax.lang.model.util.ElementScanner14;

public class ButtonListener extends ListenerAdapter {
    public static HashMap<Guild, HashMap<String, Emoji>> emoteMap = new HashMap<>();
    private static HashMap<String, Set<Player>> playerUsedSC = new HashMap<>();

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        event.deferEdit().queue();
        String id = event.getUser().getId();
        MessageListener.setActiveGame(event.getMessageChannel(), id, "button");
        String buttonID = event.getButton().getId();
        String buttonLabel = event.getButton().getLabel();
        String lastchar = StringUtils.right(buttonLabel, 2).replace("#", "");
        String lastcharMod = StringUtils.right(buttonID, 1);
        if (buttonID == null) {
            event.getChannel().sendMessage("Button command not found").queue();
            return;
        }
        String messageID = event.getMessage().getId();

        String gameName = event.getChannel().getName();
        gameName = gameName.replace(ACInfo_Legacy.CARDS_INFO, "");
        gameName = gameName.substring(0, gameName.indexOf("-"));
        Map activeMap = MapManager.getInstance().getMap(gameName);
        Player player = activeMap.getPlayer(id);
        player = Helper.getGamePlayer(activeMap, player, event.getMember(), id);
        if (player == null) {
            event.getChannel().sendMessage("You're not a player of the game").queue();
            return;
        }

        MessageChannel privateChannel = event.getChannel();
        if (activeMap.isFoWMode()) {
            if (player.getPrivateChannel() == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Private channels are not set up for this game. Messages will be suppressed.");
                privateChannel = null;
            } else {
                privateChannel = player.getPrivateChannel();
            }
        }
        
        MessageChannel mainGameChannel = event.getChannel();
        if (activeMap.getMainGameChannel() != null) {
            mainGameChannel = activeMap.getMainGameChannel();
        }

        MessageChannel actionsChannel = null;
        for (TextChannel textChannel_ : MapGenerator.jda.getTextChannels()) {
            if (textChannel_.getName().equals(gameName + Constants.ACTIONS_CHANNEL_SUFFIX)) {
                actionsChannel = textChannel_;
                break;
            }
        }

        if (buttonID.startsWith(Constants.AC_PLAY_FROM_HAND)) {
            String acID = buttonID.replace(Constants.AC_PLAY_FROM_HAND, "");
            MessageChannel channel = null;
            if (activeMap.getMainGameChannel() != null) {
                channel = activeMap.getMainGameChannel();
            } else {
                channel = actionsChannel;
            }

            if (channel != null) {
                try {
                    String error = PlayAC.playAC(event, activeMap, player, acID, channel, event.getGuild());
                    if (error != null) {
                        event.getChannel().sendMessage(error).queue();
                    }
                } catch (Exception e) {
                    BotLogger.log(event, "Could not parse AC ID: " + acID, e);
                    event.getChannel().asThreadChannel().sendMessage("Could not parse AC ID: " + acID + " Please play manually.").queue();
                    return;
                }
            } else {
                event.getChannel().sendMessage("Could not find channel to play card. Please ping Bothelper.").queue();
            }
        } else if (buttonID.startsWith(Constants.SO_SCORE_FROM_HAND)) {
            String soID = buttonID.replace(Constants.SO_SCORE_FROM_HAND, "");
            MessageChannel channel = null;
            if (activeMap.isFoWMode()) {
                channel = privateChannel;
            } else if (activeMap.isCommunityMode() && activeMap.getMainGameChannel() != null) {
                channel = mainGameChannel;
            } else {
                channel = actionsChannel;
            }

            if (channel != null) {
                try {
                    int soIndex = Integer.parseInt(soID);
                    ScoreSO.scoreSO(event, activeMap, player, soIndex, channel);
                } catch (Exception e) {
                    BotLogger.log(event, "Could not parse SO ID: " + soID, e);
                    event.getChannel().sendMessage("Could not parse SO ID: " + soID + " Please Score manually.").queue();
                    return;
                }
            } else {
                event.getChannel().sendMessage("Could not find channel to play card. Please ping Bothelper.").queue();
            }
        } else if (buttonID.startsWith(Constants.PO_SCORING)) {
            String poID = buttonID.replace(Constants.PO_SCORING, "");
            try {
                int poIndex = Integer.parseInt(poID);
                ScorePublic.scorePO(event, privateChannel, activeMap, player, poIndex);
                addReaction(event, false, false, null, "");
            } catch (Exception e) {
                BotLogger.log(event, "Could not parse PO ID: " + poID, e);
                event.getChannel().sendMessage("Could not parse PO ID: " + poID + " Please Score manually.").queue();
                return;
            }
        } else if (buttonID.startsWith(Constants.SC3_ASSIGN_SPEAKER_BUTTON_ID_PREFIX)) {
            String faction = buttonID.replace(Constants.SC3_ASSIGN_SPEAKER_BUTTON_ID_PREFIX, "");
            if (!player.getSCs().contains(3)) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Only the player who played Politics can assign Speaker");
                return;
            }
            if (activeMap != null && !activeMap.isFoWMode()) {
                for (Player player_ : activeMap.getPlayers().values()) {
                    if (player_.getFaction().equals(faction)) {
                        activeMap.setSpeaker(player_.getUserID());
                        String message = Emojis.SpeakerToken + " Speaker assigned to: " + Helper.getPlayerRepresentation(event, player_);
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
                    }
                }
            }
        } else if (buttonID.startsWith("reveal_stage")) {
            String lastC = StringUtils.right(buttonLabel, 1);
            if(lastC.equalsIgnoreCase("2"))
            {
                new RevealStage2().revealS2(event, event.getChannel());
            }
            else
            {
                new RevealStage1().revealS1(event, event.getChannel());
            }
            String message2 = "Resolve status homework using the buttons.";   
            Button draw1AC = Button.success("draw_1_AC", "Draw 1 AC");
            Button confirmCCs = Button.primary("confirm_cc", "Confirm Your CCs");
            boolean custodiansTaken = activeMap.isCustodiansScored();
            Button passOnAbilities;
            if(custodiansTaken)
            {
                passOnAbilities = Button.danger("pass_on_abilities", "Pass on Political Stability/Ancient Burial Sites/Maw Of Worlds");
            }
            else
            {
                passOnAbilities = Button.danger("pass_on_abilities", "Pass on Political Stability/Summit/Manipulate Investments");
            }
            ActionRow actionRow4 = ActionRow.of(List.of(draw1AC, confirmCCs, passOnAbilities));
            MessageCreateBuilder baseMessageObject4 = new MessageCreateBuilder().addContent(message2);
            if (!actionRow4.isEmpty()) baseMessageObject4.addComponents(actionRow4);
                    event.getChannel().sendMessage(baseMessageObject4.build()).queue(message4_ -> {
            });
        } else if (buttonID.startsWith("sc_follow_") && (!buttonID.contains("leadership")) && (!buttonID.contains("trade"))) {
            boolean used = addUsedSCPlayer(messageID, activeMap, player, event, "");
            if (!used) {
                String message = deductCC(player, event);
                int scnum = 1;
                boolean setstatus = true;
                try{
                    scnum = Integer.parseInt(lastcharMod);
                } catch(NumberFormatException e) {
                    setstatus = false;
                }
                if(setstatus)
                {
                    player.addFollowedSC(scnum);
                }
                addReaction(event, false, false, message, "");
            }
        }
        else if (buttonID.startsWith("sc_no_follow_")) {
            int scnum2 = 1;
            boolean setstatus = true;
            try{
                scnum2 = Integer.parseInt(lastcharMod);
            } catch(NumberFormatException e) {
                setstatus = false;
            }
            if(setstatus)
            {
                player.addFollowedSC(scnum2);
            }
            addReaction(event, false, false, "Not Following", "");
            Set<Player> players = playerUsedSC.get(messageID);
            if (players == null) {
                players = new HashSet<>();
            }
            players.remove(player);
            playerUsedSC.put(messageID, players);
        }
        else {
            switch (buttonID) {
                //AFTER THE LAST PLAYER PASS COMMAND, FOR SCORING
                case Constants.PO_NO_SCORING -> {
                    String message = Helper.getPlayerRepresentation(event, player) + " - no Public Objective scored.";
                    if (!activeMap.isFoWMode()) {
                        MessageHelper.sendMessageToChannel(event.getChannel(), message);
                    }
                    String reply = activeMap.isFoWMode() ? "No public objective scored" : null;
                    addReaction(event, false, false, reply, "");
                }
                case Constants.SO_NO_SCORING -> {
                    String message = Helper.getPlayerRepresentation(event, player) + " - no Secret Objective scored.";
                    if (!activeMap.isFoWMode()) {
                        MessageHelper.sendMessageToChannel(event.getChannel(), message);
                    }
                    String reply = activeMap.isFoWMode() ? "No secret objective scored" : null;
                    addReaction(event, false, false, reply, "");
                }
                //AFTER AN ACTION CARD HAS BEEN PLAYED
                case "sabotage" -> addReaction(event, true, true, "Sabotaging Action Card Play", " Sabotage played");
                case "no_sabotage" -> {
                    String message = activeMap.isFoWMode() ? "No sabotage" : null;
                    addReaction(event, false, false, message, "");
                }
                case "sc_ac_draw" -> {
                    boolean used = addUsedSCPlayer(messageID + "ac", activeMap, player, event, "");
                    if (used) {
                        break;
                    }
                    boolean isYssaril = player.getFaction().equals("yssaril");
                    String message = isYssaril ? "Drew 3 Actions cards" : "Drew 2 Actions cards";
                    int count = isYssaril ? 3 : 2;
                    for (int i = 0; i < count; i++) {
                        activeMap.drawActionCard(player.getUserID());
                    }
                    ACInfo_Legacy.sentUserCardInfo(event, activeMap, player, false);
                    addReaction(event, false, false, message, "");
                }
                case "sc_draw_so" -> {
                    boolean used = addUsedSCPlayer(messageID + "so", activeMap, player, event, " Drew a " + Emojis.SecretObjective);
                    if (used) {
                        break;
                    }
                    String message = "Drew Secret Objective";
                    activeMap.drawSecretObjective(player.getUserID());
                    player.addFollowedSC(8);
                    ACInfo_Legacy.sentUserCardInfo(event, activeMap, player, false);
                    addReaction(event, false, false, message, "");
                }
                case "sc_trade_follow" -> {
                    boolean used = addUsedSCPlayer(messageID, activeMap, player, event, "");
                    if (used) {
                        break;
                    }
                    String message = deductCC(player, event);
                    player.addFollowedSC(5);
                    player.setCommodities(player.getCommoditiesTotal());
                    addReaction(event, false, false, message, "");
                    addReaction(event, false, false, "Replenishing Commodities", "");
                }
                case "sc_follow_trade" -> {
                    boolean used = addUsedSCPlayer(messageID, activeMap, player, event, "");
                    if (used) {
                        break;
                    }
                    String message = deductCC(player, event);
                    player.addFollowedSC(5);
                    player.setCommodities(player.getCommoditiesTotal());
                    addReaction(event, false, false, message, "");
                    addReaction(event, false, false, "Replenishing Commodities", "");
                }
                case "sc_follow_leadership" -> {
                    String message = Helper.getPlayerPing(player) + " following.";
                    player.addFollowedSC(1);
                    addReaction(event, false, false, message, "");
                }
                case "sc_leadership_follow" -> {
                    String message = Helper.getPlayerPing(player) + " following.";
                    player.addFollowedSC(1);
                    addReaction(event, false, false, message, "");
                }
                case "sc_refresh" -> {
                    boolean used = addUsedSCPlayer(messageID, activeMap, player, event, "Replenish");
                    if (used) {
                        break;
                    }
                    player.setCommodities(player.getCommoditiesTotal());
                    player.addFollowedSC(5);
                    addReaction(event, false, false, "Replenishing Commodities", "");
                }
                case "sc_refresh_and_wash" -> {
                    boolean used = addUsedSCPlayer(messageID, activeMap, player, event, "Replenish and Wash");
                    if (used) {
                        break;
                    }
                    int commoditiesTotal = player.getCommoditiesTotal();
                    int tg = player.getTg();
                    player.setTg(tg + commoditiesTotal);
                    player.setCommodities(0);
                    player.addFollowedSC(5);
                    addReaction(event, false, false, "Replenishing and washing", "");
                }
                case "sc_follow" -> {
                    boolean used = addUsedSCPlayer(messageID, activeMap, player, event, "");
                    if (used) {
                        break;
                    }
                    String message = deductCC(player, event);
                    int scnum = 1;
                    boolean setstatus = true;
                    try{
                        scnum = Integer.parseInt(lastchar);
                    } catch(NumberFormatException e) {
                        setstatus = false;
                    }
                    if(setstatus)
                    {
                        player.addFollowedSC(scnum);
                    }
                    addReaction(event, false, false, message, "");
                    
                }
                case "trade_primary" -> {
                    if (!player.getSCs().contains(5)) {
                        break;
                    }
                    boolean used = addUsedSCPlayer(messageID, activeMap, player, event, "Trade Primary");
                    if (used) {
                        break;
                    }
                    int tg = player.getTg();
                    player.setTg(tg + 3);
                    player.setCommodities(player.getCommoditiesTotal());
                    addReaction(event, false, false, "gained 3" + Emojis.tg + " and replenished commodities (" + String.valueOf(player.getCommodities()) + Emojis.comm + ")", "");
                }
                case "score_imperial" -> {
                    if (player == null || activeMap == null) {
                        break;
                    }
                    if (!player.getSCs().contains(8)) {
                        MessageHelper.sendMessageToChannel(privateChannel, "Only the player who has " + Helper.getSCBackRepresentation(event, 8) + " can score the Imperial point");
                        break;
                    }
                    boolean used = addUsedSCPlayer(messageID + "score_imperial", activeMap, player, event, " scored Imperial");
                    if (used) {
                        break;
                    }
                    ScorePublic.scorePO(event, privateChannel, activeMap, player, 0);
                }
                //AFTER AN AGENDA HAS BEEN REVEALED
                case "play_when" -> {
                    clearAllReactions(event);
                    addReaction(event, true, true, "Playing When", "When Played");
                }
                case "no_when" -> {
                    String message = activeMap.isFoWMode() ? "No whens" : null;
                    addReaction(event, false, false, message, "");
                }
                case "play_after" -> {
                    clearAllReactions(event);
                    
                    addReaction(event, true, true, "Playing After", "After Played");
                }
                case "no_after" -> {
                    String message = activeMap.isFoWMode() ? "No afters" : null;
                    addReaction(event, false, false, message, "");
                }
                case "gain_2_comms" -> {
                    if(player.getCommodities()+2 > player.getCommoditiesTotal())
                    {
                        player.setCommodities(player.getCommoditiesTotal());
                        addReaction(event, false, false, "Gained Commodities to Max", "");
                    }
                    else
                    {
                        player.setCommodities(player.getCommodities()+2);
                        addReaction(event, false, false, "Gained 2 Commodities", "");
                    }
                }
                case "covert_2_comms" -> {
                    if(player.getCommodities() > 1)
                    {
                        player.setCommodities(player.getCommodities()-2);
                        player.setTg(player.getTg()+2);
                        addReaction(event, false, false, "Coverted 2 Commodities to 2 tg", "");
                    }
                    else
                    {
                        player.setTg(player.getTg()+player.getCommodities());
                        player.setCommodities(0);
                        addReaction(event, false, false, "Converted all remaining commodies (less than 2) into tg", "");
                    }
                }
                case "gain_1_comms" -> {
                    if(player.getCommodities()+1 > player.getCommoditiesTotal())
                    {
                        player.setCommodities(player.getCommoditiesTotal());
                        addReaction(event, false, false,"Gained No Commodities (at max already)", "");

                    }
                    else
                    {
                        player.setCommodities(player.getCommodities()+1);
                        addReaction(event, false, false,"Gained 1 Commodity", "");
                    }
                }
                case "spend_comm_for_AC" -> {
                    boolean isYssaril = player.getFaction().equals("yssaril");
                    int count2 = isYssaril ? 2 : 1;
                    if(player.getCommodities() > 0)
                    {
                        player.setCommodities(player.getCommodities()-1);
                        for (int i = 0; i < count2; i++) {
                            activeMap.drawActionCard(player.getUserID());
                        }
                        ACInfo_Legacy.sentUserCardInfo(event, activeMap, player, false);
                        addReaction(event, false, false,"Spent 1 commodity for "+count2+ " AC", "");
                    }
                    else if(player.getTg() > 0)
                    {
                        player.setTg(player.getTg()-1);
                        for (int i = 0; i < count2; i++) {
                            activeMap.drawActionCard(player.getUserID());
                        }
                        ACInfo_Legacy.sentUserCardInfo(event, activeMap, player, false);
                        addReaction(event, false, false,"Spent 1 tg for an AC", "");

                    }
                    else{
                        addReaction(event, false, false,"Didn't have any comms/tg to spend, no AC drawn", "");
                    }
                }
                case "spend_comm_for_mech" -> {
                    String labelP = event.getButton().getLabel();
                    String planetName = labelP.substring(labelP.lastIndexOf(" ")+1, labelP.length());
                    if(player.getCommodities() > 0)
                    {
                        player.setCommodities(player.getCommodities()-1);
                         new AddUnits().unitParsing(event, player.getColor(), activeMap.getTile(AliasHandler.resolveTile(planetName)), "mech "+planetName, activeMap);
                        addReaction(event, false, false, "Spent 1 commodity for a mech on "+planetName, "");
                    }
                    else if(player.getTg() > 0)
                    {
                        player.setTg(player.getTg()-1);
                        new AddUnits().unitParsing(event, player.getColor(), activeMap.getTile(AliasHandler.resolveTile(planetName)), "mech "+planetName, activeMap);
                        addReaction(event, false, false, "Spent 1 tg for a mech on "+ planetName, "");
                    }
                    else{
                        addReaction(event, false, false, "Didn't have any comms/tg to spend, no mech placed", "");
                    }
                }
                case "incease_strategy_cc" -> {
                    addReaction(event, false, false, "Increased Strategy Pool CCs By 1 ("+player.getStrategicCC()+"->"+(player.getStrategicCC()+1)+").", "");
                    player.setStrategicCC(player.getStrategicCC()+1);
                }
                case "incease_tactic_cc" -> {
                    addReaction(event, false, false, "Increased Tactic Pool CCs By 1 ("+player.getTacticalCC()+ "->" +(player.getTacticalCC()+1)+").", "");
                    player.setTacticalCC(player.getTacticalCC()+1);
                }
                case "incease_fleet_cc" -> {
                    addReaction(event, false, false, "Increased Fleet Pool CCs By 1 ("+player.getFleetCC()+"->"+(player.getFleetCC()+1)+").", "");

                    player.setFleetCC(player.getFleetCC()+1);
                }
                case "gain_1_tg" -> {

                    String message = "";
                    String labelP = event.getButton().getLabel();
                    String planetName = labelP.substring(labelP.lastIndexOf(" ")+1, labelP.length());
                    boolean failed = false;
                    if(labelP.contains("Inf") && labelP.contains("Mech"))
                    {
                        message = message + mechOrInfCheck(planetName, activeMap,player);
                        failed = message.contains("Please try again.");
                    }
                    if(!failed)
                    {
                        message = message + "Gained 1 tg ("+player.getTg()+"->"+(player.getTg()+1)+").";
                        player.setTg(player.getTg()+1);
                    }
                    addReaction(event, false, false, message, "");
                }
                case "decline_explore" -> {
                    addReaction(event, false, false, "Declined Explore", "");
                }
                case "confirm_cc" -> {
                    addReaction(event, true, false, "Confirmed CCs", "");
                }
                case "draw_1_AC" -> {
                    activeMap.drawActionCard(player.getUserID());
                    ACInfo_Legacy.sentUserCardInfo(event, activeMap, player, false);
                    addReaction(event, true, false, "Drew 1 AC", "");
                }
                case "pass_on_abilities" -> {
                    addReaction(event, false, false,"Passed"+event.getButton().getLabel().replace("Pass", ""), "");
                }
                case "planet_ready" -> {
                    String message = "";
                    String labelP = event.getButton().getLabel();
                    String planetName = labelP.substring(labelP.lastIndexOf(" ")+1, labelP.length());
                    boolean failed = false;
                    if(labelP.contains("Inf") && labelP.contains("Mech"))
                    {
                        message = message + mechOrInfCheck(planetName, activeMap, player);
                        failed = message.contains("Please try again.");
                    }

                    if(!failed)
                    {
                        new PlanetRefresh().doAction(player, planetName, activeMap);
                        message = message + "Readied "+ planetName;
                    }
                    addReaction(event, false, false, message, "");
                }
                case "sc_no_follow" -> {
                    int scnum2 = 1;
                    boolean setstatus = true;
                    try{
                        scnum2 = Integer.parseInt(lastchar);
                    } catch(NumberFormatException e) {
                        setstatus = false;
                    }
                    if(setstatus)
                    {
                        player.addFollowedSC(scnum2);
                    }
                    addReaction(event, false, false, "Not Following", "");
                    Set<Player> players = playerUsedSC.get(messageID);
                    if (players == null) {
                        players = new HashSet<>();
                    }
                    players.remove(player);
                    playerUsedSC.put(messageID, players);
                }
                case "gain_CC"-> {
                    String message = "";
                    String labelP = event.getButton().getLabel();
                    String planetName = labelP.substring(labelP.lastIndexOf(" ")+1, labelP.length());
                    boolean failed = false;
                    if(labelP.contains("Inf") && labelP.contains("Mech"))
                    {
                        message = message + mechOrInfCheck(planetName, activeMap, player);
                        failed = message.contains("Please try again.");
                    }

                    if(!failed)
                    {
                        String message2 = "Resolve cc gain using the buttons.";   
                        Button getTactic= Button.success("incease_tactic_cc", "Gain 1 Tactic CC");
                        Button getFleet = Button.success("incease_fleet_cc", "Gain 1 Fleet CC");
                        Button getStrat= Button.success("incease_strategy_cc", "Gain 1 Strategy CC");
                        ActionRow actionRow4 = ActionRow.of(List.of(getTactic, getFleet, getStrat));
                        MessageCreateBuilder baseMessageObject4 = new MessageCreateBuilder().addContent(message2);
                        if (!actionRow4.isEmpty()) baseMessageObject4.addComponents(actionRow4);
                             event.getChannel().sendMessage(baseMessageObject4.build()).queue(message4_ -> {
                        });

                    }
                    addReaction(event, false, false, message, "");
                }
                case "run_status_cleanup" -> {
                    new Cleanup().runStatusCleanup(activeMap);
                    addReaction(event, false, true, "Running Status Cleanup. ", "Status Cleanup Run!");
                    
                }
                default -> event.getHook().sendMessage("Button " + buttonID + " pressed.").queue();
            }
        }
        MapSaveLoadManager.saveMap(activeMap, event);
    }

    private String mechOrInfCheck(String planetName, Map activeMap, Player player)
    {
        String message = "";
        Tile tile = activeMap.getTile(AliasHandler.resolveTile(planetName));
        UnitHolder unitHolder = tile.getUnitHolders().get(planetName);
        int numMechs = 0;
        int numInf = 0;
        System.out.println(unitHolder.getUnits());
        String colorID = Mapper.getColorID(player.getColor());
        String mechKey = colorID + "_mf.png";
        String infKey = colorID + "_gf.png";
        if (unitHolder.getUnits() != null)
        {
            
            if(unitHolder.getUnits().get(mechKey) != null)
            {
                numMechs = unitHolder.getUnits().get(mechKey);
            }
            if(unitHolder.getUnits().get(infKey)!=null)
            {
                numInf = unitHolder.getUnits().get(infKey);
            }
        }
        if (numMechs > 0 || numInf > 0)
        {
            if (numMechs > 0)
            {
                message = "Planet had a mech. ";
            }
            else
            {
                message = "Planet did not have a mech. Removed 1 infantry ("+numInf+"->"+(numInf-1)+"). ";
                tile.removeUnit(planetName, infKey, 1);
            }
        }
        else
        {
            message = "Planet did not have a mech or infantry. Please try again.";
        }
        return message;
    }


    private boolean addUsedSCPlayer(String messageID, Map map, Player player, @NotNull ButtonInteractionEvent event, String defaultText) {
        Set<Player> players = playerUsedSC.get(messageID);
        if (players == null) {
            players = new HashSet<>();
        }
        boolean contains = players.contains(player);
        players.add(player);
        playerUsedSC.put(messageID, players);
        if (contains) {
            String alreadyUsedMessage = defaultText.isEmpty() ? "used Secondary of Strategy Card" : defaultText;
            String message = "Player: " + Helper.getPlayerPing(player) + " already " + alreadyUsedMessage;
            if (map.isFoWMode()) {
                MessageHelper.sendPrivateMessageToPlayer(player, map, message);
            } else {
                MessageHelper.sendMessageToChannel(event.getChannel(), message);
            }
        }
        return contains;
    }

    @NotNull
    private String deductCC(Player player, @NotNull ButtonInteractionEvent event) {
        int strategicCC = player.getStrategicCC();
        String message;
        if (strategicCC == 0) {
            message = "Have 0 CC in Strategy, can't follow";
        } else {
            strategicCC--;
            player.setStrategicCC(strategicCC);
            message = " following SC, deducted 1 CC from Strategy Tokens";
        }
        return message;
    }


    private void clearAllReactions(@NotNull ButtonInteractionEvent event) {
        Message mainMessage = event.getInteraction().getMessage();
        String messageId = mainMessage.getId();
        RestAction<Message> messageRestAction = event.getChannel().retrieveMessageById(messageId);
        messageRestAction.queue(m -> {
            RestAction<Void> voidRestAction = m.clearReactions();
            voidRestAction.queue();
        });
    }

    private void addReaction(@NotNull ButtonInteractionEvent event, boolean skipReaction, boolean sendPublic, String message, String additionalMessage) {
        String userID = event.getUser().getId();
        Map activeMap = MapManager.getInstance().getUserActiveMap(userID);
        Player player = Helper.getGamePlayer(activeMap, null, event.getMember(), userID);
        if (player == null || !player.isRealPlayer()) {
            event.getChannel().sendMessage("You're not an active player of the game").queue();
            return;
        }
        String playerFaction = player.getFaction();
        Guild guild = event.getGuild();
        if (guild == null) {
            event.getChannel().sendMessage("Could not find server Emojis").queue();
            return;
        }
        HashMap<String, Emoji> emojiMap = emoteMap.get(guild);
        List<RichCustomEmoji> emojis = guild.getEmojis();
        if (emojiMap != null && emojiMap.size() != emojis.size()) {
            emojiMap.clear();
        }
        if (emojiMap == null || emojiMap.isEmpty()) {
            emojiMap = new HashMap<>();
            for (Emoji emoji : emojis) {
                emojiMap.put(emoji.getName().toLowerCase(), emoji);
            }
        }
        
        Message mainMessage = event.getInteraction().getMessage();
        Emoji emojiToUse = Helper.getPlayerEmoji(activeMap, player, mainMessage);
        String messageId = mainMessage.getId();
        
        if (!skipReaction) {
            event.getChannel().addReactionById(messageId, emojiToUse).queue();
            checkForAllReactions(event, activeMap);
            if (message == null || message.isEmpty()) return;
        } 
        

        String text = Helper.getPlayerRepresentation(event, player) + " " + message;
        if (activeMap.isFoWMode() && sendPublic) {
            text = message;
        } else if (activeMap.isFoWMode() && !sendPublic) {
            text = Helper.getPlayerRepresentation(event, player, true) + " " + emojiToUse.getFormatted() + " " + message;
        }

        if (!additionalMessage.isEmpty()) {
            text += Helper.getGamePing(event.getGuild(), activeMap) + " " + additionalMessage;
        }

        if (activeMap.isFoWMode() && !sendPublic) {
            MessageHelper.sendPrivateMessageToPlayer(player, activeMap, text);
            return;
        }

        MessageHelper.sendMessageToChannel(Helper.getThreadChannelIfExists(event), text);
    }

    private void checkForAllReactions(@NotNull ButtonInteractionEvent event, Map map) {
        String messageId = event.getInteraction().getMessage().getId();

        Message mainMessage = event.getMessageChannel().retrieveMessageById(messageId).completeAfter(500, TimeUnit.MILLISECONDS);

        String userID = event.getUser().getId();
        Map activeMap = MapManager.getInstance().getUserActiveMap(userID);
        int matchingFactionReactions = 0;
        for (Player player : activeMap.getPlayers().values()) {
            if (!player.isRealPlayer()) {
                matchingFactionReactions++;
                continue;
            }

            String faction = player.getFaction();
            if (faction == null || faction.isEmpty() || faction.equals("null")) continue;

            Emoji reactionEmoji = Emoji.fromFormatted(Helper.getFactionIconFromDiscord(faction));
            if (map.isFoWMode()) {
                int index = 0;
                for (Player player_ : activeMap.getPlayers().values()) {
                    if (player_ == player) break;
                    index++;
                }
                reactionEmoji = Emoji.fromFormatted(Helper.getRandomizedEmoji(index, event.getMessageId()));
            }
            MessageReaction reaction = mainMessage.getReaction(reactionEmoji);
            if (reaction != null) matchingFactionReactions++;
        }
        int numberOfPlayers = activeMap.getPlayers().size();
        if (matchingFactionReactions >= numberOfPlayers) { //TODO: @Jazzxhands to verify this will work for FoW
            respondAllPlayersReacted(event);
        }
    }

    private static void respondAllPlayersReacted(ButtonInteractionEvent event) {
        String gameName = event.getChannel().getName();
        gameName = gameName.replace(ACInfo_Legacy.CARDS_INFO, "");
        gameName = gameName.substring(0, gameName.indexOf("-"));
        Map activeMap = MapManager.getInstance().getMap(gameName);
        String id = event.getButton().getId();
        if (id != null && id.startsWith(Constants.PO_SCORING))
        {
            id = Constants.PO_SCORING;
        }
        if(id != null && (id.startsWith(Constants.SC_FOLLOW) || id.startsWith("sc_no_follow")))
        {
            id = Constants.SC_FOLLOW;
        }
        switch (id) {
            case Constants.SC_FOLLOW, "sc_no_follow", "sc_refresh", "sc_refresh_and_wash", "trade_primary", "sc_ac_draw", "sc_draw_so","sc_trade_follow", "sc_leadership_follow" -> {
                if(activeMap.isFoWMode())
                {
                    event.getInteraction().getMessage().reply("All players have reacted to this Strategy Card").queueAfter(1, TimeUnit.SECONDS);
                }
                else
                {
                    GuildMessageChannel guildMessageChannel = Helper.getThreadChannelIfExists(event);
                    guildMessageChannel.sendMessage("All players have reacted to this Strategy Card").queueAfter(10, TimeUnit.SECONDS);
                    if (guildMessageChannel instanceof ThreadChannel) ((ThreadChannel) guildMessageChannel).getManager().setArchived(true).queueAfter(5, TimeUnit.MINUTES);
                }
            }
            case "no_when" -> {
                event.getInteraction().getMessage().reply("All players have indicated 'No Whens'").queueAfter(1, TimeUnit.SECONDS);
            }
            case "no_after" -> {
                event.getInteraction().getMessage().reply("All players have indicated 'No Afters'").queueAfter(1, TimeUnit.SECONDS);
            }
            case "no_sabotage" -> {
                event.getInteraction().getMessage().reply("All players have indicated 'No Sabotage'").queueAfter(1, TimeUnit.SECONDS);
            }
            case Constants.PO_SCORING, Constants.PO_NO_SCORING, Constants.SO_NO_SCORING -> {
                String message2 = "All players have indicated scoring. Run Status Cleanup and then Draw PO using the buttons.";   
                Button drawStage2= Button.success("reveal_stage_2", "Reveal Stage 2");
                Button drawStage1 = Button.success("reveal_stage_1", "Reveal Stage 1");
                Button runStatusCleanup = Button.primary("run_status_cleanup", "Run Status Cleanup");
                ActionRow actionRow4 = ActionRow.of(List.of(runStatusCleanup,drawStage1, drawStage2));
                MessageCreateBuilder baseMessageObject4 = new MessageCreateBuilder().addContent(message2);
                if (!actionRow4.isEmpty()) baseMessageObject4.addComponents(actionRow4);
                     event.getChannel().sendMessage(baseMessageObject4.build()).queue(message4_ -> {
                });
            }
            case "pass_on_abilities"-> {
                if(activeMap.isCustodiansScored())
                {
                    new RevealAgenda().revealAgenda(event, false, activeMap, event.getChannel());
                }
                else
                {
                    event.getInteraction().getMessage().reply(Helper.getGamePing(event.getGuild(), activeMap) + " All players have indicated completion of status phase. Proceed to Strategy Phase.").queueAfter(1, TimeUnit.SECONDS);
                }

            }
        }
    }
}