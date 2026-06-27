package ti4.spring.service.statistics.matchmaking.queue;

import static java.util.function.Predicate.not;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import ti4.discord.interactions.buttons.handlers.matchmaking.MatchmakingOptions;
import ti4.logging.BotLogger;
import ti4.settings.users.UserSettings;

@UtilityClass
class MatchmakingGrouper {

    static List<MatchedGame> formGames(List<QueuedParty> parties) {
        List<MatchedGame> gamesToCreate = new ArrayList<>();
        Set<QueuedParty> partiesAddedToGames = new HashSet<>();

        Map<MatchmakingQueueMember, PlayerMatchmakingData> playerMatchmakingData =
                PlayerMatchDataFactory.buildForParties(parties);
        Map<GameConfig, List<QueuedParty>> partiesByConfig = groupPartiesByConfig(parties);

        List<GameConfig> configsToTry = getRandomizedKeysThenSortByPlayerCount(partiesByConfig);
        for (GameConfig config : configsToTry) {
            matchAndCollect(
                    partiesByConfig.get(config), partiesAddedToGames, gamesToCreate, config, playerMatchmakingData);
        }
        return gamesToCreate;
    }

    private static List<GameConfig> getRandomizedKeysThenSortByPlayerCount(
            Map<GameConfig, List<QueuedParty>> partiesByConfig) {
        List<GameConfig> configsToTry = new ArrayList<>(partiesByConfig.keySet());
        Collections.shuffle(configsToTry);
        configsToTry.sort(Comparator.comparingInt(GameConfig::playerCountValue).reversed());
        return configsToTry;
    }

    private static Map<GameConfig, List<QueuedParty>> groupPartiesByConfig(List<QueuedParty> parties) {
        Map<GameConfig, List<QueuedParty>> partiesByConfig = new HashMap<>();
        for (QueuedParty party : parties) {
            UserSettings settings = party.leaderSettings();
            for (String playerCountOption : settings.getMatchmakingPlayerCounts()) {
                if (party.size() > Integer.parseInt(playerCountOption)) continue;
                for (String victoryPointGoalOption : settings.getMatchmakingVictoryPointGoals()) {
                    for (String expansionOption : settings.getMatchmakingExpansions()) {
                        for (String paceOption : settings.getMatchmakingPaces()) {
                            GameConfig config = new GameConfig(
                                    playerCountOption, victoryPointGoalOption, expansionOption, paceOption);
                            partiesByConfig
                                    .computeIfAbsent(config, _ -> new ArrayList<>())
                                    .add(party);
                        }
                    }
                }
            }
        }
        return partiesByConfig;
    }

    private static void matchAndCollect(
            List<QueuedParty> parties,
            Set<QueuedParty> partiesAddedToGames,
            List<MatchedGame> gamesToCreate,
            GameConfig config,
            Map<MatchmakingQueueMember, PlayerMatchmakingData> playerMatchmakingData) {
        List<QueuedParty> partiesStillSearching = parties.stream()
                .filter(not(partiesAddedToGames::contains))
                .sorted(prioritizeByQueuedAtTime())
                .collect(Collectors.toCollection(ArrayList::new));
        int playerCount = config.playerCountValue();
        // Greedy grouping: seed from the front (longest queue time),
        // then fill the game with the first compatible parties found.
        while (totalPlayers(partiesStillSearching) >= playerCount) {
            QueuedParty seed = partiesStillSearching.getFirst();
            List<MatchmakingQueueMember> matchMembers = new ArrayList<>(seed.members());
            List<QueuedParty> matchParties = new ArrayList<>();
            matchParties.add(seed);

            for (QueuedParty party : partiesStillSearching) {
                if (matchParties.contains(party)) continue;
                if (matchMembers.size() == playerCount) break;
                if (matchMembers.size() + party.size() > playerCount) continue;
                if (isPartyCompatibleForMatch(party, matchMembers, playerMatchmakingData, config)) {
                    matchMembers.addAll(party.members());
                    matchParties.add(party);
                }
            }

            if (matchMembers.size() != playerCount) {
                // This party can't find a game of this type right now; skip it.
                partiesStillSearching.remove(seed);
                continue;
            }

            List<String> matchRestrictions = matchParties.stream()
                    .flatMap(party -> party.leaderSettings().getMatchmakingRestrictions().stream())
                    .distinct()
                    .sorted()
                    .toList();
            gamesToCreate.add(new MatchedGame(
                    new ArrayList<>(matchMembers),
                    new ArrayList<>(
                            matchParties.stream().map(QueuedParty::party).toList()),
                    config.playerCount(),
                    config.victoryPointGoal(),
                    config.expansion(),
                    config.pace(),
                    matchRestrictions));
            logMatchFormed(config, matchMembers, playerMatchmakingData);
            partiesAddedToGames.addAll(matchParties);
            partiesStillSearching.removeAll(matchParties);
        }
    }

    private static void logMatchFormed(
            GameConfig config,
            List<MatchmakingQueueMember> matchMembers,
            Map<MatchmakingQueueMember, PlayerMatchmakingData> playerMatchmakingData) {
        String playerDetails = matchMembers.stream()
                .map(playerMatchmakingData::get)
                .map(String::valueOf)
                .collect(Collectors.joining("\n  "));
        BotLogger.info("Matchmaking formed a %d-player game (%s VP, %s, %s):%n  %s"
                .formatted(
                        config.playerCountValue(),
                        config.victoryPointGoal(),
                        config.expansion(),
                        config.pace(),
                        playerDetails));
    }

    private static Comparator<QueuedParty> prioritizeByQueuedAtTime() {
        return Comparator.comparing((QueuedParty party) ->
                        MatchmakingOptions.getHours(party.leaderSettings().getMatchmakingMaxQueueTime()))
                .reversed()
                .thenComparing(queuedParty -> queuedParty.party().getQueuedAt());
    }

    private static boolean isPartyCompatibleForMatch(
            QueuedParty party,
            List<MatchmakingQueueMember> group,
            Map<MatchmakingQueueMember, PlayerMatchmakingData> matchData,
            GameConfig config) {
        for (MatchmakingQueueMember newMember : party.members()) {
            for (MatchmakingQueueMember existing : group) {
                PlayerMatchmakingData a = matchData.get(existing);
                PlayerMatchmakingData b = matchData.get(newMember);
                if (MatchmakingCompatibilityService.areIncompatible(a, b, config.expansion())) {
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
