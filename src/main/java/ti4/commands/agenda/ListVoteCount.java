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
        List<Player> orderList = AgendaHelper.getVotingOrder(activeMap);
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

    public static String getPlayerVoteText(Map activeMap, Player player) {
        StringBuilder sb = new StringBuilder();
        int voteCount = getVoteCountFromPlanets(activeMap, player);
        Entry<Integer, String> additionalVotes = getAdditionalVotesFromOtherSources(activeMap, player);

        if (activeMap.isFoWMode()) {
            sb.append(" vote count: **???**");
            return sb.toString();
        } else if (player.hasAbility("galactic_threat") && !activeMap.playerHasLeaderUnlockedOrAlliance(player, "xxchacommander")) {
            sb.append(" NOT VOTING (Galactic Threat)");
            return sb.toString();
        } else if (player.hasLeaderUnlocked("xxchahero")) {
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
        planets.removeAll(player.getExhaustedPlanets());
        HashMap<String, UnitHolder> planetsInfo = activeMap.getPlanetsInfo();
        int baseResourceCount = planets.stream().map(planetsInfo::get).filter(Objects::nonNull).map(planet -> (Planet) planet).mapToInt(Planet::getResources).sum();
        int baseInfluenceCount = planets.stream().map(planetsInfo::get).filter(Objects::nonNull).map(planet -> (Planet) planet).mapToInt(Planet::getInfluence).sum();
        int voteCount = baseInfluenceCount; //default

        //NEKRO unless XXCHA ALLIANCE
        if (player.hasAbility("galactic_threat") && !activeMap.playerHasLeaderUnlockedOrAlliance(player,"xxchacommander")) {
            return 0;
        }

        //XXCHA
        if (player.hasLeaderUnlocked("xxchahero")) {
            voteCount = baseResourceCount + baseInfluenceCount;
            return voteCount;
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
        if (activeMap.playerHasLeaderUnlockedOrAlliance(player, "xxchacommander")) {
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
        if (player.hasTechReady("pi")) {
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

        //Absol's Syncretone - +1 bote for each neighbour
        if (player.hasRelicReady("absol_syncretone")) {
            int count = Helper.getNeighbourCount(activeMap, player);
            sb.append(" (+" + count + " for " + Emojis.Relic + "Syncretone)");
            additionalVotes += count;
        }

        //Ghoti Wayfarer Tech
        if (player.hasTechReady("dsghotg")) {
            int fleetCC = player.getFleetCC();
            sb.append(" (+" + fleetCC + " if Exhaust " + Emojis.BioticTech + "Networked Command)");
            additionalVotes += fleetCC;
        }

        //Edyn Mandate Sigil - Planets in Sigil systems gain +1 vote //INCOMPLETE, POSSIBLY CHANGING ON DS END
        Player edynMechPlayer = Helper.getPlayerFromColorOrFaction(activeMap, "edyn");
        if (edynMechPlayer != null) {
            int count = 0;
            List<Tile> edynMechTiles = activeMap.getTileMap().values().stream().filter(t -> Helper.playerHasMechInSystem(t, activeMap, edynMechPlayer)).toList();
            for (Tile tile : edynMechTiles) {
                for (String planet : tile.getUnitHolders().keySet()) {
                    if (player.getPlanets().contains(planet) && !player.getExhaustedPlanets().contains(planet)) {
                        count++;
                    }
                }
            }
            if (count != 0) {
                sb.append(" (+" + count + " for (" + count + "x) Planets in " + Emojis.edyn + "Sigil Systems)");
                additionalVotes += count;
            }
        }

        return java.util.Map.entry(additionalVotes, sb.toString());
    }
}
