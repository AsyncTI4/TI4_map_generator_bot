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
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import ti4.MapGenerator;
import ti4.MessageListener;
import ti4.commands.agenda.ListVoteCount;
import ti4.commands.agenda.RevealAgenda;
import ti4.commands.cardsac.ACInfo;
import ti4.commands.cardsac.ACInfo_Legacy;
import ti4.map.UnitHolder;
import ti4.commands.cardsac.PlayAC;
import ti4.commands.cardsso.SOInfo;
import ti4.commands.cardsso.ScoreSO;
import ti4.commands.explore.ExploreSubcommandData;
import ti4.commands.status.Cleanup;
import ti4.commands.status.RevealStage1;
import ti4.commands.status.RevealStage2;
import ti4.commands.status.ScorePublic;
import ti4.commands.units.AddUnits;
import ti4.commands.player.PlanetAdd;
import ti4.commands.player.PlanetExhaust;
import ti4.commands.player.PlanetRefresh;
import ti4.helpers.Constants;
import ti4.helpers.AliasHandler;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.MapSaveLoadManager;
import ti4.map.Planet;
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
                passOnAbilities = Button.danger("pass_on_abilities", "Pass on Pol. Stability/A. Burial Sites/Maw Of W./Crown of E.");
            }
            else
            {
                passOnAbilities = Button.danger("pass_on_abilities", "Pass on Pol. Stability/Summit/Man. Investments");
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
            activeMap.getDiscardActionCards();
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
        else if (buttonID.startsWith(Constants.GENERIC_BUTTON_ID_PREFIX)) {
            addReaction(event, false, false, null, "");
        }
        else if (buttonID.startsWith("exhaust_")) {
            String planetName = buttonID.substring(buttonID.indexOf("_")+1, buttonID.length());
            String votes = buttonLabel.substring(buttonLabel.indexOf("(")+1, buttonLabel.indexOf(")"));
            if(!buttonID.contains("argent") && !buttonID.contains("blood") && !buttonID.contains("predictive"))
            {
                new PlanetExhaust().doAction(player, planetName, activeMap);
            }
            
            //List<ItemComponent> actionRow = event.getMessage().getActionRows().get(0).getComponents();
            List<ActionRow> actionRow2 = new ArrayList<>();
            for(ActionRow row : event.getMessage().getActionRows())
            {
                List<ItemComponent> buttonRow = row.getComponents();
                int buttonIndex = buttonRow.indexOf(event.getButton());
                if(buttonIndex>-1)
                {
                    buttonRow.remove(buttonIndex); 
                }
                if(buttonRow.size() > 0)
                {
                    actionRow2.add(ActionRow.of(buttonRow));
                }
            }
            
            String totalVotesSoFar = event.getMessage().getContentRaw();
            if(totalVotesSoFar == null || totalVotesSoFar.equalsIgnoreCase(""))
            {
                totalVotesSoFar = "Total votes exhausted so far: "+ votes;
            }
            else
            {
                int totalVotes = Integer.parseInt(totalVotesSoFar.substring(totalVotesSoFar.indexOf(":")+2,totalVotesSoFar.length())) + Integer.parseInt(votes);
                totalVotesSoFar = totalVotesSoFar.substring(0, totalVotesSoFar.indexOf(":")+2) + totalVotes;
            }
            if(actionRow2.size() > 0)
            {
                event.getMessage().editMessage(totalVotesSoFar).setComponents(actionRow2).queue(); 
            }

            if(!buttonID.contains("argent") && !buttonID.contains("blood") && !buttonID.contains("predictive"))
            {
                addReaction(event, true, false,"Exhausted "+planetName + " as "+ votes + " votes", "");
            }
            else
            {
                addReaction(event, true, false,"Used ability for "+ votes + " votes", "");
            }
        }
        else if (buttonID.startsWith("delete_buttons_"))
        {
            boolean resolveTime = false;
            String winner = "";
            String votes = buttonID.substring(buttonID.lastIndexOf("_")+1, buttonID.length());
            if(!buttonID.contains("outcomeTie_"))
            {
                if(votes.equalsIgnoreCase("0"))
                {
                    addReaction(event, true, true,"Abstained.", "");
                }
                else
                {
                    String identifier = "";
                    String outcome = activeMap.getLatestOutcomeVotedFor();
                    if(activeMap.isFoWMode())
                    {
                        identifier = player.getColor();
                    }
                    else
                    {
                        identifier = player.getFaction();
                    }
                    HashMap<String, String> outcomes = activeMap.getCurrentAgendaVotes();
                    String existingData = outcomes.getOrDefault(outcome, "empty");
                    if(existingData.equalsIgnoreCase("empty"))
                    {
                        
                        existingData = identifier + "_" + votes;
                    }
                    else
                    {
                        existingData = existingData + ";" + identifier + "_" + votes;
                    }
                    activeMap.setCurrentAgendaVote(outcome, existingData);
                    addReaction(event, true, true,"Voted "+votes + " votes for "+outcome+"!", "");
                }



                String message = " up to vote! Resolve using buttons. \n \n" + getSummaryOfVotes(activeMap, true);

                Player nextInLine = ListVoteCount.getNextInLine(player, ListVoteCount.getVotingOrder(activeMap));
                if(!nextInLine.getColor().equalsIgnoreCase(player.getColor()))
                {
                    String realIdentity = "";
                    if(activeMap.isCommunityMode())
                    {
                        if(nextInLine.getRoleForCommunity() == null)
                        {
                            return;
                        }
                        realIdentity = Helper.getRoleMentionByName(event.getGuild(), nextInLine.getRoleForCommunity().getName());
                    }
                    else
                    {
                        realIdentity =Helper.getPlayerRepresentation(nextInLine);
                    }
                    message = realIdentity + message;
                    Button Vote= Button.success("vote", "Choose To Vote");
                    Button Abstain = Button.danger("delete_buttons_0", "Choose To Abstain");
                    ActionRow actionRow4 = ActionRow.of(List.of(Vote, Abstain));
                    MessageCreateBuilder baseMessageObject4 = new MessageCreateBuilder().addContent(message);
                    if (!actionRow4.isEmpty()) baseMessageObject4.addComponents(actionRow4);
                    if(activeMap.isFoWMode())
                    {
                        if(nextInLine.getPrivateChannel() != null)
                        {
                            nextInLine.getPrivateChannel().sendMessage(baseMessageObject4.build()).queue(message4_ -> {});
                            event.getChannel().sendMessage("Notified next in line");
                        }
                    }
                    else
                    {
                        event.getChannel().sendMessage(baseMessageObject4.build()).queue(message4_ -> {});
                    }
                }
                else
                {
                    String summary = getSummaryOfVotes(activeMap, false);
                    winner = getWinner(summary);
                    if(winner != null && !winner.contains("_"))
                    {
                        resolveTime = true;
                    }
                    else
                    {
                        Player speaker = null;
                        for(Player pos : activeMap.getPlayers().values())
                        {
                            if(pos.getColor() != null && pos.getUserID().equalsIgnoreCase(activeMap.getSpeaker()))
                            {
                                speaker = pos;
                            }
                        }
                        List<Button> tiedWinners = new ArrayList<Button>();
                        if(winner != null)
                        {
                            StringTokenizer winnerInfo = new StringTokenizer(winner, "_");
                            while (winnerInfo.hasMoreTokens()) {
                                String tiedWinner = winnerInfo.nextToken();
                                Button button = Button.primary("delete_buttons_outcomeTie_"+tiedWinner, tiedWinner);
                                tiedWinners.add(button);
                            }
                        }
                        else
                        {
                            event.getChannel().sendMessage("Please try the voting process again and cast at least one vote for something. Ping Fin to tell him to fix this.");
                            Button button = Button.primary("placeholder", "Unfortunate Dead End");
                            tiedWinners.add(button);
                        }
                        if (!tiedWinners.isEmpty())
                        {
                            for (MessageCreateData messageCreateData : MessageHelper.getMessageCreateDataObjects(Helper.getPlayerRepresentation(event, speaker)+ " please decide the winner.",tiedWinners)) {
                                if(activeMap.isFoWMode())
                                {
                                    speaker.getPrivateChannel().sendMessage(messageCreateData).queue();
                                }
                                else
                                {
                                    event.getChannel().sendMessage(messageCreateData).queue();
                                }
                            }
                        }
                    }
                }
            }
            else
            {
                resolveTime = true;
                winner = buttonID.substring(buttonID.lastIndexOf("_"), buttonID.length());
            }
            if(resolveTime)
            {

                String summary = getSummaryOfVotes(activeMap, false);
                List<String> losers = getLosers(summary, winner, activeMap);
                String resMessage = "Current winner is "+winner + ". Please hold while people resolve shenanigans.";
                if((!activeMap.isACInDiscard("Bribery") || !activeMap.isACInDiscard("Deadly Plot")) && losers.size() > 0)
                {
                    Button noDeadly = Button.primary("genericReact1", "No Deadly Plot");
                    Button noBribery = Button.primary("genericReact2", "No Bribery");
                    List<Button> deadlyActionRow = new ArrayList<>(List.of(noBribery, noDeadly));
                    
                    if (!deadlyActionRow.isEmpty())
                    {
                        for (MessageCreateData messageCreateData : MessageHelper.getMessageCreateDataObjects(resMessage,deadlyActionRow)) {
                            event.getChannel().sendMessage(messageCreateData).queue();
                        }
                    } 
                    String loseMessage = "";
                    for(String loser : losers)
                    {

                        Player los = null;
                        for(Player pos : activeMap.getPlayers().values())
                        {
                            if(pos.getColor() != null && pos.getColor().equals(loser))
                            {
                                los = pos;
                            }
                            if(pos.getFaction() != null && pos.getFaction().equals(loser))
                            {
                                los = pos;
                            }
                        }
                        if(los != null)
                        {
                            if(activeMap.isFoWMode())
                            {
                                los.getPrivateChannel().sendMessage(Helper.getPlayerRepresentation(los) + "Please respond to bribery/deadly plot window");
                            }
                            else
                            {
                                loseMessage = loseMessage + Helper.getPlayerRepresentation(los);
                            }
                        }
                    }
                    if(!activeMap.isFoWMode())
                    {
                        event.getChannel().sendMessage(loseMessage + "Please respond to bribery/deadly plot window");
                    }          
                }
                else
                {
                    activeMap.getMainGameChannel().sendMessage("Either both bribery and deadly plot were in the discard or noone could legally play them.");
                }
                if(activeMap.getCurrentAgendaInfo().contains("Elect Player") && (!activeMap.isACInDiscard("Confounding") || !activeMap.isACInDiscard("Confusing")))
                {
                    String resMessage2 = "Current winner is "+winner + ". "+Helper.getGamePing(activeMap.getGuild(), activeMap) + " please react to no confusing/confounding";
                    Button noConfounding = Button.primary("genericReact3", "Refuse Confounding Legal Text");
                    Button noConfusing = Button.primary("genericReact4", "Refuse Confusing Legal Text");
                    List<Button> deadlyActionRow2 = new ArrayList<>(List.of(noConfounding, noConfusing));
                    
                    if (!deadlyActionRow2.isEmpty())
                    {
                        for (MessageCreateData messageCreateData : MessageHelper.getMessageCreateDataObjects(resMessage2,deadlyActionRow2)) {
                            event.getChannel().sendMessage(messageCreateData).queue();
                        }
                    } 

                }
                else
                {
                    if(activeMap.getCurrentAgendaInfo().contains("Elect Player"))
                    {
                        activeMap.getMainGameChannel().sendMessage("Both confounding and confusing are in the discard pile. ");

                    }
                }
                
                String resMessage3 = "Current winner is "+winner + ". "+Helper.getGamePing(activeMap.getGuild(), activeMap) + "When shenanigans have concluded, please confirm resolution or discard the result and manually resolve it yourselves.";
                Button autoResolve = Button.primary("autoresolve_"+winner, "Resolve with current winner");
                Button manualResolve = Button.danger("autoresolve_manual", "Resolve it Manually");
                List<Button> deadlyActionRow3 = new ArrayList<>(List.of(autoResolve, manualResolve));
                    
                if (!deadlyActionRow3.isEmpty())
                {
                    for (MessageCreateData messageCreateData : MessageHelper.getMessageCreateDataObjects(resMessage3,deadlyActionRow3)) {
                        event.getChannel().sendMessage(messageCreateData).queue();
                    }
                } 
            }
            if(!votes.equalsIgnoreCase("0"))
            {
                event.getMessage().delete().queue();
            }
            
        }
        else if(buttonID.startsWith("fixerVotes_"))
        {
            String voteMessage = "Thank you for specifying, please select from the available buttons to vote.";
            String vote = buttonID.substring(11, buttonID.length());
            int votes =Integer.parseInt(vote);
            List<Button> voteActionRow = getVoteButtons(votes-9,votes);
            if (!voteActionRow.isEmpty())
            {
                for (MessageCreateData messageCreateData : MessageHelper.getMessageCreateDataObjects(voteMessage,voteActionRow)) {
                    event.getChannel().sendMessage(messageCreateData).queue();
                }
            }
            event.getMessage().delete().queue(); 
        }
        else if(buttonID.startsWith("planetOutcomes_"))
        {
            String factionOrColor = buttonID.substring(buttonID.indexOf("_")+1, buttonID.length());
            Player planetOwner = null;
            String voteMessage= "Chose to vote for one of "+factionOrColor +"'s planets. Click buttons for which outcome to vote for.";
            List<Button> outcomeActionRow = null;
            if(activeMap.isFoWMode())
            {
                for(Player pos : activeMap.getPlayers().values())
                {
                    if(pos.getColor() != null && pos.getColor().equalsIgnoreCase(factionOrColor))
                    {
                        planetOwner = pos;
                    }
                }
            }
            else
            {
                
                for(Player pos : activeMap.getPlayers().values())
                {
                    if(pos.getFaction() != null && pos.getFaction().equalsIgnoreCase(factionOrColor))
                    {
                        planetOwner = pos;
                    }
                }
            }
           
            outcomeActionRow = getPlanetOutcomeButtons(event, planetOwner, activeMap);
            if (!outcomeActionRow.isEmpty()) 
            {
                for (MessageCreateData messageCreateData : MessageHelper.getMessageCreateDataObjects(voteMessage,outcomeActionRow)) {
                    event.getChannel().sendMessage(messageCreateData).queue();
                }
            }
            event.getMessage().delete().queue();
        }
        else if(buttonID.startsWith("distinguished_"))
        {
            String voteMessage = "You added 5 votes to your total. Please select from the available buttons to vote.";
            String vote = buttonID.substring(14, buttonID.length());
            int votes =Integer.parseInt(vote);
            List<Button> voteActionRow = getVoteButtons(votes,votes+5);
            if (!voteActionRow.isEmpty())
            {
                for (MessageCreateData messageCreateData : MessageHelper.getMessageCreateDataObjects(voteMessage,voteActionRow)) {
                    event.getChannel().sendMessage(messageCreateData).queue();
                }
            }
            event.getMessage().delete().queue(); 
        }
        else if(buttonID.startsWith("outcome_"))
        {
            String outcome = buttonID.substring(8, buttonID.length());
            String voteMessage= "Chose to vote for "+outcome+". Click Buttons for amount of votes";
            activeMap.setLatestOutcomeVotedFor(outcome);
            String speakerName = activeMap.getSpeaker();
            
            int[] voteArray = ListVoteCount.getVoteTotal(event, player, activeMap);

            int minvote = 1;
            if(player.getFaction().equalsIgnoreCase("argent"))
            {
                int numPlayers = 0;
                for (Player player_ : activeMap.getPlayers().values()) {
                    if (player_.isRealPlayer()) numPlayers++;
                }
                minvote = minvote+numPlayers;
            }
            if(voteArray[0]-minvote > 20)
            {
                voteMessage= "Chose to vote for "+outcome+". You have more votes than discord has buttons. Please further specify your desired vote count by clicking the button which contains your desired vote amount (or largest button).";
            }
            List<Button> voteActionRow = getVoteButtons(minvote,voteArray[0]);
            if (!voteActionRow.isEmpty())
            {
                for (MessageCreateData messageCreateData : MessageHelper.getMessageCreateDataObjects(voteMessage,voteActionRow)) {
                    event.getChannel().sendMessage(messageCreateData).queue();
                }
            }
            event.getMessage().delete().queue(); 
        }
        else if(buttonID.startsWith("votes_"))
        {
            String votes = buttonID.substring(6, buttonID.length());
            String voteMessage= "Chose to vote  "+votes+" votes for "+activeMap.getLatestOutcomeVotedFor()+ ". Click buttons to choose which planets to exhaust for votes";

            List<Button> voteActionRow = getPlanetButtons(event, player, activeMap);
            Button concludeExhausting = Button.danger("delete_buttons_"+votes, "Done exhausting planets.");
            voteActionRow.add(concludeExhausting);
            if (!voteActionRow.isEmpty())
            {
                for (MessageCreateData messageCreateData : MessageHelper.getMessageCreateDataObjects(voteMessage,voteActionRow)) {
                    event.getChannel().sendMessage(messageCreateData).queue();
                }
            }
            event.getMessage().delete().queue(); 
        }
        else if(buttonID.startsWith("autoresolve_"))
        {
            String voteMessage = "";
            String result = buttonID.substring(buttonID.indexOf("_")+1, buttonID.length());
            if(result.equalsIgnoreCase("manual"))
            {
                //TODO
                voteMessage = "You chose a manual resolution. Click the buttons for next steps after you're done with that.";
            }
            else
            {
                //TODO
                voteMessage= "Resolving vote for "+ result +". Click the buttons for next steps after you're done resolving riders.";
                boolean success = activeMap.addLaw(agendaID, result (find result));
                if (success) {
                    MessageHelper.sendMessageToChannel(event.getChannel(), "Law added");
                } else {
                    MessageHelper.sendMessageToChannel(event.getChannel(), "Law ID not found");
                }                
            }
            
            Button flipNextAgenda = Button.primary("flip_agenda", "Flip Agenda");
            Button proceedToStrategyPhase = Button.secondary("proceed_to_strategy", "Proceed to Strategy Phase (will run agenda cleanup and ping speaker)");
            List<Button> resActionRow = new ArrayList<>(List.of(flipNextAgenda, proceedToStrategyPhase));
            if (!resActionRow.isEmpty())
            {
                for (MessageCreateData messageCreateData : MessageHelper.getMessageCreateDataObjects(voteMessage,resActionRow)) {
                    event.getChannel().sendMessage(messageCreateData).queue();
                }
            }   
            event.getMessage().delete().queue(); 
        }
        else if(buttonID.startsWith("reverse_"))
        {
            String choice= buttonID.substring(buttonID.indexOf("_"+1), buttonID.length());
            String voteMessage= "Chose to reverse latest rider on "+choice;
            
            HashMap<String, String> outcomes = activeMap.getCurrentAgendaVotes();
            String existingData = outcomes.getOrDefault(choice, "empty");
            if(existingData.equalsIgnoreCase("empty"))
            {
                voteMessage = "Something went wrong. Ping Fin and continue onwards.";
            }
            else
            {
                int lastSemicolon = existingData.lastIndexOf(";");
                if (lastSemicolon < 0)
                {
                    existingData = "";
                }
                else
                {
                    existingData = existingData.substring(0, lastSemicolon);
                }
               
            }
            activeMap.setCurrentAgendaVote(choice, existingData);
            event.getChannel().sendMessage(voteMessage).queue();
            event.getMessage().delete().queue(); 
        }
        else if(buttonID.startsWith("rider_"))
        {
            String choice = buttonID.substring(6, buttonID.lastIndexOf("_"));
            String rider = buttonID.substring(buttonID.lastIndexOf("_")+1,buttonID.length());
            String voteMessage= "Chose to put a "+rider+ " on "+StringUtils.capitalize(choice);
            String identifier = "";
            if(activeMap.isFoWMode())
            {
                identifier =  player.getColor();
            }
            else
            {
                identifier =  player.getFaction();
            }
            HashMap<String, String> outcomes = activeMap.getCurrentAgendaVotes();
            String existingData = outcomes.getOrDefault(choice, "empty");
            if(existingData.equalsIgnoreCase("empty"))
            {
                existingData = identifier + "_" + rider;
            }
            else
            {
                existingData = existingData + ";" + identifier + "_" + rider;
            }
            activeMap.setCurrentAgendaVote(choice, existingData);

            List<Button> voteActionRow = new ArrayList<Button>();
            Button concludeExhausting = Button.danger("reverse_"+choice, "Click this to undo the rider (for if it is sabod)");
            voteActionRow.add(concludeExhausting);
            if (!voteActionRow.isEmpty())
            {
                for (MessageCreateData messageCreateData : MessageHelper.getMessageCreateDataObjects(voteMessage,voteActionRow)) {
                    event.getChannel().sendMessage(messageCreateData).queue();
                }
            }
            event.getMessage().delete().queue(); 
        }
        else if(buttonID.startsWith("genericReact"))
        {
            String message = activeMap.isFoWMode() ? "Turned down window" : null;
            addReaction(event, false, false, message, "");
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
                    boolean hasSchemingAbility = player.getFactionAbilities().contains("scheming");
                    String message = hasSchemingAbility ? "Drew 3 Actions Cards (Scheming) - please discard an Action Card from your hand" : "Drew 2 Actions cards";
                    int count = hasSchemingAbility ? 3 : 2;
                    for (int i = 0; i < count; i++) {
                        activeMap.drawActionCard(player.getUserID());
                    }
                    ACInfo.sendActionCardInfo(activeMap, player, event);
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
                    SOInfo.sendSecretObjectiveInfo(activeMap, player, event);
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
                    addPersistentReactions(event, activeMap);
                }
                case "no_after" -> {
                    String message = activeMap.isFoWMode() ? "No afters" : null;
                    addReaction(event, false, false, message, "");
                }
                case "no_after_persistent" -> {
                    String message = activeMap.isFoWMode() ? "No afters (locked in)" : null;
                    activeMap.addPlayersWhoHitPersistentNoAfter(player.getFaction());
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
                    boolean hasSchemingAbility = player.getFactionAbilities().contains("scheming");
                    int count2 = hasSchemingAbility ? 2 : 1;
                    if(player.getCommodities() > 0)
                    {
                        player.setCommodities(player.getCommodities()-1);
                        for (int i = 0; i < count2; i++) {
                            activeMap.drawActionCard(player.getUserID());
                        }
                        ACInfo.sendActionCardInfo(activeMap, player, event);
                        String message = hasSchemingAbility ? "Spent 1 commodity to draw " + count2 + " Action Card (Scheming) - please discard an Action Card from your hand" : "Spent 1 commodity to draw " + count2 + " AC";
                        addReaction(event, false, false, message, "");
                    }
                    else if(player.getTg() > 0)
                    {
                        player.setTg(player.getTg()-1);
                        for (int i = 0; i < count2; i++) {
                            activeMap.drawActionCard(player.getUserID());
                        }
                        ACInfo.sendActionCardInfo(activeMap, player, event);
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
                    addReaction(event, true, false, "Confirmed CCs: "+player.getTacticalCC()+"/"+player.getFleetCC()+"/"+player.getStrategicCC(), "");
                }
                case "draw_1_AC" -> {
                    activeMap.drawActionCard(player.getUserID());
                    ACInfo.sendActionCardInfo(activeMap, player, event);
                    addReaction(event, true, false, "Drew 1 AC", "");
                }
                case "draw_2_AC" -> {
                    activeMap.drawActionCard(player.getUserID());
                    activeMap.drawActionCard(player.getUserID());
                    ACInfo_Legacy.sentUserCardInfo(event, activeMap, player, false);
                    addReaction(event, true, false, "Drew 2 AC", "");
                }
                case "pass_on_abilities" -> {
                    addReaction(event, false, false,"Passed"+event.getButton().getLabel().replace("Pass", ""), "");
                }
                case "vote" -> {
                    String voteMessage= "Chose to Vote. Click buttons for which outcome to vote for.";
                    String agendaDetails = activeMap.getCurrentAgendaInfo();
                    agendaDetails = agendaDetails.substring(agendaDetails.indexOf("_")+1, agendaDetails.length());
                    List<Button> outcomeActionRow = null;
                    if(agendaDetails.contains("For"))
                    {
                        outcomeActionRow = getForAgainstOutcomeButtons(null);
                    }
                    else if(agendaDetails.contains("Player"))
                    {
                        outcomeActionRow = getPlayerOutcomeButtons(activeMap, null, null);
                    }
                    else if(agendaDetails.contains("Planet"))
                    {
                        voteMessage= "Chose to Vote. Too many planets in the game to represent all as buttons. Click buttons for which player owns the planet you wish to elect.";
                        outcomeActionRow = getPlayerOutcomeButtons(activeMap, null, "True");
                    }
                    else if(agendaDetails.contains("Secret"))
                    {
                        outcomeActionRow = getSecretOutcomeButtons(activeMap, null);
                    }
                    else if(agendaDetails.contains("Strategy"))
                    {
                        outcomeActionRow = getStrategyOutcomeButtons(null);
                    }
                    else
                    {
                        outcomeActionRow = getLawOutcomeButtons(activeMap, null);
                    }
                    if (outcomeActionRow.isEmpty()) break;
                    for (MessageCreateData messageCreateData : MessageHelper.getMessageCreateDataObjects(voteMessage,outcomeActionRow)) {
                        event.getChannel().sendMessage(messageCreateData).queue();
                    }
                    //event.getMessage().delete().queue();
                    //Possibilities include For/Against, Players, Planets, Secrets, Strategy Cards, Law
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
                             event.getChannel().sendMessage(baseMessageObject4.build()).queue(message4_ -> {});

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
        mainMessage.clearReactions().queue();
        String messageId = mainMessage.getId();
        RestAction<Message> messageRestAction = event.getChannel().retrieveMessageById(messageId);
        //messageRestAction.queue(m -> {
         //   RestAction<Void> voidRestAction = m.clearReactions();
         //   voidRestAction.queue();
        //});
    }


    private void addPersistentReactions(ButtonInteractionEvent event, Map activeMap)
    {
        Guild guild = activeMap.getGuild();
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
        String messageId = mainMessage.getId();

    
        StringTokenizer players = new StringTokenizer(activeMap.getPlayersWhoHitPersistentNoAfter(), "_");
        while (players.hasMoreTokens()) {
            String player = players.nextToken();
            Player player_ = null;
            for(Player pos : activeMap.getPlayers().values())
            {
                if(pos.getFaction() != null && pos.getFaction().equalsIgnoreCase(player))
                {
                    player_ = pos;
                }
            }
            if(player_ != null)
            {
                Emoji emojiToUse = Helper.getPlayerEmoji(activeMap, player_, mainMessage);
                event.getChannel().addReactionById(messageId, emojiToUse).queue();
            }
        }
        
        



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
            text = "(You) " + emojiToUse.getFormatted() + " " + message;
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
            respondAllPlayersReacted(event, map);
        }
    }

    private List<Button> getVoteButtons(int minVote, int voteTotal) {
        List<Button> voteButtons = new ArrayList<>();
        
        if(voteTotal-minVote > 20)
        {
            for(int x = 10; x < voteTotal+10; x=+10)
            {
                int y = x-9;
                Button button = Button.secondary("fixerVotes_"+x, y+"-"+x);
                voteButtons.add(button);
            }
        }
        else
        {
            for (int x = minVote; x < voteTotal+1; x++) {
                Button button = Button.secondary("votes_"+x, ""+x);
                voteButtons.add(button);
            }
            Button button = Button.danger("distinguished_"+voteTotal, "Press Me For +5 Vote Options");
            voteButtons.add(button);
        }
        return voteButtons;
    }

    private List<Button> getForAgainstOutcomeButtons(String rider) {
        List<Button> voteButtons = new ArrayList<>();
        Button button = null;
        Button button2 = null;
        if(rider == null)
        {
            button = Button.secondary("outcome_for", "For");
            button2 = Button.danger("outcome_against", "Against");
        }
        else
        {
            button = Button.primary("rider_for_"+rider, "For");
            button2 = Button.danger("rider_against_"+rider, "Against");
        }
        voteButtons.add(button);
        voteButtons.add(button2);
        return voteButtons;
    }

    private List<Button> getLawOutcomeButtons(Map activeMap, String rider) {
        List<Button> lawButtons = new ArrayList<>();
        for(java.util.Map.Entry<String, Integer> law : activeMap.getLaws().entrySet())
        {
            //String id :  activeMap.getLaws().keySet()
            //String[] agendaDetails = Mapper.getAgenda(id).split(";");
           // String agendaName = agendaDetails[0];
           Button button = null;
           if(rider == null)
           {
                button = Button.secondary("outcome_"+law.getValue(), law.getKey());
           }
           else
           {
                button = Button.secondary("rider_"+law.getValue()+"_"+rider, law.getKey());
           }
            lawButtons.add(button);
        }
        return lawButtons;
    }

    private List<Button> getSecretOutcomeButtons(Map activeMap, String rider) {
        List<Button> secretButtons = new ArrayList<>();
        for(Player player :  activeMap.getPlayers().values())
        {
            for(java.util.Map.Entry<String, Integer> so : player.getSecretsScored().entrySet())
            {
                Button button = null;
                if(rider == null)
                {
                    button = Button.secondary("outcome_"+so.getValue(), so.getKey()+"");
                }
                else
                {
                    button = Button.secondary("rider_"+so.getValue()+"_"+rider, so.getKey()+"");
                }
                secretButtons.add(button);
            }
        }
        return secretButtons;
    }
    private List<Button> getStrategyOutcomeButtons(String rider) {
        List<Button> strategyButtons = new ArrayList<>();
        for(int x = 1; x < 9; x++)
        {
            Button button = null;
            if(rider == null)
           {
                button = Button.secondary("outcome_"+x, x+"");
           }
           else
           {
                button = Button.secondary("rider_"+x+"_"+rider, x+"");
           }
            strategyButtons.add(button);  
        }
        return strategyButtons;
    }




    private List<Button> getPlanetOutcomeButtons(GenericInteractionCreateEvent event, Player player, Map map) {
        List<Button> planetOutcomeButtons = new ArrayList<>();
        List<String> planets = new ArrayList<>(player.getPlanets());
        
        for (String planet : planets) {
            
            Button button = Button.secondary("outcome_"+planet, planet);
            planetOutcomeButtons.add(button);
        }
        return planetOutcomeButtons;
    }

    private List<Button> getPlayerOutcomeButtons(Map activeMap, String rider, String gettingForPlanets) {
        List<Button> playerOutcomeButtons = new ArrayList<>();
    
        for (Player player : activeMap.getPlayers().values()) {
            if (player.isRealPlayer()) {
                String faction = player.getFaction();
               
                if (faction != null && Mapper.isFaction(faction)) {
                    Button button = null;
                    if(!activeMap.isFoWMode())
                    {
                        
                        if(rider != null)
                        {
                            button = Button.secondary("rider_"+faction+"_"+rider, " ");
                            
                        }
                        else
                        {
                            if(gettingForPlanets == null)
                            {
                                button = Button.secondary("outcome_"+faction, " ");
                            }
                            else
                            {
                                button = Button.secondary("planetOutcomes_"+faction, " ");
                            }
                            
                        }
                        String factionEmojiString = Helper.getFactionIconFromDiscord(faction);
                        button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                    }
                    else
                    {
                        if(rider == null)
                        {
                            button = Button.secondary("outcome_"+player.getColor(), player.getColor());
                        }
                        else
                        {
                            if(gettingForPlanets == null)
                            {
                                button = Button.secondary("rider_"+player.getColor()+"_"+rider, player.getColor());
                            }
                            else
                            {
                                button = Button.secondary("planetOutcomes_"+player.getColor(), player.getColor());
                            }
                        }
                    }
                    playerOutcomeButtons.add(button);
                }
            }
        }
        return playerOutcomeButtons;
    }



    private List<Button> getPlanetButtons(GenericInteractionCreateEvent event, Player player, Map map) {
        List<Button> planetButtons = new ArrayList<>();
        List<String> planets = new ArrayList<>(player.getPlanets());
        planets.removeAll(player.getExhaustedPlanets());
        int[] voteInfo = ListVoteCount.getVoteTotal(event, player, map);
        HashMap<String, UnitHolder> planetsInfo = map.getPlanetsInfo();
        for (String planet : planets) {
            int voteAmount = 0;
            Planet p = (Planet) planetsInfo.get(planet);
            voteAmount += p.getInfluence();
            if(voteInfo[2] != 0)
            {
                voteAmount+=1;
            }
            if(voteInfo[1] != 0)
            {
                voteAmount+=p.getResources();
            }
            if(voteAmount != 0)
            {
                Button button = Button.secondary("exhaust_"+planet, planet + " ("+voteAmount+")");
                planetButtons.add(button);
            }
        }
        if(player.getFaction().equalsIgnoreCase("argent"))
        {
            int numPlayers = 0;
            for (Player player_ : map.getPlayers().values()) {
                if (player_.isRealPlayer()) numPlayers++;
            }
            Button button = Button.primary("exhaust_argent", "Special Argent Votes ("+numPlayers+")");
            planetButtons.add(button);
        }
        if(player.getTechs().contains("pi") && !player.getExhaustedTechs().contains("pi"))
        {
            Button button = Button.primary("exhaust_predictive", "Use Predictive Votes (3)");
            planetButtons.add(button);
        }
        
        return planetButtons;
    }

    private static List<String> getRiders(String summary, String winner, Map activeMap)
    {
        String nomadTag = "nomad";
        if(activeMap.isFoWMode())
        {
            for(Player player : activeMap.getPlayers().values()) 
            {
                if(player.getFaction().equalsIgnoreCase("Nomad"))
                {
                    nomadTag = player.getColor();
                }
            }
        }
        List<String> riders = new ArrayList();
        
        StringTokenizer vote_info = new StringTokenizer(summary, "\n");
        boolean winnerfound = false;

        while (vote_info.hasMoreTokens() && !winnerfound) {
            String specificVote = vote_info.nextToken();
            if(specificVote.contains("current status"))
            {
                continue;
            }
            else
            {
                String outcome = specificVote.substring(0, specificVote.indexOf(":"));
                if(outcome.equalsIgnoreCase(winner))
                {
                    winnerfound = true;
                    StringTokenizer winner_info = new StringTokenizer(specificVote, ".");
                    while (winner_info.hasMoreTokens()) {
                        String specificPlayer = winner_info.nextToken();
                        if(specificPlayer.contains("cast a") || (specificPlayer.contains(nomadTag) && !specificPlayer.contains("Total")))
                        {
                            riders.add(specificPlayer.substring(0, specificPlayer.indexOf(" ")));
                        }
                        else
                        {
                            continue;
                        }
                    }
                }
            }
        }
        return riders;
    }

    private static List<String> getLosers(String summary, String winner, Map activeMap)
    {
        List<String> losers = new ArrayList();
        
        StringTokenizer vote_info = new StringTokenizer(summary, "\n");

        while (vote_info.hasMoreTokens()) {
            String specificVote = vote_info.nextToken();
            if(specificVote.contains("current status"))
            {
                continue;
            }
            else
            {
                String outcome = specificVote.substring(0, specificVote.indexOf(":"));
 
                if(!outcome.equalsIgnoreCase(winner))
                {
                    StringTokenizer winner_info = new StringTokenizer(specificVote, ".");
                    while (winner_info.hasMoreTokens()) {
                        String specificPlayer = winner_info.nextToken();
                        if(!specificPlayer.contains("Total"))
                        {
                            losers.add(specificPlayer.substring(0, specificPlayer.indexOf(" ")));
                        }
                        else
                        {
                            continue;
                        }
                    }
                }
            }
        }
        return losers;
    }


    private static String getWinner(String summary)
    {
        String winner = "";
        StringTokenizer vote_info = new StringTokenizer(summary, ":");
        int currentHighest = 0;
        String outcome = "";
  
        while (vote_info.hasMoreTokens()) {
            String specificVote = vote_info.nextToken();
            if(specificVote.contains("Current status"))
            {
                continue;
            }
            else
            {
                if(!specificVote.contains("Total"))
                {
                    outcome = specificVote;
                    continue;
                }

                String votes = specificVote.substring(13,specificVote.indexOf("."));
                if(Integer.parseInt(votes) >= currentHighest)
                {
                    if(Integer.parseInt(votes) == currentHighest)
                    {
                        winner = winner + "_"+outcome;
                    }
                    else
                    {
                        currentHighest = Integer.parseInt(votes);
                        winner = outcome;
                    }
                    
                }
                outcome = specificVote.substring(specificVote.lastIndexOf(".")+3,specificVote.length());
            }
        }
        return winner;
    }

    private static String getSummaryOfVotes(Map activeMap, boolean capitalize)
    {
        String summary = "";
        HashMap<String, String> outcomes = activeMap.getCurrentAgendaVotes();
        
        if(outcomes.keySet().size() == 0)
        {
            summary = "No current riders or votes have been cast yet.";
        }
        else
        {
            summary = "Current status of votes and outcomes is: \n";
            for(String outcome : outcomes.keySet())
            {
                int totalVotes = 0;
                StringTokenizer vote_info = new StringTokenizer(outcomes.get(outcome), ";");
                String outcomeSummary = "";
                if(capitalize)
                {
                    outcome = StringUtils.capitalize(outcome);
                }
                while (vote_info.hasMoreTokens()) {
                    
                    String specificVote = vote_info.nextToken();
                    String faction = specificVote.substring(0, specificVote.indexOf("_"));
                    if(capitalize)
                    {
                        faction = StringUtils.capitalize(faction);
                    }
                    String vote = specificVote.substring(specificVote.indexOf("_")+1,specificVote.length());
                    if(!vote.contains("Rider"))
                    {
                        totalVotes += Integer.parseInt(vote);
                        outcomeSummary = outcomeSummary + faction +" voted "+ vote + " votes. ";
                    }
                    else
                    {
                        outcomeSummary = outcomeSummary + faction +" cast a "+ vote + ". ";
                    }
                }
                summary = summary + outcome+": Total votes "+totalVotes +". " +outcomeSummary + "\n";   
            }
        }
        return summary;
    }
    public List<Button> getRiderButtons(String ridername, Map activeMap)
    {
        String agendaDetails = activeMap.getCurrentAgendaInfo();
        agendaDetails = agendaDetails.substring(agendaDetails.indexOf("_")+1, agendaDetails.length());
        List<Button> outcomeActionRow = null;
        if(agendaDetails.contains("For"))
        {
            outcomeActionRow = getForAgainstOutcomeButtons(ridername);
        }
        else if(agendaDetails.contains("Player"))
        {
            outcomeActionRow = getPlayerOutcomeButtons(activeMap, ridername, null);
        }
        else if(agendaDetails.contains("Planet"))
        {
            
        }
        else if(agendaDetails.contains("Secret"))
        {
            outcomeActionRow = getSecretOutcomeButtons(activeMap, ridername);
        }
        else if(agendaDetails.contains("Strategy"))
        {
            outcomeActionRow = getStrategyOutcomeButtons(ridername);
        }
        else
        {
            outcomeActionRow = getLawOutcomeButtons(activeMap,ridername);
        }

        return outcomeActionRow;
       
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
        if (buttonID.startsWith(Constants.PO_SCORING)) {
            buttonID = Constants.PO_SCORING;
        } else if((buttonID.startsWith(Constants.SC_FOLLOW) || buttonID.startsWith("sc_no_follow"))) {
            buttonID = Constants.SC_FOLLOW;
        } else if(buttonID.startsWith(Constants.GENERIC_BUTTON_ID_PREFIX)) {
            String buttonText = event.getButton().getLabel();
            event.getInteraction().getMessage().reply("All players have reacted to '" + buttonText + "'").queue();
        }
        switch (buttonID) {
            case Constants.SC_FOLLOW, "sc_no_follow", "sc_refresh", "sc_refresh_and_wash", "trade_primary", "sc_ac_draw", "sc_draw_so","sc_trade_follow", "sc_leadership_follow" -> {
                if(map.isFoWMode())
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
                if(activeMap.getCurrentAgendaInfo() != null)
                {

                    String message = " up to vote! Resolve using buttons. \n \n" + getSummaryOfVotes(activeMap, true);
                    Player nextInLine = ListVoteCount.getNextInLine(null, ListVoteCount.getVotingOrder(activeMap));
                    String realIdentity = "";
                    if(activeMap.isCommunityMode())
                    {
                        if(nextInLine.getRoleForCommunity() == null)
                        {
                            return;
                        }
                        realIdentity = Helper.getRoleMentionByName(event.getGuild(), nextInLine.getRoleForCommunity().getName());
                    }
                    else
                    {
                        realIdentity =Helper.getPlayerRepresentation(nextInLine);
                    }
                    message = realIdentity + message;
                    Button Vote= Button.success("vote", "Choose To Vote");
                    Button Abstain = Button.danger("delete_buttons_0", "Choose To Abstain");
                    ActionRow actionRow4 = ActionRow.of(List.of(Vote, Abstain));
                    MessageCreateBuilder baseMessageObject4 = new MessageCreateBuilder().addContent(message);
                    if (!actionRow4.isEmpty()) baseMessageObject4.addComponents(actionRow4);
                    if(activeMap.isFoWMode())
                    {
                        if(nextInLine.getPrivateChannel() != null)
                        {
                            nextInLine.getPrivateChannel().sendMessage(baseMessageObject4.build()).queue(message4_ -> {});
                            event.getChannel().sendMessage("Notified next in line");
                        }
                    }
                    else
                    {
                        event.getChannel().sendMessage(baseMessageObject4.build()).queue(message4_ -> {});
                    }
                }
               
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
                if(map.isCustodiansScored())
                {
                    new RevealAgenda().revealAgenda(event, false, map, event.getChannel());
                }
                else
                {
                    event.getInteraction().getMessage().reply(Helper.getGamePing(event.getGuild(), map) + " All players have indicated completion of status phase. Proceed to Strategy Phase.").queueAfter(1, TimeUnit.SECONDS);
                }

            }
        }
    }
}