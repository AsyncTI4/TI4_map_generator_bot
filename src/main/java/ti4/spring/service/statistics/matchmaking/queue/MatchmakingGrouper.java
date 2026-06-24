package ti4.spring.service.statistics.matchmaking.queue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import ti4.discord.interactions.buttons.handlers.matchmaking.MatchmakingOptions;
import ti4.settings.users.UserSettings;

@UtilityClass
class MatchmakingGrouper {

    static List<MatchedGame> formGames(
            List<QueuedParty> parties, Map<MatchmakingQueueMember, PlayerMatchData> matchData) {
        List<MatchedGame> gamesToCreate = new ArrayList<>();
        Set<MatchmakingQueueMember> playersAddedToGames = new HashSet<>();

        for (String playerCountOption : MatchmakingOptions.getPlayerCountOptionsDescending()) {
            for (String victoryPointGoalOption : MatchmakingOptions.getShuffledVictoryPointOptions()) {
                for (String expansionOption : MatchmakingOptions.getShuffledExpansionsOptions()) {
                    for (String paceOption : MatchmakingOptions.getShuffledPaceRestrictions()) {
                        matchAndCollect(
                                parties,
                                playersAddedToGames,
                                gamesToCreate,
                                playerCountOption,
                                victoryPointGoalOption,
                                expansionOption,
                                paceOption,
                                matchData);
                    }
                }
            }
        }
        return gamesToCreate;
    }

    private static void matchAndCollect(
            List<QueuedParty> parties,
            Set<MatchmakingQueueMember> playersAddedToGames,
            List<MatchedGame> gamesToCreate,
            String playerCountOption,
            String victoryPointGoalOption,
            String expansionOption,
            String paceOption,
            Map<MatchmakingQueueMember, PlayerMatchData> matchData) {
        int playerCount = Integer.parseInt(playerCountOption);

        List<QueuedParty> remaining = parties.stream()
                .filter(party -> party.members().stream().noneMatch(playersAddedToGames::contains))
                .filter(party -> party.size() <= playerCount)
                .filter(party -> {
                    UserSettings settings = party.leaderSettings();
                    return settings.getMatchmakingPlayerCounts().contains(playerCountOption)
                            && settings.getMatchmakingVictoryPointGoals().contains(victoryPointGoalOption)
                            && settings.getMatchmakingExpansions().contains(expansionOption)
                            && settings.getMatchmakingPaces().contains(paceOption);
                })
                .sorted(prioritizeByQueuedAtTime())
                .collect(Collectors.toCollection(ArrayList::new));

        // Greedy grouping: seed from the front (longest queue time, then longest-waiting first),
        // then fill the game with the first compatible parties found.
        while (totalPlayers(remaining) >= playerCount) {
            QueuedParty seed = remaining.getFirst();
            List<MatchmakingQueueMember> group = new ArrayList<>(seed.members());
            List<QueuedParty> groupParties = new ArrayList<>();
            groupParties.add(seed);

            for (QueuedParty party : remaining) {
                if (groupParties.contains(party)) continue;
                if (group.size() == playerCount) break;
                if (group.size() + party.size() > playerCount) continue;
                if (partyCompatibleWithGroup(party, group, matchData)) {
                    group.addAll(party.members());
                    groupParties.add(party);
                }
            }

            if (group.size() == playerCount) {
                List<String> restrictions = groupParties.stream()
                        .flatMap(party -> party.leaderSettings().getMatchmakingRestrictions().stream())
                        .distinct()
                        .sorted()
                        .toList();
                gamesToCreate.add(new MatchedGame(
                        new ArrayList<>(group),
                        new ArrayList<>(
                                groupParties.stream().map(QueuedParty::party).toList()),
                        playerCountOption,
                        victoryPointGoalOption,
                        expansionOption,
                        paceOption,
                        restrictions));
                playersAddedToGames.addAll(group);
                remaining.removeAll(groupParties);
            } else {
                // This party can't be completed into a full game right now; skip its seed.
                remaining.remove(seed);
            }
        }
    }

    private static Comparator<QueuedParty> prioritizeByQueuedAtTime() {
        return Comparator.comparing((QueuedParty party) ->
                        MatchmakingOptions.getHours(party.leaderSettings().getMatchmakingMaxQueueTime()))
                .reversed()
                .thenComparing(party -> party.party().getQueuedAt());
    }

    private static boolean partyCompatibleWithGroup(
            QueuedParty party,
            List<MatchmakingQueueMember> group,
            Map<MatchmakingQueueMember, PlayerMatchData> matchData) {
        for (MatchmakingQueueMember newMember : party.members()) {
            for (MatchmakingQueueMember existing : group) {
                PlayerMatchData a = matchData.get(existing);
                PlayerMatchData b = matchData.get(newMember);
                boolean relaxed = a.halfQueueTimePassed() || b.halfQueueTimePassed();
                if (MatchmakingCompatibilityService.areIncompatible(a, b, relaxed)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int totalPlayers(List<QueuedParty> parties) {
        return parties.stream().mapToInt(QueuedParty::size).sum();
    }
}
