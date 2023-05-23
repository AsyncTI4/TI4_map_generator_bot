package ti4.commands.agenda;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.Mapper;
import ti4.helpers.AgendaHelper;
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
        Map activeMap = getActiveMap();
        turnOrder(event, activeMap);
    }

    public static void turnOrder(SlashCommandInteractionEvent event, Map activeMap) {
        turnOrder(event, activeMap, event.getChannel());
    }

    public static void turnOrder(GenericInteractionCreateEvent event, Map activeMap, MessageChannel channel) {
        Boolean isPrivateFogGame = FoWHelper.isPrivateGame(event);
        boolean privateGame = isPrivateFogGame != null && isPrivateFogGame;
        String speakerName = activeMap.getSpeaker();
        StringBuilder msg = new StringBuilder();
        int i = 1;
        List<Player> orderList = AgendaHelper.getVotingOrder(activeMap);

        if (!activeMap.isTestBetaFeaturesMode()) {
            for (Player player : orderList) {
                if (!player.isRealPlayer()) {
                    continue;
                }
                List<String> planets = new ArrayList<>(player.getPlanets());
                planets.removeAll(player.getExhaustedPlanets());

                String text = "";
                text += Helper.getPlayerRepresentation(player, activeMap);
                HashMap<String, UnitHolder> planetsInfo = activeMap.getPlanetsInfo();
                boolean bloodPactPn = false;
                boolean hasXxchaAlliance = false;
                int influenceCount = 0;

                if ("mahact".equals(player.getFaction())) {
                    Player xxcha = Helper.getPlayerFromColorOrFaction(activeMap, "xxcha");
                    if (xxcha != null) {
                        if (player.getMahactCC().contains(xxcha.getColor())) {
                            Leader leader = xxcha.getLeader(Constants.COMMANDER);
                            if (leader != null && !leader.isLocked()) {
                                influenceCount += planets.size();

                            }
                        }
                    }
                }
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
                if (player.hasAbility("lithoids")) { //Khrask Faction Ability Lithoids - Vote with RES, not INF

                }
                if (!player.getPromissoryNotesInPlayArea().isEmpty()) {
                    for (String pn : player.getPromissoryNotesInPlayArea()) {
                        String promissoryNoteOwner = Mapper.getPromissoryNoteOwner(pn);
                        for (Player player_ : activeMap.getPlayers().values()) {
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
                if (player.hasAbility("biophobic")) {
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
                        for (Player player_ : activeMap.getPlayers().values()) {
                            if (player_.isRealPlayer()) numPlayers++;
                        }
                        text += " (+" + numPlayers + " votes for Zeal)";
                    }
                    if (bloodPactPn) {
                        text += " (+4 votes for Blood Pact)";
                    }
                    //Predictive Intelligence
                    if (player.getTechs().contains("pi") && !player.getExhaustedTechs().contains("pi")) {
                        text += " (+3 votes for Predictive Intelligence)";
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

        } else { //BETA TEST
            StringBuilder sb = new StringBuilder("**__Vote Count:__**\n");
            int itemNo = 1;
            for (Player player : orderList) {
                sb.append("`").append(itemNo).append(".` ");
                sb.append(Helper.getPlayerRepresentation(player, activeMap));
                if (player.getUserID().equals(activeMap.getSpeaker())) sb.append(Emojis.SpeakerToken);
                sb.append(getPlayerVoteText(activeMap, player));
                sb.append("\n");
                itemNo++;
            }
            MessageHelper.sendMessageToChannel(channel, sb.toString());
        }
    }

    public static String getPlayerVoteText(Map activeMap, Player player) {
        StringBuilder sb = new StringBuilder();
        int voteCount = getVoteCountFromPlanets(activeMap, player);
        Entry<Integer, String> additionalVotes = getAdditionalVotesFromOtherSources(activeMap, player);

        if (activeMap.isFoWMode()) {
            sb.append(" vote count: **???**");
            return sb.toString();
        } else if (player.hasAbility("galactic_threat") && !Helper.playerHasXxchaCommanderUnlocked(activeMap, player)) {
            sb.append(" NOT VOTING (Galactic Threat)");
            return sb.toString();
        } else if (Helper.playerHasXxchaHeroUnlocked(player)) {
            sb.append(" vote count: **" + Emojis.ResInf + " " + voteCount);
        } else if (player.hasAbility("lithoids")) { // Vote with planet resources, no influence
            sb.append(" vote count: **" + Emojis.resources + " " + voteCount);
        } else if (player.hasAbility("biophobic")) {
            sb.append(" vote count: **" + Emojis.SemLor + " " + voteCount);
        } else {
            sb.append(" vote count: **" + Emojis.influence + " " + voteCount);
        }
        if (additionalVotes.getKey() > 0) {
            sb.append(" + " + additionalVotes.getKey() + "** additional votes from: ").append(additionalVotes.getValue());
        } else sb.append("**");

        return sb.toString();
    }

    public static int getTotalVoteCount(Map activeMap, Player player) {
        return getVoteCountFromPlanets(activeMap, player) + getAdditionalVotesFromOtherSources(activeMap, player).getKey();
    }

    public static int getVoteCountFromPlanets(Map activeMap, Player player) {
        List<String> planets = new ArrayList<>(player.getPlanets());
        HashMap<String, UnitHolder> planetsInfo = activeMap.getPlanetsInfo();
        int baseResourceCount = planets.stream().map(planetsInfo::get).filter(Objects::nonNull).map(planet -> (Planet) planet).mapToInt(Planet::getResources).sum();
        int baseInfluenceCount = planets.stream().map(planetsInfo::get).filter(Objects::nonNull).map(planet -> (Planet) planet).mapToInt(Planet::getInfluence).sum();
        int voteCount = baseInfluenceCount; //default

        planets.removeAll(player.getExhaustedPlanets());

        //NEKRO unless XXCHA ALLIANCE
        if (player.hasAbility("galactic_threat") && !Helper.playerHasXxchaCommanderUnlocked(activeMap, player)) {
            return 0;
        }

        //XXCHA
        if (player.getFaction().equals("xxcha")) {
            Leader xxchaHero = player.getLeader("hero");
            if (xxchaHero != null && !xxchaHero.isLocked()) {
                voteCount = baseResourceCount + baseInfluenceCount;
                return voteCount;
            }
        }

        //KHRASK
        if (player.hasAbility("lithoids")) { // Vote with planet resources, no influence
            return baseResourceCount;
        }

        //ZELIAN PURIFIER BIOPHOBIC ABILITY - 1 planet = 1 vote
        if (player.hasAbility("biophobic")) {
            return planets.size();
        }

        return voteCount;
    }

    /**
     * @param activeMap
     * @param player
     * @return (K, V) -> K = additionalVotes / V = text explanation of votes
     */
    public static Entry<Integer, String> getAdditionalVotesFromOtherSources(Map activeMap, Player player) {
        StringBuilder sb = new StringBuilder();
        int additionalVotes = 0;

        //Argent Zeal
        if (player.hasAbility("zeal")) {
            long playerCount = activeMap.getPlayers().values().stream().filter(Player::isRealPlayer).count();
            sb.append("(+" + playerCount + " for " + Emojis.Argent + "Zeal)");
            additionalVotes += playerCount;
        }

        //Xxcha Alliance
        if (Helper.playerHasXxchaCommanderUnlocked(activeMap, player)) {
            Set<String> planets = new HashSet<>(player.getPlanets());
            planets.removeAll(player.getExhaustedPlanets());
            int readyPlanetCount = planets.size();
            sb.append("(+" + readyPlanetCount + " for Xxcha Commander (+1 vote per planet exhausted))");
            additionalVotes += readyPlanetCount;
        }

        //Blood Pact
        if (player.getPromissoryNotesInPlayArea().contains("blood_pact")) {
            sb.append("(+4 potential for " + Emojis.Empyrean + Emojis.PN + "Blood Pact)");
            additionalVotes += 4;
        }

        //Predictive Intelligence
        if (player.getTechs().contains("pi") && !player.getExhaustedTechs().contains("pi")) {
            sb.append(" (+3 for " + Emojis.CyberneticTech + "Predictive Intelligence)");
            additionalVotes += 3;
        }

        //Absol Shard of the Throne
        if (CollectionUtils.containsAny(player.getRelics(), List.of("absol_shardofthethrone1", "absol_shardofthethrone2", "absol_shardofthethrone3"))) {
            int count = player.getRelics().stream().filter(s -> s.contains("absol_shardofthethrone")).toList().size(); //  +2 votes per Absol shard
            int shardVotes = 2 * count;
            sb.append(" (+" + shardVotes + " for (" + count + "x) " + Emojis.Relic + "Shard of the Throne" + Emojis.Absol + ")");
            additionalVotes += shardVotes;
        }

        return java.util.Map.entry(additionalVotes, sb.toString());
    }
}
