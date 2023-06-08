package ti4.helpers;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.util.*;

import org.apache.commons.lang3.StringUtils;

import ti4.generator.Mapper;
import ti4.map.Leader;
import ti4.map.Map;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;


public class AgendaHelper {
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
            button = Button.primary("rider_for_"+rider, "For");
            button2 = Button.danger("rider_against_"+rider, "Against");
        }
        voteButtons.add(button);
        voteButtons.add(button2);
        return voteButtons;
    }

    public static void startTheVoting(Map activeMap, GenericInteractionCreateEvent event) {
        if (activeMap.getCurrentAgendaInfo() != null) {
            String message = " up to vote! Resolve using buttons. \n \n" + AgendaHelper.getSummaryOfVotes(activeMap, true);
            Player nextInLine = AgendaHelper.getNextInLine(null, AgendaHelper.getVotingOrder(activeMap), activeMap);
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
            Button Vote= Button.success("vote", pFaction+" Choose To Vote");
            Button Abstain = Button.danger("delete_buttons_0", pFaction+" Choose To Abstain");
           
            List<Button> buttons = List.of(Vote, Abstain);
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
                button = Button.secondary(prefix+"rider_"+law.getKey()+"_"+rider, law.getKey());
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
                    button = Button.secondary(prefix+"rider_"+so.getKey()+"_"+rider, soName);
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
                button = Button.secondary(prefix+"rider_"+x+"_"+rider, x+"");
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
                button = Button.secondary(prefix+"rider_"+planet+"_"+rider, Helper.getPlanetRepresentation(planet, activeMap));
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
                                button = Button.secondary(prefix+"rider_"+faction+"_"+rider, " ");
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
                                 button = Button.secondary(prefix+"rider_"+player.getColor()+"_"+rider, player.getColor());
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

    public static List<Player> getWinningRiders(String summary, String winner, Map activeMap) {
        List<Player> winningRs = new ArrayList<Player>();
        HashMap<String, String> outcomes = activeMap.getCurrentAgendaVotes();

        for (String outcome : outcomes.keySet()) {
            if (outcome.equalsIgnoreCase(winner)) {
                StringTokenizer vote_info = new StringTokenizer(outcomes.get(outcome), ";");
                while (vote_info.hasMoreTokens()) {
                    String specificVote = vote_info.nextToken();
                    String faction = specificVote.substring(0, specificVote.indexOf("_"));
                    Player winningR = Helper.getPlayerFromColorOrFaction(activeMap, faction.toLowerCase());

                    if (winningR != null && (specificVote.contains("Rider") || winningR.getFaction().equalsIgnoreCase("nomad"))) {

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

    public static int[] getVoteTotal(GenericInteractionCreateEvent event, Player player, Map activeMap) {

        List<String> planets = new ArrayList<>(player.getPlanets());
        planets.removeAll(player.getExhaustedPlanets());
        HashMap<String, UnitHolder> planetsInfo = activeMap.getPlanetsInfo();
        int hasXxchaAlliance = 0;
        int hasXxchaHero = 0;
        int influenceCount = 0;

        int influenceCountFromPlanets = planets.stream().map(planetsInfo::get).filter(Objects::nonNull)
                .map(planet -> (Planet) planet).mapToInt(Planet::getInfluence).sum();
        influenceCount += influenceCountFromPlanets;


        if (player.hasAbility("imperia")) {
            Player xxcha = Helper.getPlayerFromColorOrFaction(activeMap, "xxcha");
            if (xxcha != null) {
                if (player.getMahactCC().contains(xxcha.getColor())) {
                    Leader leader = xxcha.getLeader(Constants.COMMANDER);
                    if (leader != null && !leader.isLocked()) {
                        influenceCount += planets.size();
                        hasXxchaAlliance = 1;
                    }
                }
            }

        }



        if ("xxcha".equals(player.getFaction())) {
            Leader leader = player.getLeader(Constants.COMMANDER);
            if (leader != null && !leader.isLocked()) {
                influenceCount += planets.size();
                hasXxchaAlliance = 1;
            }
            leader = player.getLeader(Constants.HERO);
            if (leader != null && !leader.isLocked()) {
                int influenceCountFromPlanetsRes = planets.stream().map(planetsInfo::get).filter(Objects::nonNull)
                        .map(planet -> (Planet) planet).mapToInt(Planet::getResources).sum();
                influenceCount += influenceCountFromPlanetsRes;
                hasXxchaHero = 1;
            }
        } else if (!player.getPromissoryNotesInPlayArea().isEmpty()) {
            for (String pn : player.getPromissoryNotesInPlayArea()) {
                String promissoryNoteOwner = Mapper.getPromissoryNoteOwner(pn);
                for (Player player_ : activeMap.getPlayers().values()) {
                    if (player_ != player) {
                        String playerColor = player_.getColor();
                        String playerFaction = player_.getFaction();
                        boolean isCorrectPlayer = playerColor != null && playerColor.equals(promissoryNoteOwner) ||
                                playerFaction.equals(promissoryNoteOwner);
                        if ("xxcha".equals(playerFaction) && pn.endsWith("_an")) {
                            if (isCorrectPlayer) {
                                Leader leader = player_.getLeader(Constants.COMMANDER);
                                if (leader != null && !leader.isLocked()) {
                                    influenceCount += planets.size();
                                    hasXxchaAlliance = 1;
                                    break;
                                }
                            }
                        }
                        if ("empyrean".equals(playerFaction) && "blood_pact".equals(pn)) {
                            if (isCorrectPlayer && influenceCount > 0) {
                                influenceCount += 4;
                            }
                        }
                    }
                }
            }
        }


        if ("argent".equals(player.getFaction())) {
            int numPlayers = 0;
            for (Player player_ : activeMap.getPlayers().values()) {
                if (player_.isRealPlayer()) numPlayers++;
            }
            if (influenceCount > 0) {
                influenceCount += numPlayers;
            }

        }
        //Predictive Intelligence
        if (player.hasTechReady("pi")) {
            if (influenceCount > 0) {
                influenceCount += 3;
            }
        }
        if (activeMap.getLaws() != null && (activeMap.getLaws().keySet().contains("rep_govt") || activeMap.getLaws().keySet().contains("absol_government"))) {
            influenceCount = 1;
        }


        if (player.getFaction().equals("nekro") && hasXxchaAlliance == 0) {
            influenceCount = 0;
        }
        List<Player> riders = getRiders(activeMap);
        if (riders.indexOf(player) > -1) {
            if (hasXxchaAlliance == 0) {
                influenceCount = 0;
            }
        }

        int[] voteArray = {influenceCount, hasXxchaHero, hasXxchaAlliance};
        return voteArray;
    }

    public static List<Player> getVotingOrder(Map activeMap) {
        List<Player> orderList = new ArrayList<>();
        orderList.addAll(activeMap.getPlayers().values().stream().filter(p -> p.isRealPlayer()).toList());
        String speakerName = activeMap.getSpeaker();
        Optional<Player> optSpeaker = orderList.stream().filter(player -> player.getUserID().equals(speakerName))
                .findFirst();

        if (optSpeaker.isPresent()) {
            int rotationDistance = orderList.size() - orderList.indexOf(optSpeaker.get()) - 1;
            Collections.rotate(orderList, rotationDistance);
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
        List<String> planets = new ArrayList<>(player.getPlanets());
        planets.removeAll(player.getExhaustedPlanets());
        int[] voteInfo = getVoteTotal(event, player, activeMap);
        HashMap<String, UnitHolder> planetsInfo = activeMap.getPlanetsInfo();
        for (String planet : planets) {
            int voteAmount = 0;
            Planet p = (Planet) planetsInfo.get(planet);
            voteAmount += p.getInfluence();
            if (voteInfo[2] != 0) {
                voteAmount+=1;
            }
            if (voteInfo[1] != 0) {
                voteAmount+=p.getResources();
            }
            if (voteAmount != 0) {
                Button button = Button.secondary("exhaust_"+planet, planet + " ("+voteAmount+")");
                planetButtons.add(button);
            }
        }
        if (player.getFaction().equalsIgnoreCase("argent")) {
            int numPlayers = 0;
            for (Player player_ : activeMap.getPlayers().values()) {
                if (player_.isRealPlayer()) numPlayers++;
            }
            Button button = Button.primary("exhaust_argent", "Special Argent Votes ("+numPlayers+")");
            planetButtons.add(button);
        }
        if (player.hasTechReady("pi")) {
            Button button = Button.primary("exhaust_predictive", "Use Predictive Votes (3)");
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

        HashMap<String, String> outcomes = activeMap.getCurrentAgendaVotes();

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
        if(currentHighest == 0)
        {
            return null;
        }
        return winner;
    }
















}

