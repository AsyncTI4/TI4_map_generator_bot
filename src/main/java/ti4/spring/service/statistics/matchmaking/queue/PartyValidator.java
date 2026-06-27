package ti4.spring.service.statistics.matchmaking.queue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import ti4.discord.JdaService;
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
        List<String> allowed = restrictions;
        if (members.stream().anyMatch(PartyValidator::isNewPlayer)) {
            allowed = allowed.stream()
                    .filter(restriction -> !MatchmakingOptions.SIMILAR_PLAYER_SKILL_OPTION.equals(restriction))
                    .toList();
        }
        if (!members.stream().allMatch(PartyValidator::hasSetTiglRank)) {
            allowed = allowed.stream()
                    .filter(restriction -> !MatchmakingOptions.TIGL_OPTION.equals(restriction))
                    .toList();
        }

        Set<String> groupRoles = groupRoleNames(members);
        boolean hasFloater = groupRoles.contains(MatchmakingOptions.FLOATERS_ROLE_NAME);
        boolean hasWarrior = groupRoles.contains(MatchmakingOptions.WARRIORS_ROLE_NAME);
        if (hasFloater || !hasWarrior) {
            allowed = allowed.stream()
                    .filter(restriction -> !MatchmakingOptions.AVOID_FLOATERS_OPTION.equals(restriction))
                    .toList();
        }
        if (hasWarrior || !hasFloater) {
            allowed = allowed.stream()
                    .filter(restriction -> !MatchmakingOptions.AVOID_WARRIORS_OPTION.equals(restriction))
                    .toList();
        }
        return allowed;
    }

    private static Set<String> groupRoleNames(List<String> userIds) {
        Guild guild = JdaService.guildPrimary;
        if (guild == null) return Set.of();
        Set<String> roleNames = new HashSet<>();
        for (String userId : userIds) {
            Member member = guild.getMemberById(userId);
            if (member != null) {
                roleNames.addAll(MatchmakingOptions.getHeldRoleNames(guild, member));
            }
        }
        return roleNames;
    }

    private static boolean hasSetTiglRank(String userId) {
        return UserSettingsManager.get(userId).hasSetTiglRank();
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
