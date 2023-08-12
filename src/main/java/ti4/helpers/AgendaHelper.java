package ti4.helpers;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.util.*;
import java.util.Map.Entry;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import ti4.commands.cardsac.ACInfo;
import ti4.commands.cardsso.SOInfo;
import ti4.commands.planet.PlanetExhaust;
import ti4.commands.special.RiseOfMessiah;
import ti4.commands.special.SwordsToPlowsharesTGGain;
import ti4.generator.Mapper;
import ti4.map.Map;
import ti4.map.MapSaveLoadManager;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.UnitHolder;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.AgendaModel;
import ti4.model.PlanetModel;


public class AgendaHelper {

    public static void resolveAgenda(Map activeMap, String buttonID, ButtonInteractionEvent event, MessageChannel actionsChannel) {
        String winner = buttonID.substring(buttonID.indexOf("_") + 1, buttonID.length());
            String agendaid = activeMap.getCurrentAgendaInfo().substring(
                    activeMap.getCurrentAgendaInfo().lastIndexOf("_") + 1, activeMap.getCurrentAgendaInfo().length());
            int aID = 0;
            if (agendaid.equalsIgnoreCase("CL")) {
                String id2 = activeMap.revealAgenda(false);
                LinkedHashMap<String, Integer> discardAgendas = activeMap.getDiscardAgendas();
                AgendaModel agendaDetails = Mapper.getAgenda(id2);
                String agendaName = agendaDetails.getName();
                MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(), "The hidden agenda was " + agendaName
                        + "! You can find it added as a law or in the discard.");
                Integer uniqueID = discardAgendas.get(id2);
                aID = uniqueID;
            } else {
                aID = Integer.parseInt(agendaid);
            }
            LinkedHashMap<String, Integer> discardAgendas = activeMap.getDiscardAgendas();
                String agID ="";
                for (java.util.Map.Entry<String, Integer> agendas : discardAgendas.entrySet()) {
                    if (agendas.getValue().equals(aID)) {
                        agID = agendas.getKey();
                        break;
                    }
                }

            if (activeMap.getCurrentAgendaInfo().startsWith("Law")) {
                if (activeMap.getCurrentAgendaInfo().contains("Player")) {
                    Player player2 = Helper.getPlayerFromColorOrFaction(activeMap, winner);
                    if (player2 != null) {
                        activeMap.addLaw(aID, winner);
                    }
                    MessageHelper.sendMessageToChannel(event.getChannel(),
                            "Added Law with " + winner + " as the elected!");
                } else {
                    if (winner.equalsIgnoreCase("for")) {
                        activeMap.addLaw(aID, null);
                        MessageHelper.sendMessageToChannel(event.getChannel(), Helper.getGamePing(activeMap.getGuild(), activeMap)+" Added law to map!");
                    }
                    if(agID.equalsIgnoreCase("regulations")){
                        if (winner.equalsIgnoreCase("for")) {
                            for(Player playerB : activeMap.getRealPlayers()){
                                if(playerB.getFleetCC() > 4){
                                        playerB.setFleetCC(4);
                                }
                            }
                            MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(), Helper.getGamePing(activeMap.getGuild(), activeMap)+" Reduced people's fleets to 4 if they had more than that");
                        }else{
                            for(Player playerB : activeMap.getRealPlayers()){
                                playerB.setFleetCC(playerB.getFleetCC()+1);
                            }
                            MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(), Helper.getGamePing(activeMap.getGuild(), activeMap)+" Gave everyone 1 extra fleet CC");

                        }   
                    }
                    if (activeMap.getCurrentAgendaInfo().contains("Secret")) {
                        activeMap.addLaw(aID, winner);
                        int soID = 0;
                        String soName = winner;
                        Player playerWithSO = null;

                        for (java.util.Map.Entry<String, Player> playerEntry : activeMap.getPlayers().entrySet()) {
                            Player player_ = playerEntry.getValue();
                            LinkedHashMap<String, Integer> secretsScored = new LinkedHashMap<>(
                                    player_.getSecretsScored());
                            for (java.util.Map.Entry<String, Integer> soEntry : secretsScored.entrySet()) {
                                if (soEntry.getKey().equals(soName)) {
                                    soID = soEntry.getValue();
                                    playerWithSO = player_;
                                    break;
                                }
                            }
                        }

                        if (playerWithSO == null) {
                            MessageHelper.sendMessageToChannel(event.getChannel(), "Player not found");
                            return;
                        }
                        if (soName.isEmpty()) {
                            MessageHelper.sendMessageToChannel(event.getChannel(), "Can make just Scored SO to Public");
                            return;
                        }
                        activeMap.addToSoToPoList(soName);
                        Integer poIndex = activeMap.addCustomPO(soName, 1);
                        activeMap.scorePublicObjective(playerWithSO.getUserID(), poIndex);

                        String sb = "**Public Objective added from Secret:**" + "\n" +
                                "(" + poIndex + ") " + "\n" +
                                Mapper.getSecretObjectivesJustNames().get(soName) + "\n";
                        MessageHelper.sendMessageToChannel(event.getChannel(), sb);

                        SOInfo.sendSecretObjectiveInfo(activeMap, playerWithSO, event);

                    }
                }
            } else {
                if(agID.equalsIgnoreCase("mutiny")){
                    List<Player> winOrLose = null;
                    StringBuilder message = new StringBuilder("");
                    Integer poIndex = 5;
                    if (winner.equalsIgnoreCase("for")) {
                        winOrLose = AgendaHelper.getWinningVoters(winner, activeMap);
                        poIndex = activeMap.addCustomPO("Mutiny", 1);

                    }else{
                        winOrLose = AgendaHelper.getLosingVoters(winner, activeMap);
                        poIndex = activeMap.addCustomPO("Mutiny", -1);
                    }
                    message.append("Custom PO 'Mutiny' has been added.\n");
                    for(Player playerWL : winOrLose){
                        activeMap.scorePublicObjective(playerWL.getUserID(), poIndex);
                        message.append(Helper.getPlayerRepresentation(playerWL, activeMap)).append(" scored 'Mutiny'\n");
                    }
                    MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(), message.toString());     
                }
                if(agID.equalsIgnoreCase("plowshares")){
                    if (winner.equalsIgnoreCase("for")) {
                        for(Player playerB : activeMap.getRealPlayers()){
                            new SwordsToPlowsharesTGGain().doSwords(playerB, event, activeMap);
                        }
                    }else{
                        for(Player playerB : activeMap.getRealPlayers()){
                            new RiseOfMessiah().doRise(playerB, event, activeMap);
                        }
                    }   
                }
                if(agID.equalsIgnoreCase("economic_equality")){
                    int tg = 0;
                    if (winner.equalsIgnoreCase("for")) {
                        for(Player playerB : activeMap.getRealPlayers()){
                            playerB.setTg(5);
                            ButtonHelperFactionSpecific.pillageCheck(playerB, activeMap);
                        }
                        tg = 5;
                    }else{
                         for(Player playerB : activeMap.getRealPlayers()){
                             playerB.setTg(0);
                        }
                    }   
                    MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(), Helper.getGamePing(activeMap.getGuild(), activeMap)+" Set everyone's tgs to "+tg);
                }
                
                if (activeMap.getCurrentAgendaInfo().contains("Law")) {
                    // Figure out law
                }
            }
            String summary = AgendaHelper.getSummaryOfVotes(activeMap, false);
            List<Player> riders = AgendaHelper.getWinningRiders(summary, winner, activeMap, event);
            String ridSum = "People had riders to resolve.";
            for (Player rid : riders) {
                String rep = Helper.getPlayerRepresentation(rid, activeMap, event.getGuild(), true);
                if (rid != null) {
                    String message = "";
                    if (rid.hasAbility("future_sight")) {
                        message = rep
                                + "You have a rider to resolve or you voted for the correct outcome. Either way a tg has been added to your total due to your future sight ability. ("
                                + rid.getTg() + "-->" + (rid.getTg() + 1) + ")";
                        rid.setTg(rid.getTg() + 1);
                        ButtonHelperFactionSpecific.pillageCheck(rid, activeMap);
                    } else {
                        message = rep + "You have a rider to resolve";
                    }
                    if (activeMap.isFoWMode()) {
                        MessageHelper.sendPrivateMessageToPlayer(rid, activeMap, message);
                    } else {
                        MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(), message);
                    }
                }
            }
            if (activeMap.isFoWMode()) {
                MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(),
                        "Sent pings to all those who ridered");
            } else {
                if (riders.size() > 0) {
                    MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(), ridSum);
                }

            }
            String voteMessage = "Resolving vote for " + StringUtils.capitalize(winner)
                    + ". Click the buttons for next steps after you're done resolving riders.";

            Button flipNextAgenda = Button.primary("flip_agenda", "Flip Agenda");
            Button proceedToStrategyPhase = Button.success("proceed_to_strategy",
                    "Proceed to Strategy Phase (will run agenda cleanup and ping speaker)");
            List<Button> resActionRow = List.of(flipNextAgenda, proceedToStrategyPhase);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, resActionRow);

            event.getMessage().delete().queue();
    }

     public static void pingMissingPlayers(Map activeMap) {
        List<Player> missingPlayersWhens = ButtonHelper.getPlayersWhoHaventReacted(activeMap.getLatestWhenMsg(), activeMap);
        List<Player> missingPlayersAfters = ButtonHelper.getPlayersWhoHaventReacted(activeMap.getLatestAfterMsg(), activeMap);
        if(missingPlayersAfters.size() == 0 && missingPlayersWhens.size() == 0){
            return;
        }

        String messageWhens = " please indicate no whens";
        String messageAfters = " please indicate no afters";
        if(activeMap.isFoWMode()){
            for(Player player : missingPlayersWhens){
                MessageHelper.sendMessageToChannel(player.getPrivateChannel(), Helper.getPlayerRepresentation(player, activeMap, activeMap.getGuild(), true) + messageWhens);
            }
            for(Player player : missingPlayersAfters){
                MessageHelper.sendMessageToChannel(player.getPrivateChannel(), Helper.getPlayerRepresentation(player, activeMap, activeMap.getGuild(), true) + messageAfters);
            }
            MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(), "Sent reminder pings to players who have not yet reacted");

        }else{
            for(Player player : missingPlayersWhens){
                messageWhens = Helper.getPlayerRepresentation(player, activeMap, activeMap.getGuild(), true) + messageWhens;
            }
            if(missingPlayersWhens.size()> 0){
                MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(),  messageWhens);
            }
           

            for(Player player : missingPlayersAfters){
                messageAfters = Helper.getPlayerRepresentation(player, activeMap, activeMap.getGuild(), true) + messageAfters;
            }
            if(missingPlayersAfters.size()> 0){
                MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(), messageAfters);
            }
        }
        Date newTime = new Date();
        activeMap.setLastActivePlayerPing(newTime);
     }
    public static void offerVoteAmounts(String buttonID, ButtonInteractionEvent event, Map activeMap, Player player, String ident, String buttonLabel){
        String outcome = buttonID.substring(buttonID.indexOf("_") + 1, buttonID.length());
        String voteMessage = "Chose to vote for " + StringUtils.capitalize(outcome)
                + ". Click buttons for amount of votes";
        activeMap.setLatestOutcomeVotedFor(outcome);
        int maxVotes = getTotalVoteCount(activeMap, player);
        int minVotes = 1;

        if (activeMap.getLaws() != null && (activeMap.getLaws().keySet().contains("rep_govt") || activeMap.getLaws().keySet().contains("absol_government"))) {
                minVotes = 1;
                maxVotes = 1;
        }
        if (maxVotes - minVotes > 20) {
            voteMessage = "Chose to vote for " + StringUtils.capitalize(outcome)
                    + ". You have more votes than discord has buttons. Please further specify your desired vote count by clicking the button which contains your desired vote amount (or largest button).";
        }
        List<Button> voteActionRow = AgendaHelper.getVoteButtons(minVotes, maxVotes);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, voteActionRow);
        event.getMessage().delete().queue();
    }

    public static void exhaustPlanetsForVoting(String buttonID, ButtonInteractionEvent event, Map activeMap, Player player, String ident, String buttonLabel, String finsFactionCheckerPrefix){
        String votes = buttonID.substring(buttonID.indexOf("_") + 1, buttonID.length());
        String voteMessage = "Chose to vote  " + votes + " votes for "
                + StringUtils.capitalize(activeMap.getLatestOutcomeVotedFor())
                + ". Click buttons to choose which planets to exhaust for votes";
        List<Button> voteActionRow = AgendaHelper.getPlanetButtons(event, player, activeMap);
        int allVotes = AgendaHelper.getVoteTotal(event, player, activeMap)[0];
        Button exhausteverything = Button.danger("exhaust_everything_" + allVotes,
                "Exhaust everything (" + allVotes + ")");
        Button concludeExhausting = Button.danger(finsFactionCheckerPrefix + "delete_buttons_" + votes,
                "Done exhausting planets.");
        Button OopsMistake = Button.success("refreshVotes_" + votes,
                "I made a mistake and want to ready some planets");
        voteActionRow.add(exhausteverything);
        voteActionRow.add(concludeExhausting);
        voteActionRow.add(OopsMistake);
        String voteMessage2 = "Exhaust stuff";
        MessageHelper.sendMessageToChannel(event.getChannel(), voteMessage);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage2, voteActionRow);
        event.getMessage().delete().queue();

    }
    public static void exhaustStuffForVoting(String buttonID, ButtonInteractionEvent event, Map activeMap, Player player, String ident, String buttonLabel){
            String planetName = StringUtils.substringAfter(buttonID, "_");
            String votes = StringUtils.substringBetween(buttonLabel, "(", ")");
            if (!buttonID.contains("argent") && !buttonID.contains("blood") && !buttonID.contains("predictive")
                    && !buttonID.contains("everything")) {
                new PlanetExhaust().doAction(player, planetName, activeMap);
            }
            if (buttonID.contains("everything")) {
                for (String planet : player.getPlanets()) {
                    player.exhaustPlanet(planet);
                }
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
            String totalVotesSoFar = event.getMessage().getContentRaw();
            if (!buttonID.contains("argent") && !buttonID.contains("blood") && !buttonID.contains("predictive")
                    && !buttonID.contains("everything")) {

                if (totalVotesSoFar == null || totalVotesSoFar.equalsIgnoreCase("Exhaust stuff")) {
                    totalVotesSoFar = "Total votes exhausted so far: " + votes + "\n Planets exhausted so far are: "
                            + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, activeMap);
                } else {
                    int totalVotes = Integer.parseInt(
                            totalVotesSoFar.substring(totalVotesSoFar.indexOf(":") + 2, totalVotesSoFar.indexOf("\n")))
                            + Integer.parseInt(votes);
                    totalVotesSoFar = totalVotesSoFar.substring(0, totalVotesSoFar.indexOf(":") + 2) + totalVotes
                            + totalVotesSoFar.substring(totalVotesSoFar.indexOf("\n"), totalVotesSoFar.length())
                            + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, activeMap);
                }
                if (actionRow2.size() > 0) {
                    event.getMessage().editMessage(totalVotesSoFar).setComponents(actionRow2).queue();
                }
                // addReaction(event, true, false,"Exhausted
                // "+Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName,
                // activeMap) + " as "+ votes + " votes", "");
            } else {
                if (totalVotesSoFar == null || totalVotesSoFar.equalsIgnoreCase("Exhaust stuff")) {
                    totalVotesSoFar = "Total votes exhausted so far: " + votes
                            + "\n Planets exhausted so far are: all planets";
                } else {
                    int totalVotes = Integer.parseInt(
                            totalVotesSoFar.substring(totalVotesSoFar.indexOf(":") + 2, totalVotesSoFar.indexOf("\n")))
                            + Integer.parseInt(votes);
                    totalVotesSoFar = totalVotesSoFar.substring(0, totalVotesSoFar.indexOf(":") + 2) + totalVotes
                            + totalVotesSoFar.substring(totalVotesSoFar.indexOf("\n"), totalVotesSoFar.length());
                }
                if (actionRow2.size() > 0) {
                    event.getMessage().editMessage(totalVotesSoFar).setComponents(actionRow2).queue();
                }
                if (buttonID.contains("everything")) {
                    // addReaction(event, true, false,"Exhausted
                    // "+Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName,
                    // activeMap) + " as "+ votes + " votes", "");
                    ButtonHelper.addReaction(event, true, false, "Exhausted all planets for " + votes + " votes", "");
                } else {
                    ButtonHelper.addReaction(event, true, false, "Used ability for " + votes + " votes", "");
                }
            }
    }
    public static void resolvingAnAgendaVote(String buttonID, ButtonInteractionEvent event, Map activeMap, Player player){
            boolean resolveTime = false;
            String winner = "";
            String votes = buttonID.substring(buttonID.lastIndexOf("_") + 1, buttonID.length());
            if (!buttonID.contains("outcomeTie*")) {
                if (votes.equalsIgnoreCase("0")) {

                    String pfaction2 = null;
                    if (player != null) {
                        pfaction2 = player.getFaction();
                    }
                    if (pfaction2 != null) {
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(), Helper.getPlayerRepresentation(player, activeMap)+ " Abstained");
                        event.getMessage().delete().queue();
                    } 

                } else {
                    String identifier = "";
                    String outcome = activeMap.getLatestOutcomeVotedFor();
                    if (activeMap.isFoWMode()) {
                        identifier = player.getColor();
                    } else {
                        identifier = player.getFaction();
                    }
                    HashMap<String, String> outcomes = activeMap.getCurrentAgendaVotes();
                    String existingData = outcomes.getOrDefault(outcome, "empty");
                    if (existingData.equalsIgnoreCase("empty")) {

                        existingData = identifier + "_" + votes;
                    } else {
                        existingData = existingData + ";" + identifier + "_" + votes;
                    }
                    activeMap.setCurrentAgendaVote(outcome, existingData);
                    ButtonHelper.addReaction(event, true, true,
                            "Voted " + votes + " votes for " + StringUtils.capitalize(outcome) + "!", "");
                }

                String pFaction1 = StringUtils.capitalize(player.getFaction());
                Button EraseVote = Button.danger("FFCC_" + pFaction1 + "_eraseMyVote",
                        pFaction1 + " Erase Any Of Your Previous Votes");
                List<Button> EraseButton = List.of(EraseVote);
                String mErase = "Use this button if you want to erase your previous votes and vote again";
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), mErase, EraseButton);
                String message = " up to vote! Resolve using buttons.";

                Player nextInLine = AgendaHelper.getNextInLine(player, AgendaHelper.getVotingOrder(activeMap),
                        activeMap);
                String realIdentity2 = Helper.getPlayerRepresentation(nextInLine, activeMap, event.getGuild(), true);

                int[] voteInfo = AgendaHelper.getVoteTotal(event, nextInLine, activeMap);

                while (voteInfo[0] < 1 && !nextInLine.getColor().equalsIgnoreCase(player.getColor())) {
                    String skippedMessage = realIdentity2
                            + "You are being skipped because you either have 0 votes or have ridered";
                    if (activeMap.isFoWMode()) {
                        MessageHelper.sendPrivateMessageToPlayer(nextInLine, activeMap, skippedMessage);
                    } else {
                        MessageHelper.sendMessageToChannel(event.getChannel(), skippedMessage);
                    }
                    player = nextInLine;
                    nextInLine = AgendaHelper.getNextInLine(nextInLine, AgendaHelper.getVotingOrder(activeMap),
                            activeMap);
                    realIdentity2 = Helper.getPlayerRepresentation(nextInLine, activeMap, event.getGuild(), true);
                    voteInfo = AgendaHelper.getVoteTotal(event, nextInLine, activeMap);
                }

                if (!nextInLine.getColor().equalsIgnoreCase(player.getColor())) {
                    String realIdentity = "";
                    realIdentity = Helper.getPlayerRepresentation(nextInLine, activeMap, event.getGuild(), true);
                    String pFaction = StringUtils.capitalize(nextInLine.getFaction());
                    String finChecker = "FFCC_"+nextInLine.getFaction() + "_";
                    Button Vote = Button.success(finChecker+"vote", pFaction + " Choose To Vote");
                    Button Abstain = Button.danger(finChecker+"delete_buttons_0", pFaction + " Choose To Abstain");
                    Button ForcedAbstain = Button.secondary("forceAbstainForPlayer_"+nextInLine.getFaction(),
                            "(For Others) Abstain for this player");
                    activeMap.updateActivePlayer(nextInLine);
                    List<Button> buttons = List.of(Vote, Abstain, ForcedAbstain);
                    if (activeMap.isFoWMode()) {
                        if (nextInLine.getPrivateChannel() != null) {
                            MessageHelper.sendMessageToChannel(nextInLine.getPrivateChannel(),AgendaHelper.getSummaryOfVotes(activeMap, true) + "\n ");
                            MessageHelper.sendMessageToChannelWithButtons(nextInLine.getPrivateChannel(), "\n " + realIdentity+message,
                                    buttons);
                            event.getChannel().sendMessage("Notified next in line").queue();
                        }
                    } else {
                        message = AgendaHelper.getSummaryOfVotes(activeMap, true) + "\n \n " + realIdentity + message;
                        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
                    }
                } else {
                    String summary = AgendaHelper.getSummaryOfVotes(activeMap, false);
                    winner = AgendaHelper.getWinner(summary);
                    if (winner != null && !winner.contains("*")) {
                        resolveTime = true;
                    } else {
                        Player speaker = null;
                        if (activeMap.getPlayer(activeMap.getSpeaker()) != null) {
                            speaker = activeMap.getPlayers().get(activeMap.getSpeaker());
                        } else {
                            speaker = null;
                        }

                        List<Button> tiedWinners = new ArrayList<Button>();
                        if (winner != null) {
                            StringTokenizer winnerInfo = new StringTokenizer(winner, "*");
                            while (winnerInfo.hasMoreTokens()) {
                                String tiedWinner = winnerInfo.nextToken();
                                Button button = Button.primary("delete_buttons_outcomeTie* " + tiedWinner, tiedWinner);
                                tiedWinners.add(button);
                            }
                        } else {

                            tiedWinners = AgendaHelper.getAgendaButtons(null, activeMap, "delete_buttons_outcomeTie*");
                        }
                        if (!tiedWinners.isEmpty()) {
                            MessageChannel channel = null;
                            if (activeMap.isFoWMode()) {
                                channel = speaker == null ? null : speaker.getPrivateChannel();
                                if (channel == null) {
                                    MessageHelper.sendMessageToChannel(event.getChannel(),
                                            "Speaker is not assigned for some reason. Please decide the winner.");
                                }
                            } else {
                                channel = event.getChannel();
                            }
                            MessageHelper.sendMessageToChannelWithButtons(channel,
                                    Helper.getPlayerRepresentation(speaker, activeMap, event.getGuild(), true)
                                            + " please decide the winner.",
                                    tiedWinners);
                        }
                    }
                }
            } else {
                resolveTime = true;
                winner = buttonID.substring(buttonID.lastIndexOf("*") + 2, buttonID.length());
            }
            if (resolveTime) {
                AgendaHelper.resolveTime(event, activeMap, winner);
            }
            if (!votes.equalsIgnoreCase("0")) {
                event.getMessage().delete().queue();
            }
            MapSaveLoadManager.saveMap(activeMap, event);


    }
    public static void resolveTime(GenericInteractionCreateEvent event, Map activeMap, String winner){
        if(winner == null){
            String summary = AgendaHelper.getSummaryOfVotes(activeMap, false);
            winner = AgendaHelper.getWinner(summary);
        }
        List<Player> losers = AgendaHelper.getLosers(winner, activeMap);
        String summary2 = AgendaHelper.getSummaryOfVotes(activeMap, true);
        MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(), summary2 + "\n \n");
        activeMap.setCurrentPhase("agendaEnd");
        activeMap.setActivePlayer(null);
        String resMessage = "Please hold while people resolve shenanigans. " + losers.size()
                + " players have the opportunity to play deadly plot.";
        if ((!activeMap.isACInDiscard("Bribery") || !activeMap.isACInDiscard("Deadly Plot"))
                && (losers.size() > 0 || activeMap.isAbsolMode())) {
            Button noDeadly = Button.primary("genericReact1", "No Deadly Plot");
            Button noBribery = Button.primary("genericReact2", "No Bribery");
            List<Button> deadlyActionRow = List.of(noBribery, noDeadly);

            MessageHelper.sendMessageToChannelWithButtons(activeMap.getMainGameChannel(), resMessage,
                    deadlyActionRow);
            if (!activeMap.isFoWMode()) {
                String loseMessage = "";
                for (Player los : losers) {
                    if (los != null) {
                        loseMessage = loseMessage
                                + Helper.getPlayerRepresentation(los, activeMap, event.getGuild(), true);
                    }
                }
                event.getMessageChannel().sendMessage(loseMessage + " Please respond to bribery/deadly plot window")
                        .queue();
            } else {
                MessageHelper.privatelyPingPlayerList(losers, activeMap,
                        "Please respond to bribery/deadly plot window");
            }
        } else {
            String messageShen = "Either both bribery and deadly plot were in the discard or noone could legally play them.";
            
            if (activeMap.getCurrentAgendaInfo().contains("Elect Player")&& (!activeMap.isACInDiscard("Confounding") || !activeMap.isACInDiscard("Confusing"))){

            } else{
                messageShen = messageShen + " There are no shenanigans possible. Please resolve the agenda. ";
            }
            activeMap.getMainGameChannel().sendMessage(messageShen).queue();
        }
        if (activeMap.getCurrentAgendaInfo().contains("Elect Player")
                && (!activeMap.isACInDiscard("Confounding") || !activeMap.isACInDiscard("Confusing"))) {
            String resMessage2 = Helper.getGamePing(activeMap.getGuild(), activeMap)
                    + " please react to no confusing/confounding";
            Button noConfounding = Button.primary("genericReact3", "Refuse Confounding Legal Text");
            Button noConfusing = Button.primary("genericReact4", "Refuse Confusing Legal Text");
            List<Button> buttons = List.of(noConfounding, noConfusing);
            MessageHelper.sendMessageToChannelWithButtons(activeMap.getMainGameChannel(), resMessage2, buttons);

        } else {
            if (activeMap.getCurrentAgendaInfo().contains("Elect Player")) {
                activeMap.getMainGameChannel()
                        .sendMessage("Both confounding and confusing are in the discard pile. ").queue();

            }
        }

        String resMessage3 = "Current winner is " + StringUtils.capitalize(winner) + ". "
                + Helper.getGamePing(activeMap.getGuild(), activeMap)
                + "When shenanigans have concluded, please confirm resolution or discard the result and manually resolve it yourselves.";
        Button autoResolve = Button.primary("agendaResolution_" + winner, "Resolve with current winner");
        Button manualResolve = Button.danger("autoresolve_manual", "Resolve it Manually");
        List<Button> deadlyActionRow3 = List.of(autoResolve, manualResolve);
        MessageHelper.sendMessageToChannelWithButtons(activeMap.getMainGameChannel(), resMessage3,
                deadlyActionRow3);

    }

    public static void reverseRider(String buttonID, ButtonInteractionEvent event, Map activeMap, Player player, String ident){
        String choice = buttonID.substring(buttonID.indexOf("_") + 1, buttonID.length());
        String voteMessage = "Chose to reverse latest rider on " + choice;
        HashMap<String, String> outcomes = activeMap.getCurrentAgendaVotes();
        String existingData = outcomes.getOrDefault(choice, "empty");
        if (existingData.equalsIgnoreCase("empty")) {
            voteMessage = "Something went wrong. Ping Fin and continue onwards.";
        } else {
            int lastSemicolon = existingData.lastIndexOf(";");
            if (lastSemicolon < 0) {
                existingData = "";
            } else {
                existingData = existingData.substring(0, lastSemicolon);
            }
        }
        activeMap.setCurrentAgendaVote(choice, existingData);
        event.getChannel().sendMessage(voteMessage).queue();
        event.getMessage().delete().queue();
    }


     public static void placeRider(String buttonID, ButtonInteractionEvent event, Map activeMap, Player player, String ident){
        String[] choiceParams = buttonID.substring(buttonID.indexOf("_") + 1, buttonID.lastIndexOf("_")).split(";");
        String choiceType = choiceParams[0];
        String choice = choiceParams[1];

        String rider = buttonID.substring(buttonID.lastIndexOf("_") + 1, buttonID.length());
        String agendaDetails = activeMap.getCurrentAgendaInfo();
        agendaDetails = agendaDetails.substring(agendaDetails.indexOf("_")+1, agendaDetails.length());
       // if(activeMap)
       String cleanedChoice = choice;
       if(agendaDetails.contains("Planet") || agendaDetails.contains("planet")){
            cleanedChoice = Helper.getPlanetRepresentation(choice, activeMap);
       }
        String voteMessage = "Chose to put a " + rider + " on " + StringUtils.capitalize(cleanedChoice);
        String identifier = "";
        if (activeMap.isFoWMode()) {
            identifier = player.getColor();
        } else {
            identifier = player.getFaction();
        }
        HashMap<String, String> outcomes = activeMap.getCurrentAgendaVotes();
        String existingData = outcomes.getOrDefault(choice, "empty");
        if (existingData.equalsIgnoreCase("empty")) {
            existingData = identifier + "_" + rider;
        } else {
            existingData = existingData + ";" + identifier + "_" + rider;
        }
        activeMap.setCurrentAgendaVote(choice, existingData);

        if (!rider.equalsIgnoreCase("Non-AC Rider") && !rider.equalsIgnoreCase("Keleres Rider")
                && !rider.equalsIgnoreCase("Keleres Xxcha Hero")
                && !rider.equalsIgnoreCase("Galactic Threat Rider")) {
            List<Button> voteActionRow = new ArrayList<Button>();
            Button concludeExhausting = Button.danger("reverse_" + choice, "Click this if the rider is sabod");
            voteActionRow.add(concludeExhausting);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, voteActionRow);
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), voteMessage);
        }
        event.getMessage().delete().queue();
     }

     public static List<Button> getWhenButtons(Map activeMap) {
        Button playWhen = Button.danger("play_when", "Play When");
        Button noWhen = Button.primary("no_when", "No Whens")
                .withEmoji(Emoji.fromFormatted(Emojis.noafters));
        Button noWhenPersistent = Button
                .primary("no_when_persistent", "No Whens No Matter What (for this agenda)")
                .withEmoji(Emoji.fromFormatted(Emojis.noafters));
        List<Button> whenButtons = new ArrayList<>(List.of(playWhen, noWhen, noWhenPersistent));
        Player quasher = Helper.getPlayerFromAbility(activeMap, "quash");
        if(quasher != null && quasher.getStrategicCC()>0){
             String finChecker = "FFCC_"+quasher.getFaction() + "_";
            Button quashButton = Button.danger(finChecker+"quash", "Quash Agenda").withEmoji(Emoji.fromFormatted(Emojis.Xxcha));
            if(activeMap.isFoWMode()){
                List<Button> quashButtons = new ArrayList<>(List.of(quashButton));
                MessageHelper.sendMessageToChannelWithButtons(quasher.getPrivateChannel(), "Use Button To Quash If You Want", quashButtons);
            }else{
                whenButtons.add(quashButton);
            }
        }
        return whenButtons;
     }
    public static List<Button> getAfterButtons(Map activeMap) {
        List<Button> afterButtons = new ArrayList<Button>();
        Button playAfter = Button.danger("play_after_Non-AC Rider", "Play A Non-AC Rider");
        afterButtons.add(playAfter);

        if(Helper.getPlayerFromColorOrFaction(activeMap,"keleres") != null && !activeMap.isFoWMode())
        {
            Button playKeleresAfter = Button.secondary("play_after_Keleres Rider", "Play Keleres Rider").withEmoji(Emoji.fromFormatted(Helper.getFactionIconFromDiscord("keleres")));;
            afterButtons.add(playKeleresAfter);
        }
        if(Helper.getPlayerFromAbility(activeMap, "galactic_threat") != null && !activeMap.isFoWMode())
        {
            Player nekroProbably = Helper.getPlayerFromAbility(activeMap, "galactic_threat");
            String finChecker = "FFCC_"+nekroProbably.getFaction() + "_";
            Button playNekroAfter = Button.secondary(finChecker+"play_after_Galactic Threat Rider", "Do Galactic Threat Rider").withEmoji(Emoji.fromFormatted(Helper.getFactionIconFromDiscord("nekro")));
            afterButtons.add(playNekroAfter);
        }
        if(Helper.getPlayerFromUnlockedLeader(activeMap, "keleresheroodlynn") != null)
        {
            Player keleresX = Helper.getPlayerFromUnlockedLeader(activeMap, "keleresheroodlynn");
            String finChecker = "FFCC_"+keleresX.getFaction() + "_";
            Button playKeleresHero = Button.secondary(finChecker+"play_after_Keleres Xxcha Hero", "Play Keleres Hero").withEmoji(Emoji.fromFormatted(Helper.getFactionIconFromDiscord("keleres")));
            afterButtons.add(playKeleresHero);
        }

        Button noAfter = Button.primary("no_after", "No Afters")
                .withEmoji(Emoji.fromFormatted(Emojis.noafters));
        afterButtons.add(noAfter);
        Button noAfterPersistent = Button
                .primary("no_after_persistent", "No Afters No Matter What (for this agenda)")
                .withEmoji(Emoji.fromFormatted(Emojis.noafters));
        afterButtons.add(noAfterPersistent);
        
        return afterButtons;
    }
    public static List<Button> getVoteButtons(int minVote, int voteTotal) {
        List<Button> voteButtons = new ArrayList<>();

        if (voteTotal-minVote > 20) {
            for (int x = 10; x < voteTotal+10; x += 10) {
                int y = x-9;
                int z = x;
                if (x > voteTotal) {
                    z = voteTotal;
                    y = z-9;
                }
                Button button = Button.secondary("fixerVotes_"+z, y+"-"+z);
                voteButtons.add(button);
            }
        } else {
            for (int x = minVote; x < voteTotal+1; x++) {
                Button button = Button.secondary("votes_"+x, ""+x);
                voteButtons.add(button);
            }
            Button button = Button.danger("distinguished_"+voteTotal, "Press Me For +5 Vote Options");
            voteButtons.add(button);
        }
        return voteButtons;
    }

    public static List<Button> getForAgainstOutcomeButtons(String rider, String prefix) {
        List<Button> voteButtons = new ArrayList<>();
        Button button = null;
        Button button2 = null;
        if (rider == null) {
            button = Button.secondary(prefix+"_for", "For");
            button2 = Button.danger(prefix+"_against", "Against");
        } else {
            button = Button.primary("rider_fa;for_"+rider, "For");
            button2 = Button.danger("rider_fa;against_"+rider, "Against");
        }
        voteButtons.add(button);
        voteButtons.add(button2);
        return voteButtons;
    }

    public static void startTheVoting(Map activeMap, GenericInteractionCreateEvent event) {
        activeMap.setCurrentPhase("agendaVoting");
        if (activeMap.getCurrentAgendaInfo() != null) {
            String message = " up to vote! Resolve using buttons. \n \n" + AgendaHelper.getSummaryOfVotes(activeMap, true);
            
            Player nextInLine = null;
             try {
                    nextInLine = AgendaHelper.getNextInLine(null, AgendaHelper.getVotingOrder(activeMap), activeMap);
                } catch (Exception e) {
                    BotLogger.log(event, "Could not find next in line", e);
                }
            String realIdentity = Helper.getPlayerRepresentation(nextInLine, activeMap, event.getGuild(), true);
            int[] voteInfo = AgendaHelper.getVoteTotal(event, nextInLine, activeMap);
            if (nextInLine == null) {
                return;
            }
            int counter = 0;
            while (voteInfo[0] < 1 && counter < 10) {
                String skippedMessage = realIdentity+"You are being skipped because you either have 0 votes or have ridered";
                if (activeMap.isFoWMode()) {
                    MessageHelper.sendPrivateMessageToPlayer(nextInLine, activeMap, skippedMessage);
                } else {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(),skippedMessage);
                }
                nextInLine = AgendaHelper.getNextInLine(nextInLine, AgendaHelper.getVotingOrder(activeMap), activeMap);
                realIdentity = Helper.getPlayerRepresentation(nextInLine, activeMap, event.getGuild(), true);
                voteInfo = AgendaHelper.getVoteTotal(event, nextInLine, activeMap);
                counter = counter + 1;
            }

            String pFaction = StringUtils.capitalize(nextInLine.getFaction());
            message = realIdentity + message;
            String finChecker = "FFCC_"+nextInLine.getFaction() + "_";
            Button Vote= Button.success(finChecker+"vote", pFaction+" Choose To Vote");
            Button Abstain = Button.danger(finChecker+"delete_buttons_0", pFaction+" Choose To Abstain");
            Button ForcedAbstain = Button.secondary("forceAbstainForPlayer_"+nextInLine.getFaction(),
                            "(For Others) Abstain for this player");
            try {
                    activeMap.updateActivePlayer(nextInLine);
                } catch (Exception e) {
                    BotLogger.log(event, "Could not update active player", e);
                }
            
            List<Button> buttons = List.of(Vote, Abstain, ForcedAbstain);
            if (activeMap.isFoWMode()) {
                if (nextInLine.getPrivateChannel() != null) {
                    MessageHelper.sendMessageToChannelWithButtons(nextInLine.getPrivateChannel(), message, buttons);
                    event.getMessageChannel().sendMessage("Voting started. Notified first in line").queue();
                }
            } else {
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel)event.getChannel(), message, buttons);
            }
        } else {
            event.getMessageChannel().sendMessage("Cannot find voting info, sorry. Please resolve automatically").queue();
        }
    }

    public static List<Button> getLawOutcomeButtons(Map activeMap, String rider, String prefix) {
        List<Button> lawButtons = new ArrayList<>();
        for (java.util.Map.Entry<String, Integer> law : activeMap.getLaws().entrySet()) {
            Button button = null;
            if (rider == null) {
                button = Button.secondary(prefix+"_"+law.getKey(), law.getKey());
            } else {
                button = Button.secondary(prefix+"rider_law;"+law.getKey()+"_"+rider, law.getKey());
            }
            lawButtons.add(button);
        }
        return lawButtons;
    }

    public static List<Button> getSecretOutcomeButtons(Map activeMap, String rider, String prefix) {
        List<Button> secretButtons = new ArrayList<>();
        for (Player player :  activeMap.getPlayers().values()) {
            for (java.util.Map.Entry<String, Integer> so : player.getSecretsScored().entrySet()) {
                Button button = null;
                String soName = Mapper.getSecretObjectivesJustNames().get(so.getKey());
                if (rider == null) {
                    
                    button = Button.secondary(prefix+"_"+so.getKey(), soName);
                } else {
                    button = Button.secondary(prefix+"rider_so;"+so.getKey()+"_"+rider, soName);
                }
                secretButtons.add(button);
            }
        }
        return secretButtons;
    }

    public static List<Button> getStrategyOutcomeButtons(String rider, String prefix) {
        List<Button> strategyButtons = new ArrayList<>();
        for (int x = 1; x < 9; x++) {
            Button button = null;
            if (rider == null) {
                button = Button.secondary(prefix+"_"+x, x+"");
            } else {
                button = Button.secondary(prefix+"rider_sc;"+x+"_"+rider, x+"");
            }
            strategyButtons.add(button);
        }
        return strategyButtons;
    }

    public static List<Button> getPlanetOutcomeButtons(GenericInteractionCreateEvent event, Player player, Map activeMap, String prefix, String rider) {
        List<Button> planetOutcomeButtons = new ArrayList<>();
        List<String> planets = new ArrayList<>(player.getPlanets());
        for (String planet : planets) {
            Button button = null;
            if (rider == null) {
                button = Button.secondary(prefix+"_"+planet, Helper.getPlanetRepresentation(planet, activeMap));
            } else {
                button = Button.secondary(prefix+"rider_planet;"+planet+"_"+rider, Helper.getPlanetRepresentation(planet, activeMap));
            }
            planetOutcomeButtons.add(button);
        }
        return planetOutcomeButtons;
    }


    public static List<Button> getPlayerOutcomeButtons(Map activeMap, String rider, String prefix, String planetRes) {
        List<Button> playerOutcomeButtons = new ArrayList<>();

        for (Player player : activeMap.getPlayers().values()) {
            if (player.isRealPlayer()) {
                String faction = player.getFaction();
                if (faction != null && Mapper.isFaction(faction)) {
                    Button button = null;
                    if (!activeMap.isFoWMode()) {
                        if (rider != null) {
                            if(planetRes != null)
                            {
                                button = Button.secondary(planetRes+"_"+faction+"_"+rider, " ");
                            }
                            else
                            {
                                button = Button.secondary(prefix+"rider_player;"+faction+"_"+rider, " ");
                            }
                           
                        } else {
                            button = Button.secondary(prefix+"_"+faction, " ");
                        }
                        String factionEmojiString = Helper.getFactionIconFromDiscord(faction);
                        button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                    } else {
                        if (rider != null) {
                            if(planetRes != null)
                            {
                                button = Button.secondary(planetRes+"_"+player.getColor()+"_"+rider, " ");
                            }
                            else
                            {
                                 button = Button.secondary(prefix+"rider_player;"+player.getColor()+"_"+rider, player.getColor());
                            }
                        } else {
                            button = Button.secondary(prefix+"_"+player.getColor(), player.getColor());
                        }
                    }
                    playerOutcomeButtons.add(button);
                }
            }
        }
        return playerOutcomeButtons;
    }



    public static List<Button> getAgendaButtons(String ridername, Map activeMap, String prefix) {
        String agendaDetails = activeMap.getCurrentAgendaInfo();
        agendaDetails = agendaDetails.substring(agendaDetails.indexOf("_")+1, agendaDetails.length());
        List<Button> outcomeActionRow = null;
        if (agendaDetails.contains("For")) {
            outcomeActionRow = getForAgainstOutcomeButtons(ridername, prefix);
        }
        else if (agendaDetails.contains("Player") || agendaDetails.contains("player")) {
            outcomeActionRow = getPlayerOutcomeButtons(activeMap, ridername, prefix, null);
        }
        else if (agendaDetails.contains("Planet") || agendaDetails.contains("planet")) {
            if(ridername == null)
            {
                outcomeActionRow = getPlayerOutcomeButtons(activeMap, ridername, "tiedPlanets_"+prefix, "planetRider");
            }
            else
            {
                outcomeActionRow = getPlayerOutcomeButtons(activeMap, ridername, prefix, "planetRider");
            }
        }
        else if (agendaDetails.contains("Secret") || agendaDetails.contains("secret")) {
            outcomeActionRow = getSecretOutcomeButtons(activeMap, ridername, prefix);
        }
        else if (agendaDetails.contains("Strategy") || agendaDetails.contains("strategy")) {
            outcomeActionRow = getStrategyOutcomeButtons(ridername, prefix);
        } else {
            outcomeActionRow = getLawOutcomeButtons(activeMap, ridername, prefix);
        }

        return outcomeActionRow;

    }

    public static List<Player> getWinningRiders(String summary, String winner, Map activeMap, GenericInteractionCreateEvent event) {
        List<Player> winningRs = new ArrayList<Player>();
        HashMap<String, String> outcomes = activeMap.getCurrentAgendaVotes();

        for (String outcome : outcomes.keySet()) {
            if (outcome.equalsIgnoreCase(winner)) {
                StringTokenizer vote_info = new StringTokenizer(outcomes.get(outcome), ";");
                while (vote_info.hasMoreTokens()) {
                    String specificVote = vote_info.nextToken();
                    String faction = specificVote.substring(0, specificVote.indexOf("_"));
                    Player winningR = Helper.getPlayerFromColorOrFaction(activeMap, faction.toLowerCase());

                    if (winningR != null && (specificVote.contains("Rider") || winningR.hasAbility("future_sight"))) {

                        MessageChannel channel = ButtonHelper.getCorrectChannel(winningR, activeMap);
                        String identity = Helper.getPlayerRepresentation(winningR, activeMap, activeMap.getGuild(), true);
                        if(specificVote.contains("Galactic Threat Rider")){
                            List<Player> voters = AgendaHelper.getWinningVoters(winner, activeMap);
                            List<String> potentialTech = new ArrayList<String>();
                            for(Player techGiver : voters){
                                potentialTech = ButtonHelperFactionSpecific.getPossibleTechForNekroToGainFromPlayer(winningR, techGiver, potentialTech, activeMap);
                            }
                            MessageHelper.sendMessageToChannelWithButtons(channel, identity+" resolve Galactic Threat Rider using the buttons", ButtonHelperFactionSpecific.getButtonsForPossibleTechForNekro(winningR, potentialTech, activeMap));
                        }
                        if(specificVote.contains("Technology Rider") && !winningR.hasAbility("technological_singularity")){
                            activeMap.setComponentAction(true);
                            Button getTech = Button.success("acquireATech", "Get a tech");
                            List<Button> buttons = new ArrayList();
                            buttons.add(getTech);
                            MessageHelper.sendMessageToChannelWithButtons(channel, identity+" resolve Technology Rider by using the button to get a tech", buttons);
                        }
                        if(specificVote.contains("Leadership Rider") || (specificVote.contains("Technology Rider") && winningR.hasAbility("technological_singularity"))){
                            Button getTactic= Button.success("increase_tactic_cc", "Gain 1 Tactic CC");
                            Button getFleet = Button.success("increase_fleet_cc", "Gain 1 Fleet CC");
                            Button getStrat= Button.success("increase_strategy_cc", "Gain 1 Strategy CC");
                            Button DoneGainingCC = Button.danger("deleteButtons", "Done Gaining CCs");
                            List<Button> buttons = List.of(getTactic, getFleet, getStrat, DoneGainingCC);
                            String message = identity + "! Your current CCs are " + Helper.getPlayerCCs(winningR)
                            + ". Use buttons to gain CCs";
                            MessageHelper.sendMessageToChannel(channel, identity+" resolve rider by using the button to get 3 command counters");
                            MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
                        }
                        if(specificVote.contains("Keleres Rider")){
                            int cTG = winningR.getTg();
                            winningR.setTg(cTG+2);
                            activeMap.drawActionCard(winningR.getUserID());
                            ButtonHelper.checkACLimit(activeMap, event, winningR);
                            ACInfo.sendActionCardInfo(activeMap, winningR, event);
                            MessageHelper.sendMessageToChannel(channel, identity+" due to having a winning Keleres Rider, you have been given an AC and 2 tg ("+cTG+"->"+winningR.getTg()+")");
                            ButtonHelperFactionSpecific.pillageCheck(winningR, activeMap);
                        }
                        if(specificVote.contains("Politics Rider")){
                            int amount = 3;
                            if(winningR.hasAbility("scheming")){
                                amount = 4;
                                activeMap.drawActionCard(winningR.getUserID());
                            }
                            activeMap.drawActionCard(winningR.getUserID());
                            activeMap.drawActionCard(winningR.getUserID());
                            activeMap.drawActionCard(winningR.getUserID());
                            ButtonHelper.checkACLimit(activeMap, event, winningR);
                            ACInfo.sendActionCardInfo(activeMap, winningR, event);
                            activeMap.setSpeaker(winningR.getUserID());
                            MessageHelper.sendMessageToChannel(channel, identity+" due to having a winning Politics Rider, you have been given "+amount+" AC and the speaker token");
                            ButtonHelperFactionSpecific.pillageCheck(winningR, activeMap);
                        }
                        if(specificVote.contains("Diplomacy Rider")){
                            String message = identity + " You have a diplo rider to resolve. Click the name of the planet who's system you wish to diplo";
                            List<Button> buttons = Helper.getPlanetSystemDiploButtons(event, winningR, activeMap, true);
                            MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
                        }
                        if(specificVote.contains("Construction Rider")){
                            String message = identity + " You have a construction rider to resolve. Click the name of the planet you wish to put your space dock on";
                            List<Button> buttons = Helper.getPlanetPlaceUnitButtons(winningR, activeMap, "sd", "place");
                            MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
                        }
                        if(specificVote.contains("Warfare Rider")){
                            String message = identity + " You have a warfare rider to resolve. Select the system to put the dread";
                            List<Button> buttons = Helper.getTileWithShipsPlaceUnitButtons(winningR, activeMap, "dreadnought", "placeOneNDone_skipbuild");
                            MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
                        }
                        if(specificVote.contains("Trade Rider")){
                            int cTG = winningR.getTg();
                            winningR.setTg(cTG+5);
                            MessageHelper.sendMessageToChannel(channel, identity+" due to having a winning Trade Rider, you have been given 5 tg ("+cTG+"->"+winningR.getTg()+")");
                            ButtonHelperFactionSpecific.pillageCheck(winningR, activeMap);
                        }
                        if(specificVote.contains("Imperial Rider")){
                            String msg =  identity+" due to having a winning Imperial Rider, you have scored a pt\n";
                            int poIndex = 5;
                            poIndex = activeMap.addCustomPO("Imperial Rider", 1);
                            msg = msg+ "Custom PO 'Imperial Rider' has been added.\n";
                            activeMap.scorePublicObjective(winningR.getUserID(), poIndex);
                            msg = msg + Helper.getPlayerRepresentation(winningR, activeMap)+" scored 'Imperial Rider'\n";
                            MessageHelper.sendMessageToChannel(channel,msg);
                        }
                        if(!winningRs.contains(winningR))
                        {
                            winningRs.add(winningR);
                        }
                        

                    }

                }
            }
        }
        return winningRs;
    }

    public static List<Player> getRiders(Map activeMap) {
        List<Player> riders = new ArrayList<Player>();

        HashMap<String, String> outcomes = activeMap.getCurrentAgendaVotes();

        for (String outcome : outcomes.keySet()) {
            StringTokenizer vote_info = new StringTokenizer(outcomes.get(outcome), ";");

            while (vote_info.hasMoreTokens()) {
                String specificVote = vote_info.nextToken();
                String faction = specificVote.substring(0, specificVote.indexOf("_"));
                String vote = specificVote.substring(specificVote.indexOf("_")+1,specificVote.length());
                if (vote.contains("Rider") || vote.contains("Sanction")) {
                    Player rider = Helper.getPlayerFromColorOrFaction(activeMap, faction.toLowerCase());
                    if (rider != null) {
                        riders.add(rider);
                    }
                }

            }

        }
        return riders;
    }


    public static List<Player> getLosers(String winner, Map activeMap) {
        List<Player> losers = new ArrayList<Player>();
        HashMap<String, String> outcomes = activeMap.getCurrentAgendaVotes();

        for (String outcome : outcomes.keySet()) {
            if (!outcome.equalsIgnoreCase(winner)) {
                StringTokenizer vote_info = new StringTokenizer(outcomes.get(outcome), ";");

                while (vote_info.hasMoreTokens()) {
                    String specificVote = vote_info.nextToken();
                    String faction = specificVote.substring(0, specificVote.indexOf("_"));
                    Player loser = Helper.getPlayerFromColorOrFaction(activeMap, faction.toLowerCase());
                    if (loser != null) {
                        if (!losers.contains(loser)) {
                            losers.add(loser);
                        }

                    }
                }
            }
        }
        return losers;
    }
    public static List<Player> getWinningVoters(String winner, Map activeMap) {
        List<Player> losers = new ArrayList<Player>();
        HashMap<String, String> outcomes = activeMap.getCurrentAgendaVotes();

        for (String outcome : outcomes.keySet()) {
            if (outcome.equalsIgnoreCase(winner)) {
                StringTokenizer vote_info = new StringTokenizer(outcomes.get(outcome), ";");

                while (vote_info.hasMoreTokens()) {
                    String specificVote = vote_info.nextToken();
                    String faction = specificVote.substring(0, specificVote.indexOf("_"));
                    Player loser = Helper.getPlayerFromColorOrFaction(activeMap, faction.toLowerCase());
                    if (loser != null && !specificVote.contains("Rider")&& !specificVote.contains("Sanction")) {
                        if (!losers.contains(loser)) {
                            losers.add(loser);
                        }

                    }
                }
            }
        }
        return losers;
    }
    public static List<Player> getLosingVoters(String winner, Map activeMap) {
        List<Player> losers = new ArrayList<Player>();
        HashMap<String, String> outcomes = activeMap.getCurrentAgendaVotes();

        for (String outcome : outcomes.keySet()) {
            if (!outcome.equalsIgnoreCase(winner)) {
                StringTokenizer vote_info = new StringTokenizer(outcomes.get(outcome), ";");

                while (vote_info.hasMoreTokens()) {
                    String specificVote = vote_info.nextToken();
                    String faction = specificVote.substring(0, specificVote.indexOf("_"));
                    Player loser = Helper.getPlayerFromColorOrFaction(activeMap, faction.toLowerCase());
                    if (loser != null) {
                        if (!losers.contains(loser) && !specificVote.contains("Rider")&& !specificVote.contains("Sanction")) {
                            losers.add(loser);
                        }

                    }
                }
            }
        }
        return losers;
    }

    public static int[] getVoteTotal(GenericInteractionCreateEvent event, Player player, Map activeMap) {
        int hasXxchaAlliance = activeMap.playerHasLeaderUnlockedOrAlliance(player, "xxchacommander") ? 1 : 0;
        int hasXxchaHero = player.hasLeaderUnlocked("xxchahero") ? 1 : 0;
        int voteCount = getTotalVoteCount(activeMap, player);

        //Check if Player only has additional votes but not any "normal" votes, if so, they can't vote
        if (getVoteCountFromPlanets(activeMap, player) == 0) {
            voteCount = 0;
        }

        if (activeMap.getLaws() != null && (activeMap.getLaws().keySet().contains("rep_govt") || activeMap.getLaws().keySet().contains("absol_government"))) {
            voteCount = 1;
        }

        if (player.getFaction().equals("nekro") && hasXxchaAlliance == 0) {
            voteCount = 0;
        }
        List<Player> riders = getRiders(activeMap);
        if (riders.indexOf(player) > -1) {
            if (hasXxchaAlliance == 0) {
                voteCount = 0;
            }
        }

        int[] voteArray = {voteCount, hasXxchaHero, hasXxchaAlliance};
        return voteArray;
    }

    public static List<Player> getVotingOrder(Map activeMap) {
        List<Player> orderList = new ArrayList<>();
        orderList.addAll(activeMap.getPlayers().values().stream()
                .filter(p -> p.isRealPlayer())
                .toList());
        String speakerName = activeMap.getSpeaker();
        Optional<Player> optSpeaker = orderList.stream()
                .filter(player -> player.getUserID().equals(speakerName))
                .findFirst();

        if (optSpeaker.isPresent()) {
            int rotationDistance = orderList.size() - orderList.indexOf(optSpeaker.get()) - 1;
            Collections.rotate(orderList, rotationDistance);
        }
        if(activeMap.isReverseSpeakerOrder()) {
            Collections.reverse(orderList);
            if (optSpeaker.isPresent()) {
                int rotationDistance = orderList.size() - orderList.indexOf(optSpeaker.get()) - 1;
                Collections.rotate(orderList, rotationDistance);
            }
        }
        if (activeMap.getHackElectionStatus()) {
            Collections.reverse(orderList);
            if (optSpeaker.isPresent()) {
                int rotationDistance = orderList.size() - orderList.indexOf(optSpeaker.get()) - 1;
                Collections.rotate(orderList, rotationDistance);
            }
        }

        //Check if Argent Flight is in the game - if it is, put it at the front of the vote list.
        Optional<Player> argentPlayer = orderList.stream().filter(player -> player.getFaction() != null && player.hasAbility("zeal")).findFirst();
        if (argentPlayer.isPresent()) {
            orderList.remove(argentPlayer.orElse(null));
            orderList.add(0, argentPlayer.get());
        }

        //Check if Player has Edyn Mandate faction tech - if it is, put it at the end of the vote list.
        Optional<Player> edynPlayer = orderList.stream().filter(player -> player.getFaction() != null && player.hasTech("dsedyny")).findFirst();
        if (edynPlayer.isPresent()) {
            orderList.remove(edynPlayer.orElse(null));
            orderList.add(edynPlayer.get());
        }
        return orderList;
    }

    public static Player getNextInLine(Player player1, List<Player> votingOrder, Map activeMap) {
        boolean foundPlayer = false;
        if (player1 == null) {
            for (int x = 0; x < 6; x++) {
                if (x < votingOrder.size()) {
                    Player player = votingOrder.get(x);
                    if (player != null && !player.isDummy() && player.isRealPlayer()) {
                        return player;
                    }
                }

            }
            return null;

        }
        for (Player player2 : votingOrder) {
            if (player2 == null || player2.isDummy()) {
                continue;
            }
            if (foundPlayer && player2.isRealPlayer()) {
                return player2;
            }
            if (player1.getColor().equalsIgnoreCase(player2.getColor())) {
                foundPlayer = true;
            }
        }

        return player1;
    }


    public static List<Button> getPlanetButtons(GenericInteractionCreateEvent event, Player player, Map activeMap) {
        List<Button> planetButtons = new ArrayList<>();
        List<String> planets = new ArrayList<>(player.getReadiedPlanets());
        int[] voteInfo = getVoteTotal(event, player, activeMap);
        HashMap<String, UnitHolder> planetsInfo = activeMap.getPlanetsInfo();
        for (String planet : planets) {
            PlanetModel planetModel = Mapper.getPlanet(planet);
            int voteAmount = 0;
            Planet p = (Planet) planetsInfo.get(planet);
            if(p == null){
                continue;
            }
            voteAmount += p.getInfluence();
            if (voteInfo[2] != 0) {
                voteAmount+=1;
            }
            if (voteInfo[1] != 0) {
                voteAmount+=p.getResources();
            }
            String planetNameProper = planet;
            if(planetModel.getName() != null) {
                planetNameProper = planetModel.getName();
            } else {
                BotLogger.log(event.getChannel().getAsMention() + " TEMP BOTLOG: A bad PlanetModel was found for planet: " + planet + " - using the planet id instead of the model name");
            }

            if (voteAmount != 0) {
                Emoji emoji = Emoji.fromFormatted(Helper.getPlanetEmoji(planet));
                if (Emojis.SemLor.equals(Helper.getPlanetEmoji(planet)) || emoji == null) {
                    Button button = Button.secondary("exhaust_" + planet, planetNameProper + " ("+voteAmount+")");
                    planetButtons.add(button);
                } else {
                    Button button = Button.secondary("exhaust_" + planet, planetNameProper + " ("+voteAmount+")").withEmoji(emoji);
                    planetButtons.add(button);
                }
            }
        }

        // //TODO: Use ListVoteCount.getAdditionalVotesFromOtherSources to build these buttons
        // java.util.Map<String, Integer> additionalVotes = ListVoteCount.getAdditionalVotesFromOtherSources(activeMap, player);
        // for (java.util.Map.Entry<String, Integer> entry : additionalVotes.entrySet()) {
        //     if (entry.getValue() > 0) {
        //         Button button = Button.primary("use_additional_generic_votes_" + entry.getKey().replaceAll(" ", "_").toLowerCase(), entry.getKey() + " ("+entry.getValue()+")");
        //         planetButtons.add(button);
        //     }
        // }

        if (player.hasAbility("zeal")) {
            int numPlayers = 0;
            for (Player player_ : activeMap.getPlayers().values()) {
                if (player_.isRealPlayer()) numPlayers++;
            }
            Button button = Button.primary("exhaust_argent", "Special Argent Votes ("+numPlayers+")").withEmoji(Emoji.fromFormatted(Emojis.Argent));
            planetButtons.add(button);
        }
        if (player.hasTechReady("pi")) {
            Button button = Button.primary("exhaust_predictive", "Use Predictive Votes (3)").withEmoji(Emoji.fromFormatted(Emojis.CyberneticTech));
            planetButtons.add(button);
        }

        return planetButtons;
    }


    public static List<Button> getPlanetRefreshButtons(GenericInteractionCreateEvent event, Player player, Map activeMap) {
        List<Button> planetButtons = new ArrayList<>();
        List<String> planets = new ArrayList<>(player.getExhaustedPlanets());
        for (String planet : planets) {
            Button button = Button.success("refresh_"+planet, StringUtils.capitalize(planet) + "("+ Helper.getPlanetResources(planet, activeMap)+"/"+Helper.getPlanetInfluence(planet, activeMap) + ")");
            planetButtons.add(button);
        }

        return planetButtons;
    }


    public static void eraseVotesOfFaction(Map activeMap, String faction) {

        HashMap<String, String> outcomes = new HashMap<String, String>();
        outcomes.putAll(activeMap.getCurrentAgendaVotes());
        if (outcomes.keySet().size() == 0) {
            return;
        } else {
            String voteSumm = "";

            for (String outcome : outcomes.keySet()) {
                voteSumm = "";
                StringTokenizer vote_info = new StringTokenizer(outcomes.get(outcome), ";");


                while (vote_info.hasMoreTokens()) {

                    String specificVote = vote_info.nextToken();
                    String faction2 = specificVote.substring(0, specificVote.indexOf("_"));
                    String vote = specificVote.substring(specificVote.indexOf("_")+1,specificVote.length());
                    if (vote.contains("Rider") || vote.contains("Sanction")||vote.contains("Hero")) {
                        voteSumm = voteSumm +";"+ specificVote;
                        continue;
                    }
                    else if (faction2.equals(faction)) {
                        continue;
                    } else {
                        voteSumm = voteSumm +";"+ specificVote;
                        continue;
                    }
                }
                if (voteSumm.equalsIgnoreCase("")) {
                    activeMap.removeOutcomeAgendaVote(outcome);
                } else {
                    activeMap.setCurrentAgendaVote(outcome, voteSumm);
                }

            }
        }
    }

    public static String getSummaryOfVotes(Map activeMap, boolean capitalize) {
        String summary = "";
        HashMap<String, String> outcomes = activeMap.getCurrentAgendaVotes();

        if (outcomes.keySet().size() == 0) {
            summary = "No current riders or votes have been cast yet.";
        } else {
            summary = "Current status of votes and outcomes is: \n";
            for (String outcome : outcomes.keySet()) {
                int totalVotes = 0;
                StringTokenizer vote_info = new StringTokenizer(outcomes.get(outcome), ";");
                String outcomeSummary = "";
                if (capitalize) {
                    outcome = StringUtils.capitalize(outcome);
                }
                while (vote_info.hasMoreTokens()) {

                    String specificVote = vote_info.nextToken();
                    String faction = specificVote.substring(0, specificVote.indexOf("_"));
                    if (capitalize) {
                        faction = Helper.getFactionIconFromDiscord(faction);

                        if (activeMap.isFoWMode()) {
                            faction = "Someone";
                        }
                        String vote = specificVote.substring(specificVote.indexOf("_")+1,specificVote.length());
                        if (!vote.contains("Rider") && !vote.contains("Sanction") && !vote.contains("Hero")) {
                            totalVotes += Integer.parseInt(vote);
                            outcomeSummary = outcomeSummary + faction +"-"+ vote + ", ";
                        } else {
                            outcomeSummary = outcomeSummary + faction +"-"+ vote + ", ";
                        }
                    } else {
                        String vote = specificVote.substring(specificVote.indexOf("_")+1,specificVote.length());
                        if (!vote.contains("Rider") && !vote.contains("Sanction") && !vote.contains("Hero")) {
                            totalVotes += Integer.parseInt(vote);
                            outcomeSummary = outcomeSummary + faction +" voted "+ vote + " votes. ";
                        } else {
                            outcomeSummary = outcomeSummary + faction +" cast a "+ vote + ". ";
                        }

                    }

                }
                if (capitalize) {
                    if (outcomeSummary != null && outcomeSummary.length() > 2) {
                        outcomeSummary = outcomeSummary.substring(0, outcomeSummary.length()-2);
                    }
                    
                    
                    if(!activeMap.isFoWMode() &&activeMap.getCurrentAgendaInfo().contains("Elect Player"))
                    {
                        summary = summary + Helper.getFactionIconFromDiscord(outcome.toLowerCase())+" "+ outcome+": "+totalVotes +". (" +outcomeSummary + ")\n";
                       
                    }
                    else if(!activeMap.isHomeBrewSCMode() &&activeMap.getCurrentAgendaInfo().contains("Elect Strategy Card"))
                    {
                        summary = summary + Helper.getSCEmojiFromInteger(Integer.parseInt(outcome))+ " "+ outcome+": "+totalVotes +". (" +outcomeSummary + ")\n";
                    }
                    else
                    {
                        summary = summary + outcome+": "+totalVotes +". (" +outcomeSummary + ")\n";

                    }
                } else {
                    summary = summary + outcome+": Total votes "+totalVotes +". " +outcomeSummary + "\n";
                }

            }
        }
        return summary;
    }

    public static String getWinner(String summary) {
        String winner = null;
        StringTokenizer vote_info = new StringTokenizer(summary, ":");
        int currentHighest = 0;
        String outcome = "";

        while (vote_info.hasMoreTokens()) {
            String specificVote = vote_info.nextToken();
            if (specificVote.contains("Current status")) {
                continue;
            } else {
                if (!specificVote.contains("Total")) {
                    outcome = specificVote.substring(2, specificVote.length());
                    continue;
                }

                String votes = specificVote.substring(13,specificVote.indexOf("."));
                if (Integer.parseInt(votes) >= currentHighest) {
                    if (Integer.parseInt(votes) == currentHighest) {
                        winner = winner + "*"+outcome;
                    } else {
                        currentHighest = Integer.parseInt(votes);
                        winner = outcome;
                    }

                }
                outcome = specificVote.substring(specificVote.lastIndexOf(".")+3,specificVote.length());
            }
        }
        if (currentHighest == 0) {
            return null;
        }
        return winner;
    }

    public static String getPlayerVoteText(Map activeMap, Player player) {
        StringBuilder sb = new StringBuilder();
        int voteCount = getVoteCountFromPlanets(activeMap, player);
        java.util.Map<String, Integer> additionalVotes = getAdditionalVotesFromOtherSources(activeMap, player);
        String additionalVotesText = getAdditionalVotesFromOtherSourcesText(additionalVotes);

        if (activeMap.isFoWMode()) {
            sb.append(" vote count: **???**");
            return sb.toString();
        } else if (player.hasAbility("galactic_threat") && !activeMap.playerHasLeaderUnlockedOrAlliance(player, "xxchacommander")) {
            sb.append(" NOT VOTING (Galactic Threat)");
            return sb.toString();
        } else if (player.hasLeaderUnlocked("xxchahero")) {
            sb.append(" vote count: **" + Emojis.ResInf + " " + voteCount);
        } else if (player.hasAbility("lithoids")) { // Vote with planet resources, not influence
            sb.append(" vote count: **" + Emojis.resources + " " + voteCount);
        } else if (player.hasAbility("biophobic")) {
            sb.append(" vote count: **" + Emojis.SemLor + " " + voteCount);
        } else {
            sb.append(" vote count: **" + Emojis.influence + " " + voteCount);
        }
        if (!additionalVotesText.isEmpty()) {
            int additionalVoteCount = additionalVotes.values().stream().mapToInt(Integer::intValue).sum();
            if (additionalVoteCount > 0) {
                sb.append(" + " + additionalVoteCount + "** additional votes from:  ");
            }
            else {
                sb.append("**");
            }
            sb.append("  ").append(additionalVotesText);
        } else sb.append("**");

        return sb.toString();
    }

    public static int getTotalVoteCount(Map activeMap, Player player) {
        return getVoteCountFromPlanets(activeMap, player) + getAdditionalVotesFromOtherSources(activeMap, player).values().stream().mapToInt(Integer::intValue).sum();
    }

    public static int getVoteCountFromPlanets(Map activeMap, Player player) {
        List<String> planets = new ArrayList<>(player.getReadiedPlanets());
        HashMap<String, UnitHolder> planetsInfo = activeMap.getPlanetsInfo();
        int baseResourceCount = planets.stream().map(planetsInfo::get).filter(Objects::nonNull).map(planet -> (Planet) planet).mapToInt(Planet::getResources).sum();
        int baseInfluenceCount = planets.stream().map(planetsInfo::get).filter(Objects::nonNull).map(planet -> (Planet) planet).mapToInt(Planet::getInfluence).sum();
        int voteCount = baseInfluenceCount; //default

        //NEKRO unless XXCHA ALLIANCE
        if (player.hasAbility("galactic_threat") && !activeMap.playerHasLeaderUnlockedOrAlliance(player,"xxchacommander")) {
            return 0;
        }
        
        //KHRASK
        if (player.hasAbility("lithoids")) { // Vote with planet resources, not influence
            voteCount = baseResourceCount;
        }
        
        //ZELIAN PURIFIER BIOPHOBIC ABILITY - 1 planet = 1 vote
        if (player.hasAbility("biophobic")) {
            voteCount = planets.size();
        }

        //XXCHA
        if (player.hasLeaderUnlocked("xxchahero")) {
            voteCount = baseResourceCount + baseInfluenceCount;
        }

        //Xxcha Alliance - +1 vote for each planet
        if (activeMap.playerHasLeaderUnlockedOrAlliance(player, "xxchacommander")) {
            int readyPlanetCount = planets.size();
            voteCount += readyPlanetCount;
        }

        return voteCount;
    }

    public static String getAdditionalVotesFromOtherSourcesText(java.util.Map<String, Integer> additionalVotes) {
        StringBuilder sb = new StringBuilder();
        for (Entry<String, Integer> entry : additionalVotes.entrySet()) {
            if (entry.getValue() > 0) {
                sb.append("(+").append(entry.getValue()).append(" for ").append(entry.getKey()).append(")");
            } else {
                sb.append("(").append(entry.getKey()).append(")");
            }
        }
        return sb.toString();
    }

    /**
     * @param activeMap
     * @param player
     * @return (K, V) -> K = additionalVotes / V = text explanation of votes
     */
    public static java.util.Map<String, Integer> getAdditionalVotesFromOtherSources(Map activeMap, Player player) {
        java.util.Map<String, Integer> additionalVotesAndSources = new LinkedHashMap<>();

        //Argent Zeal
        if (player.hasAbility("zeal")) {
            long playerCount = activeMap.getPlayers().values().stream().filter(Player::isRealPlayer).count();
            additionalVotesAndSources.put(Emojis.Argent + "Zeal", Math.toIntExact(playerCount));
        }

        //Blood Pact
        if (player.getPromissoryNotesInPlayArea().contains("blood_pact")) {
            additionalVotesAndSources.put(Emojis.Empyrean + Emojis.PN + "Blood Pact", 4);
        }

        //Predictive Intelligence
        if (player.hasTechReady("pi")) {
            additionalVotesAndSources.put(Emojis.CyberneticTech + "Predictive Intelligence", 3);
        }

        //Xxcha Alliance
        if (activeMap.playerHasLeaderUnlockedOrAlliance(player, "xxchacommander")) {
            additionalVotesAndSources.put(Emojis.Xxcha + "Alliance has been counted for", 0);
        }

        //Absol Shard of the Throne
        if (CollectionUtils.containsAny(player.getRelics(), List.of("absol_shardofthethrone1", "absol_shardofthethrone2", "absol_shardofthethrone3"))) {
            int count = player.getRelics().stream().filter(s -> s.contains("absol_shardofthethrone")).toList().size(); //  +2 votes per Absol shard
            int shardVotes = 2 * count;
            additionalVotesAndSources.put("(" + count + "x)" + Emojis.Relic + "Shard of the Throne" + Emojis.Absol, shardVotes);
        }

        //Absol's Syncretone - +1 vote for each neighbour
        if (player.hasRelicReady("absol_syncretone")) {
            int count = Helper.getNeighbourCount(activeMap, player);
            additionalVotesAndSources.put(Emojis.Relic + "Syncretone", count);
        }

        //Ghoti Wayfarer Tech
        if (player.hasTechReady("dsghotg")) {
            int fleetCC = player.getFleetCC();
            additionalVotesAndSources.put(Emojis.BioticTech + "Exhaust Networked Command", fleetCC);
        }

        // //Edyn Mandate Sigil - Planets in Sigil systems gain +1 vote //INCOMPLETE, POSSIBLY CHANGING ON DS END
        // Player edynMechPlayer = Helper.getPlayerFromColorOrFaction(activeMap, "edyn");
        // if (edynMechPlayer != null) {
        //     int count = 0;
        //     List<Tile> edynMechTiles = activeMap.getTileMap().values().stream().filter(t -> Helper.playerHasMechInSystem(t, activeMap, edynMechPlayer)).toList();
        //     for (Tile tile : edynMechTiles) {
        //         for (String planet : tile.getUnitHolders().keySet()) {
        //             if (player.getPlanets().contains(planet) && !player.getExhaustedPlanets().contains(planet)) {
        //                 count++;
        //             }
        //         }
        //     }
        //     if (count != 0) {
        //         sb.append(" (+" + count + " for (" + count + "x) Planets in " + Emojis.edyn + "Sigil Systems)");
        //         additionalVotes += count;
        //     }
        // }

        return additionalVotesAndSources;
    }
}