package ti4.helpers;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonInteraction;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.ObjectUtils.Null;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import ti4.commands.cardsac.ACInfo_Legacy;
import ti4.generator.Mapper;
import ti4.generator.PositionMapper;
import ti4.map.Leader;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;


public class AgendaHelper {
    public static List<Button> getVoteButtons(int minVote, int voteTotal) {
        List<Button> voteButtons = new ArrayList<>();
        
        if(voteTotal-minVote > 20)
        {
            for(int x = 10; x < voteTotal+10; x += 10)
            {
                int y = x-9;
                int z = x;
                if(x > voteTotal)
                {
                    z = voteTotal;
                    y = z-9;
                }
                Button button = Button.secondary("fixerVotes_"+z, y+"-"+z);
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

    public static List<Button> getForAgainstOutcomeButtons(String rider, String prefix) {
        List<Button> voteButtons = new ArrayList<>();
        Button button = null;
        Button button2 = null;
        if(rider == null)
        {
            button = Button.secondary(prefix+"_for", "For");
            button2 = Button.danger(prefix+"_against", "Against");
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

    public static List<Button> getLawOutcomeButtons(Map activeMap, String rider, String prefix) {
        List<Button> lawButtons = new ArrayList<>();
        for(java.util.Map.Entry<String, Integer> law : activeMap.getLaws().entrySet())
        {
            //String id :  activeMap.getLaws().keySet()
            //String[] agendaDetails = Mapper.getAgenda(id).split(";");
           // String agendaName = agendaDetails[0];
           Button button = null;
           if(rider == null)
           {
                button = Button.secondary(prefix+"_"+law.getKey(), law.getKey());
           }
           else
           {
                button = Button.secondary("rider_"+law.getKey()+"_"+rider, law.getKey());
           }
            lawButtons.add(button);
        }
        return lawButtons;
    }







    public static List<Button> getSecretOutcomeButtons(Map activeMap, String rider, String prefix) {
        List<Button> secretButtons = new ArrayList<>();
        for(Player player :  activeMap.getPlayers().values())
        {
            for(java.util.Map.Entry<String, Integer> so : player.getSecretsScored().entrySet())
            {
                Button button = null;
                if(rider == null)
                {
                    button = Button.secondary(prefix+"_"+so.getKey(), so.getKey()+"");
                }
                else
                {
                    button = Button.secondary(prefix+"rider_"+so.getKey()+"_"+rider, so.getKey()+"");
                }
                secretButtons.add(button);
            }
        }
        return secretButtons;
    }









    public static List<Button> getStrategyOutcomeButtons(String rider, String prefix) {
        List<Button> strategyButtons = new ArrayList<>();
        for(int x = 1; x < 9; x++)
        {
            Button button = null;
            if(rider == null)
           {
                button = Button.secondary(prefix+"_"+x, x+"");
           }
           else
           {
                button = Button.secondary("rider_"+x+"_"+rider, x+"");
           }
            strategyButtons.add(button);  
        }
        return strategyButtons;
    }



    public static List<Button> getPlanetOutcomeButtons(GenericInteractionCreateEvent event, Player player, Map map) {
        List<Button> planetOutcomeButtons = new ArrayList<>();
        List<String> planets = new ArrayList<>(player.getPlanets());
        
        for (String planet : planets) {
            
            Button button = Button.secondary("outcome_"+planet, planet);
            planetOutcomeButtons.add(button);
        }
        return planetOutcomeButtons;
    }


    public static List<Button> getPlayerOutcomeButtons(Map activeMap, String rider, String prefix) {
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
                            
                            button = Button.secondary(prefix+"_"+faction, " ");
                        }
                        String factionEmojiString = Helper.getFactionIconFromDiscord(faction);
                        button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                    }
                    else
                    {
                        if(rider != null)
                        {
                            button = Button.secondary("rider_"+player.getColor()+"_"+rider, player.getColor());
                        }
                        else
                        {
                            button = Button.secondary(prefix+"_"+player.getColor(), player.getColor());
                            
                        }
                    }
                    playerOutcomeButtons.add(button);
                }
            }
        }
        return playerOutcomeButtons;
    }

    public static List<Button> getRiderButtons(String ridername, Map activeMap)
    {
        String agendaDetails = activeMap.getCurrentAgendaInfo();
        agendaDetails = agendaDetails.substring(agendaDetails.indexOf("_")+1, agendaDetails.length());
        List<Button> outcomeActionRow = null;
        if(agendaDetails.contains("For"))
        {
            outcomeActionRow = getForAgainstOutcomeButtons(ridername, "outcome");
        }
        else if(agendaDetails.contains("Player") || agendaDetails.contains("player"))
        {
            outcomeActionRow = getPlayerOutcomeButtons(activeMap, ridername, "outcome");
        }
        else if(agendaDetails.contains("Planet") || agendaDetails.contains("planet"))
        {
            MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(), "Sorry, the bot will not track riders played on planet outcomes");
        }
        else if(agendaDetails.contains("Secret") || agendaDetails.contains("secret"))
        {
            outcomeActionRow = getSecretOutcomeButtons(activeMap, ridername, "outcome");
        }
        else if(agendaDetails.contains("Strategy") || agendaDetails.contains("strategy"))
        {
            outcomeActionRow = getStrategyOutcomeButtons(ridername, "outcome");
        }
        else
        {
            outcomeActionRow = getLawOutcomeButtons(activeMap, ridername, "outcome");
        }

        return outcomeActionRow;
       
    }

    public static List<Player> getWinningRiders(String summary, String winner, Map activeMap)
    {
        List<Player> winningRs = new ArrayList<Player>();
        HashMap<String, String> outcomes = activeMap.getCurrentAgendaVotes();
        
        for(String outcome : outcomes.keySet())
        {
            if(outcome.equalsIgnoreCase(winner))
            {
                StringTokenizer vote_info = new StringTokenizer(outcomes.get(outcome), ";"); 
                while (vote_info.hasMoreTokens()) {
                    String specificVote = vote_info.nextToken();
                    String faction = specificVote.substring(0, specificVote.indexOf("_"));
                    Player winningR = Helper.getPlayerFromColorOrFaction(activeMap, faction.toLowerCase());

                    if(winningR != null && (specificVote.contains("Rider") || winningR.getFaction().equalsIgnoreCase("nomad")))
                    {
                        
                        winningRs.add(winningR);
                        
                    }
                       
                }
            }
        }
        return winningRs;
    }

    public static List<Player> getRiders(Map activeMap)
    {
        
        List<Player> riders = new ArrayList<Player>();
        
        HashMap<String, String> outcomes = activeMap.getCurrentAgendaVotes();
        
        for(String outcome : outcomes.keySet())
        {
            int totalVotes = 0;
            StringTokenizer vote_info = new StringTokenizer(outcomes.get(outcome), ";");
            
            while (vote_info.hasMoreTokens()) {
                String specificVote = vote_info.nextToken();
                String faction = specificVote.substring(0, specificVote.indexOf("_"));
                String vote = specificVote.substring(specificVote.indexOf("_")+1,specificVote.length());
                if(vote.contains("Rider") || vote.contains("Sanction"))
                {
                    Player rider = Helper.getPlayerFromColorOrFaction(activeMap, faction.toLowerCase());
                    if(rider != null)
                    {
                        riders.add(rider);
                    }
                }
                    
            }

        }
        return riders;
    }


    public static List<Player> getLosers(String winner, Map activeMap)
    {
        List<Player> losers = new ArrayList<Player>();
        HashMap<String, String> outcomes = activeMap.getCurrentAgendaVotes();
        
        for(String outcome : outcomes.keySet())
        {
            if(!outcome.equalsIgnoreCase(winner))
            {
                StringTokenizer vote_info = new StringTokenizer(outcomes.get(outcome), ";");
                
                while (vote_info.hasMoreTokens()) {
                    String specificVote = vote_info.nextToken();
                    String faction = specificVote.substring(0, specificVote.indexOf("_"));
                    Player loser = Helper.getPlayerFromColorOrFaction(activeMap, faction.toLowerCase());
                    if(loser != null)
                    {
                        losers.add(loser);
                    }     
                }
            }
        }
        return losers;
    }

    public static int[] getVoteTotal(GenericInteractionCreateEvent event, Player player, Map map)
    {

        List<String> planets = new ArrayList<>(player.getPlanets());
        planets.removeAll(player.getExhaustedPlanets());
        HashMap<String, UnitHolder> planetsInfo = map.getPlanetsInfo();
        int hasXxchaAlliance = 0;
        int hasXxchaHero = 0;
        int influenceCount = 0;
     
        int influenceCountFromPlanets = planets.stream().map(planetsInfo::get).filter(Objects::nonNull)
                .map(planet -> (Planet) planet).mapToInt(Planet::getInfluence).sum();
        influenceCount += influenceCountFromPlanets;


        if (player.hasAbility("imperia")) {
            Player xxcha = Helper.getPlayerFromColorOrFaction(map, "xxcha");
            if(xxcha != null)
            {
                if(player.getMahactCC().contains(xxcha.getColor()))
                {
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
                for (Player player_ : map.getPlayers().values()) {
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
            for (Player player_ : map.getPlayers().values()) {
                if (player_.isRealPlayer()) numPlayers++;
            }
            if(influenceCount > 0)
            {
                influenceCount += numPlayers;
            }
            
        }
        //Predictive Intelligence
        if (player.getTechs().contains("pi") && !player.getExhaustedTechs().contains("pi")) {
            if(influenceCount > 0)
            {
                influenceCount += 3;
            }
        }
        if (player.getFaction().equals("nekro") && hasXxchaAlliance == 0) 
        {
            influenceCount = 0;
        }
        List<Player> riders = getRiders(map);
        if(riders.indexOf(player) > -1)
        {
            if(hasXxchaAlliance == 0)
            {
                influenceCount = 0;
            }
        }
        
        int[] voteArray = {influenceCount, hasXxchaHero, hasXxchaAlliance};
        return voteArray;
    }

    public static List<Player> getVotingOrder(Map map)
    {
        List<Player> orderList = new ArrayList<>();
        orderList.addAll(map.getPlayers().values().stream().toList());
        String speakerName = map.getSpeaker();
        Optional<Player> optSpeaker = orderList.stream().filter(player -> player.getUserID().equals(speakerName))
                .findFirst();

        if (optSpeaker.isPresent()) {
            int rotationDistance = orderList.size() - orderList.indexOf(optSpeaker.get()) - 1;
            Collections.rotate(orderList, rotationDistance);
        }
        if(map.getHackElectionStatus())
        {
            Collections.reverse(orderList);
            if (optSpeaker.isPresent()) {
                int rotationDistance = orderList.size() - orderList.indexOf(optSpeaker.get()) - 1;
                Collections.rotate(orderList, rotationDistance);
            }
        }

        //Check if Argent Flight is in the game - if it is, put it at the front of the vote list.
        Optional<Player> argentPlayer = orderList.stream().filter(player -> player.getFaction()!= null && player.getFaction().equals("argent")).findFirst();
        if (argentPlayer.isPresent()) {
            orderList.remove(argentPlayer.orElse(null));
            orderList.add(0, argentPlayer.get());
        }
        return orderList;
    }
    
    public static Player getNextInLine(Player player1, List<Player> votingOrder, Map activeMap)
    {
        boolean foundPlayer = false;
        if (player1 == null)
        {
            for(int x = 0; x < 6; x++)
            {
                if(x < votingOrder.size())
                {
                    Player player = votingOrder.get(x);
                    if(player != null && !player.isDummy())
                    {
                        return player;
                    }
                }
                
            }
            return null;
            
        }
        for(Player player2 : votingOrder)
        {
            if(player2 == null || player2.isDummy())
            {
                continue;
            }
            if(foundPlayer && player2.isRealPlayer())
            {
                return player2;
            }
            if(player1.getColor().equalsIgnoreCase(player2.getColor()))
            {
                foundPlayer = true;
            }
        }
        
        return player1;
    }


    public static List<Button> getPlanetButtons(GenericInteractionCreateEvent event, Player player, Map map) {
        List<Button> planetButtons = new ArrayList<>();
        List<String> planets = new ArrayList<>(player.getPlanets());
        planets.removeAll(player.getExhaustedPlanets());
        int[] voteInfo = getVoteTotal(event, player, map);
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


    public static List<Button> getPlanetRefreshButtons(GenericInteractionCreateEvent event, Player player, Map map) {
        List<Button> planetButtons = new ArrayList<>();
        List<String> planets = new ArrayList<>(player.getExhaustedPlanets());
        for (String planet : planets) {
            Button button = Button.success("refresh_"+planet, StringUtils.capitalize(planet) + "("+ Helper.getPlanetResources(planet, map)+"/"+Helper.getPlanetInfluence(planet, map) + ")");
            planetButtons.add(button);     
        }
        
        return planetButtons;
    }

    public static String getSummaryOfVotes(Map activeMap, boolean capitalize)
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
                        if(activeMap.isFoWMode())
                        {
                            faction = "Someone";
                        }
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

    public static List<Button> getAgendaResButtons(Map activeMap)
    {

        String agendaDetails = activeMap.getCurrentAgendaInfo();
        agendaDetails = agendaDetails.substring(agendaDetails.indexOf("_")+1, agendaDetails.length());
        List<Button> outcomeActionRow = null;
        if(agendaDetails.contains("For") || agendaDetails.contains("for"))
        {
            outcomeActionRow = getForAgainstOutcomeButtons(null, "agendaResolution");
        }
        else if(agendaDetails.contains("Player") || agendaDetails.contains("player"))
        {
            outcomeActionRow = getPlayerOutcomeButtons(activeMap, null, "agendaResolution");
        }
        else if(agendaDetails.contains("Planet") || agendaDetails.contains("planet"))
        {
            MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(), "Sorry, the bot will resolve all possible planets rn.");
        }
        else if(agendaDetails.contains("Secret") || agendaDetails.contains("secret"))
        {
            outcomeActionRow = getSecretOutcomeButtons(activeMap, null, "agendaResolution");
        }
        else if(agendaDetails.contains("Strategy") || agendaDetails.contains("strategy"))
        {
            outcomeActionRow = getStrategyOutcomeButtons(null, "agendaResolution");
        }
        else
        {
            outcomeActionRow = getLawOutcomeButtons(activeMap, null, "agendaResolution");
        }

        return outcomeActionRow;
    }
    

    public static String getWinner(String summary)
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
                    outcome = specificVote.substring(2, specificVote.length());
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
















}

