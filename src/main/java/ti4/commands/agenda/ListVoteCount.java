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
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

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
        orderList.addAll(map.getPlayers().values().stream().toList());
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
    }

    public static int getVoteCountFromPlanets(Player player) {
        int baseResourceCount = 0;
        int baseInfluenceCount = 0;
        int voteCount = 0;

        //NEKRO

        //XXCHA

        //KHRASK

        return voteCount;
    }

    public static String getAdditionalVotesFromOtherSources(Map map, Player player) {
        StringBuilder sb = new StringBuilder();

        //Argent Zeal
        if (player.getFactionAbilities().contains("zeal")) {
            long playerCount = map.getPlayers().values().stream().filter(Player::isRealPlayer).count() - 1;
            sb.append("(+" + playerCount + " votes for " + Emojis.Argent + "Zeal)");
        }

        //Xxcha Alliance    //TODO: contains(xxcha) -> contains(playerWithXxchaCommander)
        List<String> playersPNs = player.getPromissoryNotesInPlayArea();
        List<Player> xxchaPlayers = map.getRealPlayers().stream().filter(p -> p.getFaction().equals("xxcha")).toList();
        if (!xxchaPlayers.remove(player) && !xxchaPlayers.isEmpty() && xxchaPlayers.size() == 1) {
            Player xxchaPlayer = xxchaPlayers.get(0);
            Leader xxchaCommander = xxchaPlayer.getLeader(Constants.COMMANDER);
            if (xxchaCommander != null && !xxchaCommander.isLocked()) {
                for (String pn : playersPNs) {
                    if (pn.contains(xxchaPlayer.getColor()) && pn.contains("_an")) {
                        Set<String> planets = new HashSet<>(player.getPlanets());
                        planets.removeAll(player.getExhaustedPlanets());
                        int readyPlanetCount = planets.size();
                        sb.append("(+" + readyPlanetCount + " votes for Xxcha Alliance (+1 vote per planet exhausted))");
                    }
                }
            }
        }

        //Blood Pact
        if (player.getPromissoryNotesInPlayArea().contains("blood_pact")) {
            sb.append("(+4 potential votes for " + Emojis.Empyrean + Emojis.PN + "Blood Pact)");
        }
            
        //Predictive Intelligence
        if (player.getTechs().contains("pi") && !player.getExhaustedTechs().contains("pi")) {
            sb.append(" (+3 votes for " + Emojis.CyberneticTech + "Predictive Intelligence)");
        }

        //Absol Shard of the Throne
        if (CollectionUtils.containsAny(player.getRelics(), List.of("absol_shardofthethrone1", "absol_shardofthethrone2", "absol_shardofthethrone3"))) {
            int count = player.getRelics().stream().filter(s -> s.contains("absol_shardofthethrone")).toList().size(); //  +2 votes per Absol shard
            int additionalVotes = 2 * count;
            sb.append(" (+" + additionalVotes + " votes for (" + count + "x) " + Emojis.Relic + "Shard of the Throne" + Emojis.Absol + ")");
        }

        return sb.toString();
    }
}
