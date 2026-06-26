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

    public static List<String> getValidRestrictions(List<String> userIds, List<String> restrictions) {
        List<String> members = userIds.stream().distinct().toList();
        List<String> candidateRestrictions = restrictionsAllowedForMembers(members, restrictions);
        if (members.size() < 2) return candidateRestrictions;

        List<String> available = new ArrayList<>();
        for (String restriction : candidateRestrictions) {
            Map<String, PlayerMatchmakingData> dataById =
                    PlayerMatchDataFactory.buildForUsers(members, List.of(restriction));
            if (groupInternallyCompatible(members, dataById)) {
                available.add(restriction);
            }
        }
        return available;
    }

    private static List<String> restrictionsAllowedForMembers(List<String> members, List<String> restrictions) {
        if (members.stream().noneMatch(PartyValidator::isNewPlayer)) {
            return restrictions;
        }
        return restrictions.stream()
                .filter(restriction -> !MatchmakingOptions.SIMILAR_PLAYER_SKILL_OPTION.equals(restriction))
                .toList();
    }

    private static boolean isNewPlayer(String userId) {
        ManagedPlayer managedPlayer = GameManager.getManagedPlayer(userId);
        int completedGames =
                managedPlayer == null ? 0 : UserGameInfoService.countCompletedGamesThatAffectJoinLimit(managedPlayer);
        return completedGames < MatchmakingCompatibilityService.NEW_PLAYER_GAME_THRESHOLD;
    }

    private static boolean groupInternallyCompatible(
            List<String> members, Map<String, PlayerMatchmakingData> dataById) {
        for (int i = 0; i < members.size(); i++) {
            for (int j = i + 1; j < members.size(); j++) {
                if (MatchmakingCompatibilityService.areIncompatible(
                        dataById.get(members.get(i)), dataById.get(members.get(j)))) {
                    return false;
                }
            }
        }
        return true;
    }

    static Optional<String> hasAvoidListConflicts(List<String> userIds) {
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

    static Optional<String> validateGameLimits(List<String> userIds) {
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
