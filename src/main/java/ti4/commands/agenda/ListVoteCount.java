package ti4.commands.agenda;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.*;
import ti4.message.MessageHelper;

import java.util.*;
import java.util.Map.Entry;

import org.apache.commons.collections4.CollectionUtils;

public class ListVoteCount extends AgendaSubcommandData {
    public ListVoteCount() {
        super(Constants.VOTE_COUNT, "List Vote count for agenda");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map map = getActiveMap();
        turnOrder(event, map);
    }

    public static void turnOrder(SlashCommandInteractionEvent event, Map map) {
        turnOrder(event, map, event.getChannel());
    }


    public static void turnOrder(GenericInteractionCreateEvent event, Map map, MessageChannel channel) {
        Boolean isPrivateFogGame = FoWHelper.isPrivateGame(event);
        boolean privateGame = isPrivateFogGame != null && isPrivateFogGame;

        StringBuilder msg = new StringBuilder();
        int i = 1;
        List<Player> orderList = new ArrayList<>();
        orderList.addAll(map.getPlayers().values().stream().filter(Player::isRealPlayer).toList());
        String speakerName = map.getSpeaker();
        Optional<Player> optSpeaker = orderList.stream().filter(player -> player.getUserID().equals(speakerName))
                .findFirst();

        if (optSpeaker.isPresent()) {
            int rotationDistance = orderList.size() - orderList.indexOf(optSpeaker.get()) - 1;
            Collections.rotate(orderList, rotationDistance);
        }

        //Check if Argent Flight is in the game - if it is, put it at the front of the vote list.
        Optional<Player> argentPlayer = orderList.stream().filter(player -> player.getFaction().equals("argent")).findFirst();
        if (argentPlayer.isPresent()) {
            orderList.remove(argentPlayer.orElse(null));
            orderList.add(0, argentPlayer.get());
        }

        for (Player player : orderList) {
            if (!player.isRealPlayer()) {
                continue;
            }
            List<String> planets = new ArrayList<>(player.getPlanets());
            planets.removeAll(player.getExhaustedPlanets());

            String text = "";
            text += Helper.getPlayerRepresentation(event, player);
            HashMap<String, UnitHolder> planetsInfo = map.getPlanetsInfo();
            boolean bloodPactPn = false;
            boolean hasXxchaAlliance = false;
            int influenceCount = 0;
            int resourceCount = 0;

            //XXCHA SPECIAL CASE
            if ("xxcha".equals(player.getFaction())) {
                // add planet count if xxcha commander unlocked
                Leader leader = player.getLeader(Constants.COMMANDER);
                if (leader != null && !leader.isLocked()) {
                    influenceCount += planets.size();
                }

                // add resources if xxcha hero unlocked
                leader = player.getLeader(Constants.HERO);
                if (leader != null && !leader.isLocked()) {
                    int influenceCountFromPlanetsRes = planets.stream().map(planetsInfo::get).filter(Objects::nonNull)
                            .map(planet -> (Planet) planet).mapToInt(Planet::getResources).sum();
                    influenceCount += influenceCountFromPlanetsRes;
                }
            }
            if (player.getFactionAbilities().contains("lithoids")) { //Khrask Faction Ability Lithoids - Vote with RES, not INF

            }
            if (!player.getPromissoryNotesInPlayArea().isEmpty()) {
                for (String pn : player.getPromissoryNotesInPlayArea()) {
                    String promissoryNoteOwner = Mapper.getPromissoryNoteOwner(pn);
                    for (Player player_ : map.getPlayers().values()) {
                        if (player_ != player) {
                            String playerColor = player_.getColor();
                            String playerFaction = player_.getFaction();
                            boolean isCorrectPlayer = playerColor != null && playerColor.equals(promissoryNoteOwner) ||
                                    playerFaction.equals(promissoryNoteOwner);

                            // add planet count if xxcha commander unlocked
                            if ("xxcha".equals(playerFaction) && pn.endsWith("_an")) {
                                if (isCorrectPlayer) {
                                    Leader leader = player_.getLeader(Constants.COMMANDER);
                                    if (leader != null && !leader.isLocked()) {
                                        influenceCount += planets.size();
                                        hasXxchaAlliance = true;
                                        break;
                                    }
                                }
                            }

                            // add potential +votes if player has blood pact in player area
                            if ("empyrean".equals(playerFaction) && "blood_pact".equals(pn)) {
                                if (isCorrectPlayer) {
                                    bloodPactPn = true;
                                }
                            }
                        }
                    }
                }
            }
            int influenceCountFromPlanets = planets.stream().map(planetsInfo::get).filter(Objects::nonNull)
                    .map(planet -> (Planet) planet).mapToInt(Planet::getInfluence).sum();
            influenceCount += influenceCountFromPlanets;
            
            //ZELIAN PURIFIER BIOPHOBIC ABILITY - 1 planet = 1 vote
            if (player.getFactionAbilities().contains("biophobic")) {
                influenceCount = planets.size();
            }

            if (privateGame) {
                text += " vote count: **???";
            } else if (player.getFaction().equals("nekro") && !hasXxchaAlliance) {
                text += " NOT VOTING.: **0";
            } else {
                text += " vote count: **" + influenceCount;
                if ("argent".equals(player.getFaction())) {
                    int numPlayers = 0;
                    for (Player player_ : map.getPlayers().values()) {
                        if (player_.isRealPlayer()) numPlayers++;
                    }
                    text += " (+" + numPlayers + " votes for Zeal)";
                }
                if (bloodPactPn) {
                    text += " (+4 votes for Blood Pact)";
                }
            }

            text += "**";
            if (!privateGame && player.getUserID().equals(speakerName)) {
                text += " " + Emojis.SpeakerToken;
            }
            msg.append(i).append(". ").append(text).append("\n");
            i++;
        }
        MessageHelper.sendMessageToChannel(channel, msg.toString());



        StringBuilder sb = new StringBuilder("**__Vote Count:__**\n");
        int itemNo = 1;
        for (Player player : orderList) {
            int voteCount = getVoteCountFromPlanets(map, player);
            Entry<Integer, String> additionalVotes = getAdditionalVotesFromOtherSources(map, player);
            
            sb.append("> `").append(itemNo).append(".` ");
            sb.append(Helper.getPlayerRepresentation(event, player));
            if (player.getUserID().equals(map.getSpeaker())) sb.append(Emojis.SpeakerToken);

            if (privateGame) {
                sb.append(" vote count: **???***");
            } else if (player.getFactionAbilities().contains("galactic_threat") && !Helper.playerHasXxchaCommanderUnlocked(map, player)) {
                sb.append(" NOT VOTING.: **0*** (Galactic Threat)");
            } else {
                sb.append(" vote count: **" + voteCount);
                if (additionalVotes.getKey() > 0) {
                    sb.append(" + " + additionalVotes.getKey() + "** additional votes from: ").append(additionalVotes.getValue());
                } else sb.append("**");
            }
            sb.append("\n");
            itemNo++;
        }
        MessageHelper.sendMessageToChannel(channel, sb.toString());
    }

    public static int getVoteCountFromPlanets(Map map, Player player) {
        List<String> planets = new ArrayList<>(player.getPlanets());
        HashMap<String, UnitHolder> planetsInfo = map.getPlanetsInfo();
        int baseResourceCount = planets.stream().map(planetsInfo::get).filter(Objects::nonNull).map(planet -> (Planet) planet).mapToInt(Planet::getResources).sum();
        int baseInfluenceCount = planets.stream().map(planetsInfo::get).filter(Objects::nonNull).map(planet -> (Planet) planet).mapToInt(Planet::getInfluence).sum();
        int voteCount = baseInfluenceCount; //default

        planets.removeAll(player.getExhaustedPlanets());

        //NEKRO unless XXCHA ALLIANCE
        if (player.getFactionAbilities().contains("galactic_threat") && !Helper.playerHasXxchaCommanderUnlocked(map, player)) {
            return 0;
        }

        //XXCHA
        if (player.getFaction().equals("xxcha")) {
            Leader xxchaHero = player.getLeader("hero");
            if (xxchaHero != null && !xxchaHero.isLocked()) {
                voteCount = baseResourceCount + baseInfluenceCount;
            }
        }

        //KHRASK
        if (player.getFactionAbilities().contains("lithoids")) { // Vote with planet resources, no influence
            return baseResourceCount;
        }

        //ZELIAN PURIFIER BIOPHOBIC ABILITY - 1 planet = 1 vote
        if (player.getFactionAbilities().contains("biophobic")) {
            return planets.size();
        }

        return voteCount;
    }

    /**
     * @param map
     * @param player
     * @return (K, V) -> K = additionalVotes / V = text explanation of votes
     */
    public static Entry<Integer, String> getAdditionalVotesFromOtherSources(Map map, Player player) {
        StringBuilder sb = new StringBuilder();
        int additionalVotes = 0;

        //Argent Zeal
        if (player.getFactionAbilities().contains("zeal")) {
            long playerCount = map.getPlayers().values().stream().filter(Player::isRealPlayer).count() - 1;
            sb.append("(+" + playerCount + " votes for " + Emojis.Argent + "Zeal)");
            additionalVotes += playerCount;
        }

        //Xxcha Alliance   
        if (Helper.playerHasXxchaCommanderUnlocked(map, player)) {
            Set<String> planets = new HashSet<>(player.getPlanets());
            planets.removeAll(player.getExhaustedPlanets());
            int readyPlanetCount = planets.size();
            sb.append("(+" + readyPlanetCount + " votes for Xxcha Alliance (+1 vote per planet exhausted))");
            additionalVotes += readyPlanetCount;
        }

        //Blood Pact
        if (player.getPromissoryNotesInPlayArea().contains("blood_pact")) {
            sb.append("(+4 potential votes for " + Emojis.Empyrean + Emojis.PN + "Blood Pact)");
            additionalVotes += 4;
        }
            
        //Predictive Intelligence
        if (player.getTechs().contains("pi") && !player.getExhaustedTechs().contains("pi")) {
            sb.append(" (+3 votes for " + Emojis.CyberneticTech + "Predictive Intelligence)");
            additionalVotes += 3;
        }

        //Absol Shard of the Throne
        if (CollectionUtils.containsAny(player.getRelics(), List.of("absol_shardofthethrone1", "absol_shardofthethrone2", "absol_shardofthethrone3"))) {
            int count = player.getRelics().stream().filter(s -> s.contains("absol_shardofthethrone")).toList().size(); //  +2 votes per Absol shard
            int shardVotes = 2 * count;
            sb.append(" (+" + shardVotes + " votes for (" + count + "x) " + Emojis.Relic + "Shard of the Throne" + Emojis.Absol + ")");
            additionalVotes += shardVotes;
        }

        return java.util.Map.entry(additionalVotes, sb.toString());
    }
}
