package ti4.spring.service.statistics.matchmaking.queue;

import static java.util.function.Predicate.not;

import java.time.Duration;
import java.time.Instant;
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
import ti4.settings.users.UserSettings;

@UtilityClass
class MatchmakingGrouper {

    private static final Duration NEAR_QUEUE_EXPIRY_HOURS = Duration.ofHours(1);
    private static final Duration LONG_QUEUE_DURATION = Duration.ofHours(24);
    private static final int MIN_PLAYERS_FOR_NEAR_MATCH = 3;

    static List<MatchedGame> formGames(List<QueuedParty> parties) {
        List<MatchedGame> gamesToCreate = new ArrayList<>();
        Set<QueuedParty> partiesAddedToGames = new HashSet<>();

        Map<MatchmakingQueueMember, PlayerMatchmakingData> playerMatchmakingData =
                PlayerMatchmakingDataFactory.buildForParties(parties);
        Map<GameConfig, List<QueuedParty>> partiesByConfig = groupPartiesByConfig(parties);
        List<GameConfig> configsToTry = getRandomizedKeysThenSortByPlayerCount(partiesByConfig);

        for (GameConfig config : configsToTry) {
            List<QueuedParty> available = ungrouped(partiesByConfig.get(config), partiesAddedToGames);
            for (List<QueuedParty> group :
                    formGroupsOfSize(available, config.playerCountValue(), playerMatchmakingData)) {
                collectGames(group, config, playerMatchmakingData, gamesToCreate, partiesAddedToGames);
            }
        }

        formNearMatches(configsToTry, partiesByConfig, partiesAddedToGames, gamesToCreate, playerMatchmakingData);
        return gamesToCreate;
    }

    // Detect candidates from the parties that ended up in no full game; a candidate is a compatible
    // group one player short of a full game that either has a player who waited the full long-queue
    // duration, or has a near-expiry player.
    private static void formNearMatches(
            List<GameConfig> configsToTry,
            Map<GameConfig, List<QueuedParty>> partiesByConfig,
            Set<QueuedParty> partiesAddedToGames,
            List<MatchedGame> gamesToCreate,
            Map<MatchmakingQueueMember, PlayerMatchmakingData> playerMatchmakingData) {
        Instant now = Instant.now();
        List<NearMatchCandidate> candidates = new ArrayList<>();
        for (GameConfig config : configsToTry) {
            int targetSize = config.playerCountValue() - 1;
            if (targetSize < 1) continue;
            List<QueuedParty> available = ungrouped(partiesByConfig.get(config), partiesAddedToGames);
            for (List<QueuedParty> group : formGroupsOfSize(available, targetSize, playerMatchmakingData)) {
                if (qualifiesAsNearMatch(group, now)) {
                    candidates.add(new NearMatchCandidate(config, group));
                }
            }
        }

        candidates.sort(Comparator.comparingInt((NearMatchCandidate candidate) -> totalPlayers(candidate.parties()))
                .reversed()
                .thenComparing(candidate -> getEarliestExpiry(candidate.parties())));
        for (NearMatchCandidate candidate : candidates) {
            if (candidate.parties().stream().anyMatch(partiesAddedToGames::contains)) continue;
            collectGames(
                    candidate.parties(), candidate.config(), playerMatchmakingData, gamesToCreate, partiesAddedToGames);
        }
    }

    private static boolean qualifiesAsNearMatch(List<QueuedParty> group, Instant now) {
        boolean longestQueuedWaitedLongEnough = group.stream().anyMatch(party -> hasLongQueueDuration(party, now));
        boolean nearExpiryAndHasMinimumPlayerCount = totalPlayers(group) >= MIN_PLAYERS_FOR_NEAR_MATCH
                && group.stream().anyMatch(party -> isNearExpiry(party, now));
        return longestQueuedWaitedLongEnough || nearExpiryAndHasMinimumPlayerCount;
    }

    private static boolean hasLongQueueDuration(QueuedParty queuedParty, Instant now) {
        Duration queuedFor = Duration.between(queuedParty.party().getQueuedAt(), now);
        return queuedFor.compareTo(LONG_QUEUE_DURATION) >= 0;
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

    private static List<List<QueuedParty>> formGroupsOfSize(
            List<QueuedParty> available,
            int targetSize,
            Map<MatchmakingQueueMember, PlayerMatchmakingData> playerMatchmakingData) {
        List<List<QueuedParty>> groups = new ArrayList<>();
        List<QueuedParty> searching =
                available.stream().sorted(prioritizeByQueuedAtTime()).collect(Collectors.toCollection(ArrayList::new));
        while (totalPlayers(searching) >= targetSize) {
            QueuedParty seed = searching.getFirst();
            List<MatchmakingQueueMember> members = new ArrayList<>(seed.members());
            List<QueuedParty> group = new ArrayList<>();
            group.add(seed);

            for (QueuedParty party : searching) {
                if (group.contains(party)) continue;
                if (members.size() == targetSize) break;
                if (members.size() + party.size() > targetSize) continue;
                if (isPartyCompatibleForMatch(party, members, playerMatchmakingData)) {
                    members.addAll(party.members());
                    group.add(party);
                }
            }

            if (members.size() != targetSize) {
                // This party can't be completed into a group of the target size right now; skip its seed.
                searching.remove(seed);
                continue;
            }
            groups.add(group);
            searching.removeAll(group);
        }
        return groups;
    }

    private static void collectGames(
            List<QueuedParty> groupParties,
            GameConfig config,
            Map<MatchmakingQueueMember, PlayerMatchmakingData> playerMatchmakingData,
            List<MatchedGame> gamesToCreate,
            Set<QueuedParty> partiesAddedToGames) {
        List<MatchmakingQueueMember> members = groupParties.stream()
                .flatMap(party -> party.members().stream())
                .collect(Collectors.toCollection(ArrayList::new));
        List<String> restrictions = groupParties.stream()
                .flatMap(party -> party.leaderSettings().getMatchmakingRestrictions().stream())
                .distinct()
                .sorted()
                .toList();
        MatchedGame game = new MatchedGame(
                members,
                new ArrayList<>(groupParties.stream().map(QueuedParty::party).toList()),
                config.playerCount(),
                config.victoryPointGoal(),
                config.expansion(),
                config.pace(),
                restrictions,
                tiglRanksForGame(members, playerMatchmakingData));
        gamesToCreate.add(game);
        MatchDescriber.logFormedMatch(game, playerMatchmakingData);
        partiesAddedToGames.addAll(groupParties);
    }

    private static List<QueuedParty> ungrouped(List<QueuedParty> parties, Set<QueuedParty> partiesAddedToGames) {
        return parties.stream().filter(not(partiesAddedToGames::contains)).toList();
    }

    private static boolean isNearExpiry(QueuedParty party, Instant now) {
        return getExpirationInstant(party).minus(NEAR_QUEUE_EXPIRY_HOURS).isBefore(now);
    }

    private static Instant getEarliestExpiry(List<QueuedParty> parties) {
        return parties.stream()
                .map(MatchmakingGrouper::getExpirationInstant)
                .min(Comparator.naturalOrder())
                .orElse(Instant.MAX);
    }

    private static Instant getExpirationInstant(QueuedParty party) {
        int maxHours = MatchmakingOptions.getHours(party.leaderSettings().getMatchmakingMaxQueueTime());
        return party.party().getQueuedAt().plus(Duration.ofHours(maxHours));
    }

    private static List<String> tiglRanksForGame(
            List<MatchmakingQueueMember> matchMembers,
            Map<MatchmakingQueueMember, PlayerMatchmakingData> playerMatchmakingData) {
        if (!isTiglGroup(matchMembers, playerMatchmakingData)) {
            return List.of();
        }
        Set<String> common = getMatchingTiglRanks(matchMembers, playerMatchmakingData);
        return MatchmakingOptions.TIGL_RANK_OPTIONS.stream()
                .filter(common::contains)
                .toList();
    }

    private static boolean isTiglGroup(
            List<MatchmakingQueueMember> members,
            Map<MatchmakingQueueMember, PlayerMatchmakingData> playerMatchmakingData) {
        return members.stream()
                .anyMatch(member -> playerMatchmakingData.get(member).tigl());
    }

    private static Set<String> getMatchingTiglRanks(
            List<MatchmakingQueueMember> members,
            Map<MatchmakingQueueMember, PlayerMatchmakingData> playerMatchmakingData) {
        Set<String> common = null;
        for (MatchmakingQueueMember member : members) {
            Set<String> ranks = new HashSet<>(playerMatchmakingData.get(member).tiglRanks());
            if (common == null) {
                common = ranks;
            } else {
                common.retainAll(ranks);
            }
        }
        return common == null ? Set.of() : common;
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
            Map<MatchmakingQueueMember, PlayerMatchmakingData> matchmakingData) {
        for (MatchmakingQueueMember newMember : party.members()) {
            for (MatchmakingQueueMember existing : group) {
                PlayerMatchmakingData a = matchmakingData.get(existing);
                PlayerMatchmakingData b = matchmakingData.get(newMember);
                if (MatchmakingCompatibilityService.areIncompatible(a, b)) {
                    return false;
                }
            }
        }

        List<MatchmakingQueueMember> combined = new ArrayList<>(group);
        combined.addAll(party.members());
        return !isTiglGroup(combined, matchmakingData)
                || !getMatchingTiglRanks(combined, matchmakingData).isEmpty();
    }

    private static int totalPlayers(List<QueuedParty> parties) {
        return parties.stream().mapToInt(QueuedParty::size).sum();
    }

    private record NearMatchCandidate(GameConfig config, List<QueuedParty> parties) {}
}
