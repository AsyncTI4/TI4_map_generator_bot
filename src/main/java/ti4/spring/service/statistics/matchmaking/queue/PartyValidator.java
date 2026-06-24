package ti4.spring.service.statistics.matchmaking.queue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.discord.interactions.buttons.handlers.matchmaking.MatchmakingOptions;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedPlayer;
import ti4.settings.users.UserSettings;
import ti4.settings.users.UserSettingsManager;
import ti4.spring.service.statistics.UserGameInfoService;

@UtilityClass
public class PartyValidator {

    public static Optional<String> validate(String leaderId, List<String> memberIds) {
        List<String> allIds = distinctMembers(leaderId, memberIds);
        UserSettings leaderSettings = UserSettingsManager.get(leaderId);

        int largestChosenPlayerCount = leaderSettings.getMatchmakingPlayerCounts().stream()
                .mapToInt(Integer::parseInt)
                .max()
                .orElse(0);
        if (allIds.size() > largestChosenPlayerCount) {
            return Optional.of("Your group has " + allIds.size()
                    + " players, which is larger than the biggest player count you selected ("
                    + largestChosenPlayerCount + "). Pick fewer players or allow a larger game size.");
        }

        Optional<String> paceProblem = validatePaceEligibility(allIds, leaderSettings);
        if (paceProblem.isPresent()) return paceProblem;

        Optional<String> gameLimitProblem = validateGameLimits(allIds);
        if (gameLimitProblem.isPresent()) return gameLimitProblem;

        Map<String, PlayerMatchData> dataById = PlayerMatchDataFactory.buildForUsers(allIds, leaderSettings);
        for (int i = 0; i < allIds.size(); i++) {
            for (int j = i + 1; j < allIds.size(); j++) {
                Optional<String> reason = MatchmakingCompatibilityService.incompatibilityReason(
                        dataById.get(allIds.get(i)), dataById.get(allIds.get(j)), false);
                if (reason.isPresent()) return reason;
            }
        }
        return Optional.empty();
    }

    static Optional<String> firstAvoidConflict(List<String> userIds) {
        Map<String, List<String>> avoidById = new HashMap<>();
        for (String id : userIds) {
            avoidById.put(id, UserSettingsManager.get(id).getMatchmakingAvoidList());
        }
        for (int i = 0; i < userIds.size(); i++) {
            for (int j = i + 1; j < userIds.size(); j++) {
                String a = userIds.get(i);
                String b = userIds.get(j);
                if (avoidById.get(a).contains(b) || avoidById.get(b).contains(a)) {
                    return Optional.of(
                            "<@" + a + "> and <@" + b + "> have each other on an avoid list and can't be grouped.");
                }
            }
        }
        return Optional.empty();
    }

    static List<String> distinctMembers(String leaderId, List<String> memberIds) {
        List<String> allIds = new ArrayList<>();
        allIds.add(leaderId);
        for (String id : memberIds) {
            if (!allIds.contains(id)) allIds.add(id);
        }
        return allIds;
    }

    private static Optional<String> validatePaceEligibility(List<String> userIds, UserSettings leaderSettings) {
        UserGameInfoService userGameInfoService = UserGameInfoService.get();
        for (String pace : leaderSettings.getMatchmakingPaces()) {
            Integer requirement = MatchmakingOptions.PACE_RESTRICTION_TO_GAME_DAYS_TO_COMPLETE_REQUIREMENT.get(pace);
            if (requirement == null) continue;
            for (String id : userIds) {
                if (!userGameInfoService.hasCompletedGameInDays(id, requirement)) {
                    return Optional.of("<@" + id + "> isn't eligible for the \"" + pace
                            + "\" pace (it requires a game completed within " + requirement + " days)."
                            + " Deselect the \"" + pace + "\" pace to queue with that player.");
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<String> validateGameLimits(List<String> userIds) {
        for (String id : userIds) {
            ManagedPlayer managedPlayer = GameManager.getManagedPlayer(id);
            if (managedPlayer == null) continue;
            if (UserGameInfoService.isOverStandardGameLimit(managedPlayer)) {
                return Optional.of("<@" + id + "> is at their game limit and can't join more games right now.");
            }
            UserSettings ownSettings = UserSettingsManager.get(id);
            int ongoing = UserGameInfoService.countOngoingGamesThatAffectJoinLimit(managedPlayer);
            if (ownSettings.getGameLimit() > 0 && ongoing >= ownSettings.getGameLimit()) {
                return Optional.of("<@" + id + "> is under a personal game limit and can't join more games right now.");
            }
        }
        return Optional.empty();
    }
}
